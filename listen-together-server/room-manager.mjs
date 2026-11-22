import crypto from 'node:crypto';

// Unambiguous uppercase alphanumeric characters (excludes 0, O, 1, I)
const ROOM_CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const ROOM_CODE_LENGTH = 6;

export const CONTROL_MODES = {
  HOST_ONLY: 'HOST_ONLY',
  EVERYONE: 'EVERYONE',
};

export const VALID_QUALITY_LABELS = new Set([
  'Data Saver',
  'Low',
  'HD',
  '96 kbps',
  '160 kbps',
  '320 kbps',
]);

function generateRoomCode() {
  const bytes = crypto.randomBytes(ROOM_CODE_LENGTH);
  let code = '';
  for (let i = 0; i < ROOM_CODE_LENGTH; i++) {
    code += ROOM_CODE_ALPHABET[bytes[i] % ROOM_CODE_ALPHABET.length];
  }
  return code;
}

function generateSecureToken(bytes = 24) {
  return crypto.randomBytes(bytes).toString('hex');
}

function timingSafeEqualString(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const bufA = Buffer.from(a, 'utf8');
  const bufB = Buffer.from(b, 'utf8');
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

function sanitizeDisplayName(raw) {
  if (typeof raw !== 'string') return 'Listener';
  const cleaned = raw.replace(/[\r\n\t\x00-\x1F\x7F]/g, ' ').trim().slice(0, 32);
  return cleaned.length > 0 ? cleaned : 'Listener';
}

function sanitizeQualityLabel(raw) {
  if (typeof raw !== 'string') return 'HD';
  const cleaned = raw.trim().slice(0, 24);
  return VALID_QUALITY_LABELS.has(cleaned) ? cleaned : 'HD';
}

function sanitizeRoomCode(raw) {
  if (typeof raw !== 'string') return '';
  return raw.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, ROOM_CODE_LENGTH);
}

function sanitizeTrack(raw, addedByParticipantId = null, addedByName = null) {
  if (!raw || typeof raw !== 'object') return null;
  const trackId = typeof raw.trackId === 'string' ? raw.trackId.trim().slice(0, 128) : '';
  const title = typeof raw.title === 'string' ? raw.title.trim().slice(0, 160) : '';
  if (!trackId || !title) return null;

  const provider = typeof raw.provider === 'string' && raw.provider.trim().length > 0
    ? raw.provider.trim().slice(0, 32)
    : 'JIO_SAAVN';
  const artist = typeof raw.artist === 'string' ? raw.artist.trim().slice(0, 160) : 'Unknown Artist';
  const album = typeof raw.album === 'string' ? raw.album.trim().slice(0, 160) : '';
  const artworkUrl = typeof raw.artworkUrl === 'string' ? raw.artworkUrl.trim().slice(0, 512) : '';
  const rawDuration = Number(raw.durationMs);
  const durationMs = Number.isFinite(rawDuration) && rawDuration >= 0
    ? Math.min(Math.floor(rawDuration), 12 * 60 * 60 * 1000)
    : 0;

  return {
    trackId,
    provider,
    title,
    artist,
    album,
    artworkUrl,
    durationMs,
    addedByParticipantId: raw.addedByParticipantId || addedByParticipantId || '',
    addedByName: typeof raw.addedByName === 'string' && raw.addedByName.trim()
      ? raw.addedByName.trim().slice(0, 32)
      : (addedByName || ''),
  };
}

/**
 * Server-authoritative manager for Listen Together rooms, participants,
 * shared playback state, host handoff, rate limits, and sync pulses.
 */
export class RoomManager {
  constructor(options = {}) {
    this.now = options.now || (() => Date.now());
    this.roomTtlMs = options.roomTtlMs ?? 6 * 60 * 60 * 1000; // 6 hours
    this.maxRoomLifetimeMs = options.maxRoomLifetimeMs ?? 24 * 60 * 60 * 1000; // 24 hours hard cap
    this.maxParticipantsPerRoom = options.maxParticipantsPerRoom ?? 16;
    this.maxQueueSize = options.maxQueueSize ?? 100;
    this.maxRooms = options.maxRooms ?? 10000;
    this.hostTransferGraceMs = options.hostTransferGraceMs ?? 20_000;
    this.participantDisconnectGraceMs = options.participantDisconnectGraceMs ?? 60_000;
    this.emptyRoomGraceMs = options.emptyRoomGraceMs ?? 5 * 60 * 1000;
    this.playLeadTimeMs = options.playLeadTimeMs ?? 350;
    this.trackChangeLeadTimeMs = options.trackChangeLeadTimeMs ?? 650;
    this.skipDebounceMs = options.skipDebounceMs ?? 450;
    this.syncPulseIntervalMs = options.syncPulseIntervalMs ?? 4000;
    this.cleanupIntervalMs = options.cleanupIntervalMs ?? 15_000;

    /** @type {Map<string, object>} roomCode -> room */
    this.rooms = new Map();
    /** @type {Map<object, { roomCode: string, participantId: string }>} ws -> binding */
    this.socketBindings = new Map();

    this._pulseTimer = setInterval(() => this._broadcastSyncPulses(), this.syncPulseIntervalMs);
    this._pulseTimer.unref?.();
    this._cleanupTimer = setInterval(() => this.cleanupExpiredRooms(), this.cleanupIntervalMs);
    this._cleanupTimer.unref?.();
  }

  destroy() {
    clearInterval(this._pulseTimer);
    clearInterval(this._cleanupTimer);
    for (const room of this.rooms.values()) {
      this._clearRoomTimers(room);
    }
    this.rooms.clear();
    this.socketBindings.clear();
  }

  getRoomPublicStatus(rawCode) {
    const code = sanitizeRoomCode(rawCode);
    const room = this.rooms.get(code);
    const now = this.now();
    if (!room || room.expiresAt <= now) {
      return { exists: false, expired: Boolean(room && room.expiresAt <= now) };
    }
    return {
      exists: true,
      roomCode: room.roomCode,
      participantCount: room.participants.size,
      controlMode: room.controlMode,
      expiresAt: room.expiresAt,
      isPlaying: room.playback.isPlaying,
      currentTrackTitle: room.queue[room.currentIndex]?.title || null,
    };
  }

  getStats() {
    let totalParticipants = 0;
    for (const room of this.rooms.values()) {
      totalParticipants += room.participants.size;
    }
    return {
      activeRooms: this.rooms.size,
      activeParticipants: totalParticipants,
      activeSockets: this.socketBindings.size,
    };
  }

  handleMessage(ws, rawText) {
    let msg;
    try {
      msg = JSON.parse(rawText);
    } catch {
      this._sendError(ws, 'INVALID_JSON', 'Malformed JSON payload');
      return;
    }
    if (!msg || typeof msg !== 'object' || typeof msg.type !== 'string') {
      this._sendError(ws, 'INVALID_MESSAGE', 'Missing message type');
      return;
    }

    switch (msg.type) {
      case 'time_sync_ping':
        this._handleTimeSyncPing(ws, msg);
        break;
      case 'create_room':
        this._handleCreateRoom(ws, msg);
        break;
      case 'join_room':
        this._handleJoinRoom(ws, msg);
        break;
      case 'reconnect_room':
        this._handleReconnectRoom(ws, msg);
        break;
      case 'leave_room':
        this._handleLeaveRoom(ws);
        break;
      case 'end_room':
        this._handleEndRoom(ws);
        break;
      case 'playback_command':
        this._handlePlaybackCommand(ws, msg);
        break;
      case 'set_control_mode':
        this._handleSetControlMode(ws, msg);
        break;
      case 'update_quality':
        this._handleUpdateQuality(ws, msg);
        break;
      case 'sync_request':
        this._handleSyncRequest(ws);
        break;
      default:
        this._sendError(ws, 'UNKNOWN_MESSAGE_TYPE', `Unsupported message type: ${msg.type}`);
    }
  }

  handleDisconnect(ws) {
    const binding = this.socketBindings.get(ws);
    if (!binding) return;
    this.socketBindings.delete(ws);

    const room = this.rooms.get(binding.roomCode);
    if (!room) return;

    const participant = room.participants.get(binding.participantId);
    if (!participant || participant.ws !== ws) return;

    const now = this.now();
    participant.ws = null;
    participant.connectionState = 'RECONNECTING';
    participant.lastSeenAt = now;

    this._broadcastRoomState(room, 'participant_updated');

    // Start host transfer grace timer if host disconnected
    if (room.hostId === participant.participantId) {
      if (room.hostTransferTimer) clearTimeout(room.hostTransferTimer);
      room.hostTransferTimer = setTimeout(() => {
        room.hostTransferTimer = null;
        this._maybeTransferHost(room, participant.participantId, 'HOST_DISCONNECTED');
      }, this.hostTransferGraceMs);
      room.hostTransferTimer.unref?.();
    }

    // Remove participant if they don't reconnect within disconnect grace window
    if (participant.disconnectTimer) clearTimeout(participant.disconnectTimer);
    participant.disconnectTimer = setTimeout(() => {
      participant.disconnectTimer = null;
      this._evictDisconnectedParticipant(room, participant.participantId);
    }, this.participantDisconnectGraceMs);
    participant.disconnectTimer.unref?.();

    this._checkEmptyRoomTimer(room);
  }

  _handleTimeSyncPing(ws, msg) {
    const serverReceiveTimeMs = this.now();
    const seq = Number.isFinite(Number(msg.seq)) ? Number(msg.seq) : 0;
    const clientSendTimeMs = Number.isFinite(Number(msg.clientSendTimeMs))
      ? Number(msg.clientSendTimeMs)
      : 0;
    ws.sendJson({
      type: 'time_sync_pong',
      seq,
      clientSendTimeMs,
      serverReceiveTimeMs,
      serverSendTimeMs: this.now(),
    });
  }

  _handleCreateRoom(ws, msg) {
    if (this.rooms.size >= this.maxRooms) {
      this.cleanupExpiredRooms();
      if (this.rooms.size >= this.maxRooms) {
        this._sendError(ws, 'SERVER_CAPACITY', 'Server room capacity reached');
        return;
      }
    }

    // Detach socket from any prior room cleanly
    this._detachSocket(ws);

    let roomCode = generateRoomCode();
    for (let attempt = 0; attempt < 20 && this.rooms.has(roomCode); attempt++) {
      roomCode = generateRoomCode();
    }
    if (this.rooms.has(roomCode)) {
      this._sendError(ws, 'CODE_GENERATION_FAILED', 'Unable to allocate unique room code');
      return;
    }

    const now = this.now();
    const displayName = sanitizeDisplayName(msg.displayName);
    const qualityLabel = sanitizeQualityLabel(msg.qualityLabel);
    const controlMode = msg.controlMode === CONTROL_MODES.EVERYONE
      ? CONTROL_MODES.EVERYONE
      : CONTROL_MODES.HOST_ONLY;

    const participantId = crypto.randomUUID();
    const sessionToken = generateSecureToken(24);

    const queue = [];
    if (Array.isArray(msg.initialQueue)) {
      for (const rawTrack of msg.initialQueue.slice(0, this.maxQueueSize)) {
        const track = sanitizeTrack(rawTrack, participantId, displayName);
        if (track) queue.push(track);
      }
    }
    const singleInitialTrack = sanitizeTrack(msg.initialTrack, participantId, displayName);
    if (queue.length === 0 && singleInitialTrack) {
      queue.push(singleInitialTrack);
    }

    let currentIndex = -1;
    if (queue.length > 0) {
      const requestedIndex = Number(msg.initialIndex);
      if (Number.isInteger(requestedIndex) && requestedIndex >= 0 && requestedIndex < queue.length) {
        currentIndex = requestedIndex;
      } else if (singleInitialTrack) {
        const idx = queue.findIndex((t) => t.trackId === singleInitialTrack.trackId);
        currentIndex = idx >= 0 ? idx : 0;
      } else {
        currentIndex = 0;
      }
    }

    const currentTrack = currentIndex >= 0 ? queue[currentIndex] : null;
    const rawPos = Number(msg.initialPositionMs);
    const clampedPos = this._clampPosition(
      Number.isFinite(rawPos) && rawPos >= 0 ? Math.floor(rawPos) : 0,
      currentTrack?.durationMs || 0
    );
    const initialIsPlaying = Boolean(msg.initialIsPlaying) && currentTrack !== null;
    const scheduledStartServerTimeMs = initialIsPlaying ? now + this.playLeadTimeMs : now;

    const participant = {
      participantId,
      sessionToken,
      displayName,
      qualityLabel,
      isHost: true,
      connectionState: 'CONNECTED',
      joinedAt: now,
      lastSeenAt: now,
      ws,
      disconnectTimer: null,
    };

    const room = {
      roomId: crypto.randomUUID(),
      roomCode,
      createdAt: now,
      hardExpiresAt: now + this.maxRoomLifetimeMs,
      expiresAt: now + this.roomTtlMs,
      hostId: participantId,
      controlMode,
      participants: new Map([[participantId, participant]]),
      queue,
      currentIndex,
      playback: {
        isPlaying: initialIsPlaying,
        basePositionMs: clampedPos,
        anchorServerTimeMs: now,
        scheduledStartServerTimeMs,
        playbackRate: 1.0,
        stateRevision: 1,
        queueRevision: 1,
        lastCommandId: null,
        lastUpdatedBy: participantId,
        lastAction: 'ROOM_CREATED',
      },
      seenCommandIds: [],
      seenCommandIdSet: new Set(),
      lastSkipAction: null,
      lastSkipAtMs: 0,
      lastSkipFromTrackId: null,
      hostTransferTimer: null,
      emptyRoomTimer: null,
    };

    this.rooms.set(roomCode, room);
    this.socketBindings.set(ws, { roomCode, participantId });

    ws.sendJson({
      type: 'room_joined',
      participantId,
      sessionToken,
      serverTimeMs: now,
      room: this._serializeRoom(room, now),
    });
  }

  _handleJoinRoom(ws, msg) {
    const code = sanitizeRoomCode(msg.roomCode);
    if (code.length !== ROOM_CODE_LENGTH) {
      this._sendError(ws, 'INVALID_ROOM_CODE', 'Room code must be 6 letters or numbers');
      return;
    }

    const room = this.rooms.get(code);
    const now = this.now();
    if (!room) {
      this._sendError(ws, 'ROOM_NOT_FOUND', 'No active room matches that code');
      return;
    }
    if (room.expiresAt <= now) {
      this._closeAndDeleteRoom(room, 'ROOM_EXPIRED', 'This listening room has expired');
      this._sendError(ws, 'ROOM_EXPIRED', 'This listening room has expired');
      return;
    }
    if (room.participants.size >= this.maxParticipantsPerRoom) {
      this._sendError(ws, 'ROOM_FULL', 'This room has reached the participant limit');
      return;
    }

    this._detachSocket(ws);
    this._touchRoom(room, now);
    this._cancelEmptyRoomTimer(room);

    const participantId = crypto.randomUUID();
    const sessionToken = generateSecureToken(24);
    const displayName = sanitizeDisplayName(msg.displayName);
    const qualityLabel = sanitizeQualityLabel(msg.qualityLabel);

    const participant = {
      participantId,
      sessionToken,
      displayName,
      qualityLabel,
      isHost: false,
      connectionState: 'CONNECTED',
      joinedAt: now,
      lastSeenAt: now,
      ws,
      disconnectTimer: null,
    };

    room.participants.set(participantId, participant);
    this.socketBindings.set(ws, { roomCode: code, participantId });

    ws.sendJson({
      type: 'room_joined',
      participantId,
      sessionToken,
      serverTimeMs: now,
      room: this._serializeRoom(room, now),
    });

    this._broadcastRoomState(room, 'participant_updated', participantId);
  }

  _handleReconnectRoom(ws, msg) {
    const code = sanitizeRoomCode(msg.roomCode);
    const participantId = typeof msg.participantId === 'string' ? msg.participantId.trim() : '';
    const sessionToken = typeof msg.sessionToken === 'string' ? msg.sessionToken.trim() : '';

    const room = this.rooms.get(code);
    const now = this.now();
    if (!room) {
      this._sendError(ws, 'ROOM_NOT_FOUND', 'Room no longer exists');
      return;
    }
    if (room.expiresAt <= now) {
      this._closeAndDeleteRoom(room, 'ROOM_EXPIRED', 'Room expired');
      this._sendError(ws, 'ROOM_EXPIRED', 'Room expired');
      return;
    }

    const participant = room.participants.get(participantId);
    if (!participant || !timingSafeEqualString(participant.sessionToken, sessionToken)) {
      this._sendError(ws, 'UNAUTHORIZED', 'Invalid room session token');
      return;
    }

    // Replace previous socket binding if stale socket was still attached
    if (participant.ws && participant.ws !== ws) {
      this.socketBindings.delete(participant.ws);
      participant.ws.close(1000, 'Reconnected on new socket');
    }
    this._detachSocket(ws);

    if (participant.disconnectTimer) {
      clearTimeout(participant.disconnectTimer);
      participant.disconnectTimer = null;
    }
    if (room.hostId === participantId && room.hostTransferTimer) {
      clearTimeout(room.hostTransferTimer);
      room.hostTransferTimer = null;
    }
    this._cancelEmptyRoomTimer(room);

    participant.ws = ws;
    participant.connectionState = 'CONNECTED';
    participant.lastSeenAt = now;
    if (msg.displayName) {
      participant.displayName = sanitizeDisplayName(msg.displayName);
    }
    if (msg.qualityLabel) {
      participant.qualityLabel = sanitizeQualityLabel(msg.qualityLabel);
    }

    this.socketBindings.set(ws, { roomCode: code, participantId });
    this._touchRoom(room, now);

    ws.sendJson({
      type: 'room_joined',
      participantId,
      sessionToken: participant.sessionToken,
      reconnected: true,
      serverTimeMs: now,
      room: this._serializeRoom(room, now),
    });

    this._broadcastRoomState(room, 'participant_updated', participantId);
  }

  _handleLeaveRoom(ws) {
    const binding = this.socketBindings.get(ws);
    if (!binding) return;
    this.socketBindings.delete(ws);

    const room = this.rooms.get(binding.roomCode);
    if (!room) return;

    this._removeParticipant(room, binding.participantId, 'HOST_LEFT');
    ws.sendJson({ type: 'room_left', roomCode: binding.roomCode, serverTimeMs: this.now() });
  }

  _handleEndRoom(ws) {
    const context = this._requireRoomParticipant(ws);
    if (!context) return;
    const { room, participant } = context;

    if (!participant.isHost) {
      this._sendError(ws, 'FORBIDDEN', 'Only the room host can end this room');
      return;
    }

    const now = this.now();
    this._clearRoomTimers(room);
    this.rooms.delete(room.roomCode);

    for (const member of room.participants.values()) {
      if (member.disconnectTimer) {
        clearTimeout(member.disconnectTimer);
        member.disconnectTimer = null;
      }
      if (member.ws) {
        this.socketBindings.delete(member.ws);
        member.ws.sendJson({
          type: 'room_ended',
          roomCode: room.roomCode,
          reason: 'HOST_ENDED_ROOM',
          endedBy: participant.displayName,
          serverTimeMs: now,
        });
      }
    }
    room.participants.clear();
  }

  _handleSetControlMode(ws, msg) {
    const context = this._requireRoomParticipant(ws);
    if (!context) return;
    const { room, participant } = context;

    if (!participant.isHost) {
      this._sendError(ws, 'FORBIDDEN', 'Only the room host can change control permissions');
      return;
    }

    const nextMode = msg.controlMode;
    if (nextMode !== CONTROL_MODES.HOST_ONLY && nextMode !== CONTROL_MODES.EVERYONE) {
      this._sendError(ws, 'INVALID_CONTROL_MODE', 'Control mode must be HOST_ONLY or EVERYONE');
      return;
    }

    const now = this.now();
    room.controlMode = nextMode;
    room.playback.stateRevision += 1;
    room.playback.lastUpdatedBy = participant.participantId;
    room.playback.lastAction = 'CONTROL_MODE_CHANGED';
    this._touchRoom(room, now);

    this._broadcastRoomState(room, 'room_state');
  }

  _handleUpdateQuality(ws, msg) {
    const context = this._requireRoomParticipant(ws);
    if (!context) return;
    const { room, participant } = context;

    participant.qualityLabel = sanitizeQualityLabel(msg.qualityLabel);
    participant.lastSeenAt = this.now();
    this._broadcastRoomState(room, 'participant_updated');
  }

  _handleSyncRequest(ws) {
    const context = this._requireRoomParticipant(ws);
    if (!context) return;
    const { room } = context;
    const now = this.now();
    ws.sendJson({
      type: 'playback_state',
      serverTimeMs: now,
      room: this._serializeRoom(room, now),
    });
  }

  _handlePlaybackCommand(ws, msg) {
    const context = this._requireRoomParticipant(ws);
    if (!context) return;
    const { room, participant } = context;
    const now = this.now();
    participant.lastSeenAt = now;

    const action = typeof msg.action === 'string' ? msg.action.toUpperCase() : '';
    const commandId = typeof msg.commandId === 'string' ? msg.commandId.trim().slice(0, 64) : '';

    // Idempotency check
    if (commandId && room.seenCommandIdSet.has(commandId)) {
      ws.sendJson({
        type: 'playback_state',
        duplicate: true,
        serverTimeMs: now,
        room: this._serializeRoom(room, now),
      });
      return;
    }

    // Authorization check
    const isTrackEnded = action === 'TRACK_ENDED';
    if (isTrackEnded) {
      if (!participant.isHost && room.controlMode !== CONTROL_MODES.EVERYONE) {
        return; // Guests in HOST_ONLY mode let host advance tracks
      }
    } else if (room.controlMode === CONTROL_MODES.HOST_ONLY && !participant.isHost) {
      this._sendError(ws, 'FORBIDDEN', 'Only the room host can control playback in Host Only mode');
      return;
    }

    const currentTrack = room.currentIndex >= 0 ? room.queue[room.currentIndex] : null;
    const currentEffectivePos = this.computeEffectivePositionMs(room, now);

    // Debounce simultaneous NEXT/PREV/TRACK_ENDED taps from multiple devices in EVERYONE mode
    const clientFromTrackId = typeof msg.trackId === 'string' ? msg.trackId : null;
    const isDuplicateSkip = (action === 'NEXT' || action === 'PREV' || action === 'TRACK_ENDED') &&
      room.lastSkipAction === action &&
      now - room.lastSkipAtMs < this.skipDebounceMs &&
      (clientFromTrackId === null || clientFromTrackId === room.lastSkipFromTrackId);
    if (isDuplicateSkip) {
      ws.sendJson({
        type: 'playback_state',
        deduplicated: true,
        serverTimeMs: now,
        room: this._serializeRoom(room, now),
      });
      return;
    }

    let stateChanged = false;

    switch (action) {
      case 'PLAY': {
        if (!currentTrack) {
          this._sendError(ws, 'NO_TRACK', 'Select a track before starting playback');
          return;
        }
        const pos = Number.isFinite(Number(msg.positionMs))
          ? this._clampPosition(Math.floor(Number(msg.positionMs)), currentTrack.durationMs)
          : currentEffectivePos;
        room.playback.isPlaying = true;
        room.playback.basePositionMs = pos;
        room.playback.anchorServerTimeMs = now;
        room.playback.scheduledStartServerTimeMs = now + this.playLeadTimeMs;
        room.playback.lastAction = 'PLAY';
        stateChanged = true;
        break;
      }

      case 'PAUSE': {
        const pos = Number.isFinite(Number(msg.positionMs))
          ? this._clampPosition(Math.floor(Number(msg.positionMs)), currentTrack?.durationMs || 0)
          : currentEffectivePos;
        room.playback.isPlaying = false;
        room.playback.basePositionMs = pos;
        room.playback.anchorServerTimeMs = now;
        room.playback.scheduledStartServerTimeMs = now;
        room.playback.lastAction = 'PAUSE';
        stateChanged = true;
        break;
      }

      case 'SEEK': {
        if (!currentTrack) {
          this._sendError(ws, 'NO_TRACK', 'No active track to seek');
          return;
        }
        const targetPos = Number(msg.positionMs);
        if (!Number.isFinite(targetPos) || targetPos < 0) {
          this._sendError(ws, 'INVALID_POSITION', 'Seek position must be a non-negative number');
          return;
        }
        room.playback.basePositionMs = this._clampPosition(Math.floor(targetPos), currentTrack.durationMs);
        room.playback.anchorServerTimeMs = now;
        room.playback.scheduledStartServerTimeMs = room.playback.isPlaying
          ? now + this.playLeadTimeMs
          : now;
        room.playback.lastAction = 'SEEK';
        stateChanged = true;
        break;
      }

      case 'SET_TRACK': {
        const track = sanitizeTrack(msg.track, participant.participantId, participant.displayName);
        if (!track) {
          this._sendError(ws, 'INVALID_TRACK', 'Track must include canonical trackId and title');
          return;
        }
        const existingIdx = room.queue.findIndex((t) => t.trackId === track.trackId);
        if (existingIdx >= 0) {
          room.queue[existingIdx] = track;
          room.currentIndex = existingIdx;
        } else {
          if (room.queue.length >= this.maxQueueSize) {
            room.queue.shift();
          }
          const insertIndex = room.currentIndex >= 0
            ? Math.min(room.currentIndex + 1, room.queue.length)
            : room.queue.length;
          room.queue.splice(insertIndex, 0, track);
          room.currentIndex = insertIndex;
          room.playback.queueRevision += 1;
        }
        const autoPlay = msg.autoPlay !== false;
        const startPos = Number.isFinite(Number(msg.positionMs))
          ? this._clampPosition(Math.floor(Number(msg.positionMs)), track.durationMs)
          : 0;
        room.playback.isPlaying = autoPlay;
        room.playback.basePositionMs = startPos;
        room.playback.anchorServerTimeMs = now;
        room.playback.scheduledStartServerTimeMs = autoPlay
          ? now + this.trackChangeLeadTimeMs
          : now;
        room.playback.lastAction = 'SET_TRACK';
        stateChanged = true;
        break;
      }

      case 'SET_QUEUE': {
        if (!Array.isArray(msg.queue) || msg.queue.length === 0) {
          this._sendError(ws, 'INVALID_QUEUE', 'Queue must contain at least one valid track');
          return;
        }
        const sanitizedQueue = [];
        for (const raw of msg.queue.slice(0, this.maxQueueSize)) {
          const track = sanitizeTrack(raw, participant.participantId, participant.displayName);
          if (track) sanitizedQueue.push(track);
        }
        if (sanitizedQueue.length === 0) {
          this._sendError(ws, 'INVALID_QUEUE', 'Queue had no valid canonical tracks');
          return;
        }
        const reqIdx = Number.isInteger(Number(msg.queueIndex)) ? Number(msg.queueIndex) : 0;
        room.queue = sanitizedQueue;
        room.currentIndex = Math.max(0, Math.min(reqIdx, sanitizedQueue.length - 1));
        room.playback.queueRevision += 1;

        const activeTrack = room.queue[room.currentIndex];
        const autoPlay = msg.autoPlay !== false;
        const startPos = Number.isFinite(Number(msg.positionMs))
          ? this._clampPosition(Math.floor(Number(msg.positionMs)), activeTrack.durationMs)
          : 0;
        room.playback.isPlaying = autoPlay;
        room.playback.basePositionMs = startPos;
        room.playback.anchorServerTimeMs = now;
        room.playback.scheduledStartServerTimeMs = autoPlay
          ? now + this.trackChangeLeadTimeMs
          : now;
        room.playback.lastAction = 'SET_QUEUE';
        stateChanged = true;
        break;
      }

      case 'ADD_TO_QUEUE': {
        const track = sanitizeTrack(msg.track, participant.participantId, participant.displayName);
        if (!track) {
          this._sendError(ws, 'INVALID_TRACK', 'Track must include canonical trackId and title');
          return;
        }
        if (room.queue.length >= this.maxQueueSize) {
          this._sendError(ws, 'QUEUE_FULL', 'Shared queue is full');
          return;
        }
        room.queue.push(track);
        room.playback.queueRevision += 1;
        if (room.currentIndex < 0) {
          room.currentIndex = 0;
          const autoPlay = Boolean(msg.autoPlay);
          room.playback.isPlaying = autoPlay;
          room.playback.basePositionMs = 0;
          room.playback.anchorServerTimeMs = now;
          room.playback.scheduledStartServerTimeMs = autoPlay
            ? now + this.trackChangeLeadTimeMs
            : now;
        }
        room.playback.lastAction = 'ADD_TO_QUEUE';
        stateChanged = true;
        break;
      }

      case 'REMOVE_FROM_QUEUE': {
        const removeIdx = Number(msg.queueIndex);
        if (!Number.isInteger(removeIdx) || removeIdx < 0 || removeIdx >= room.queue.length) {
          this._sendError(ws, 'INVALID_INDEX', 'Queue index out of bounds');
          return;
        }
        const removingCurrent = removeIdx === room.currentIndex;
        room.queue.splice(removeIdx, 1);
        room.playback.queueRevision += 1;

        if (room.queue.length === 0) {
          room.currentIndex = -1;
          room.playback.isPlaying = false;
          room.playback.basePositionMs = 0;
          room.playback.anchorServerTimeMs = now;
          room.playback.scheduledStartServerTimeMs = now;
        } else if (removeIdx < room.currentIndex) {
          room.currentIndex -= 1;
        } else if (removingCurrent) {
          room.currentIndex = Math.min(removeIdx, room.queue.length - 1);
          room.playback.basePositionMs = 0;
          room.playback.anchorServerTimeMs = now;
          room.playback.scheduledStartServerTimeMs = room.playback.isPlaying
            ? now + this.trackChangeLeadTimeMs
            : now;
        }
        room.playback.lastAction = 'REMOVE_FROM_QUEUE';
        stateChanged = true;
        break;
      }

      case 'PLAY_QUEUE_INDEX': {
        const targetIdx = Number(msg.queueIndex);
        if (!Number.isInteger(targetIdx) || targetIdx < 0 || targetIdx >= room.queue.length) {
          this._sendError(ws, 'INVALID_INDEX', 'Queue index out of bounds');
          return;
        }
        room.currentIndex = targetIdx;
        room.playback.isPlaying = true;
        room.playback.basePositionMs = 0;
        room.playback.anchorServerTimeMs = now;
        room.playback.scheduledStartServerTimeMs = now + this.trackChangeLeadTimeMs;
        room.playback.lastAction = 'PLAY_QUEUE_INDEX';
        stateChanged = true;
        break;
      }

      case 'NEXT':
      case 'TRACK_ENDED': {
        if (room.queue.length === 0) {
          this._sendError(ws, 'EMPTY_QUEUE', 'Queue is empty');
          return;
        }
        if (action === 'TRACK_ENDED' && typeof msg.trackId === 'string' && currentTrack && msg.trackId !== currentTrack.trackId) {
          return; // Ignore stale track-ended notification from an earlier track
        }
        const prevTrackId = currentTrack?.trackId || null;
        if (room.currentIndex + 1 < room.queue.length) {
          room.currentIndex += 1;
          room.playback.isPlaying = true;
          room.playback.basePositionMs = 0;
          room.playback.anchorServerTimeMs = now;
          room.playback.scheduledStartServerTimeMs = now + this.trackChangeLeadTimeMs;
        } else {
          // End of queue: pause cleanly at end (or wrap if explicit NEXT with multiple items)
          if (action === 'NEXT' && room.queue.length > 1) {
            room.currentIndex = 0;
            room.playback.isPlaying = true;
            room.playback.basePositionMs = 0;
            room.playback.anchorServerTimeMs = now;
            room.playback.scheduledStartServerTimeMs = now + this.trackChangeLeadTimeMs;
          } else {
            room.playback.isPlaying = false;
            room.playback.basePositionMs = currentTrack?.durationMs || 0;
            room.playback.anchorServerTimeMs = now;
            room.playback.scheduledStartServerTimeMs = now;
          }
        }
        room.lastSkipAction = action;
        room.lastSkipAtMs = now;
        room.lastSkipFromTrackId = prevTrackId;
        room.playback.lastAction = action;
        stateChanged = true;
        break;
      }

      case 'PREV': {
        if (room.queue.length === 0) {
          this._sendError(ws, 'EMPTY_QUEUE', 'Queue is empty');
          return;
        }
        const prevTrackId = currentTrack?.trackId || null;
        if (currentEffectivePos > 3500 || room.currentIndex <= 0) {
          // Restart current track if past 3.5s or already at track 0
          room.playback.basePositionMs = 0;
          room.playback.anchorServerTimeMs = now;
          room.playback.scheduledStartServerTimeMs = room.playback.isPlaying
            ? now + this.playLeadTimeMs
            : now;
        } else {
          room.currentIndex -= 1;
          room.playback.isPlaying = true;
          room.playback.basePositionMs = 0;
          room.playback.anchorServerTimeMs = now;
          room.playback.scheduledStartServerTimeMs = now + this.trackChangeLeadTimeMs;
        }
        room.lastSkipAction = 'PREV';
        room.lastSkipAtMs = now;
        room.lastSkipFromTrackId = prevTrackId;
        room.playback.lastAction = 'PREV';
        stateChanged = true;
        break;
      }

      default:
        this._sendError(ws, 'INVALID_ACTION', `Unsupported playback action: ${action}`);
        return;
    }

    if (stateChanged) {
      if (commandId) {
        this._recordCommandId(room, commandId);
        room.playback.lastCommandId = commandId;
      }
      room.playback.stateRevision += 1;
      room.playback.lastUpdatedBy = participant.participantId;
      this._touchRoom(room, now);
      this._broadcastRoomState(room, 'playback_state');
    }
  }

  computeEffectivePositionMs(room, now = this.now()) {
    const track = room.currentIndex >= 0 ? room.queue[room.currentIndex] : null;
    if (!track) return 0;
    if (!room.playback.isPlaying) {
      return this._clampPosition(room.playback.basePositionMs, track.durationMs);
    }
    const startAnchor = room.playback.scheduledStartServerTimeMs || room.playback.anchorServerTimeMs;
    if (now < startAnchor) {
      return this._clampPosition(room.playback.basePositionMs, track.durationMs);
    }
    const elapsed = Math.floor((now - startAnchor) * (room.playback.playbackRate || 1.0));
    return this._clampPosition(room.playback.basePositionMs + elapsed, track.durationMs);
  }

  cleanupExpiredRooms() {
    const now = this.now();
    for (const room of this.rooms.values()) {
      if (room.expiresAt <= now) {
        this._closeAndDeleteRoom(room, 'ROOM_EXPIRED', 'Room expired');
      }
    }
  }

  _broadcastSyncPulses() {
    const now = this.now();
    for (const room of this.rooms.values()) {
      if (room.expiresAt <= now) {
        this._closeAndDeleteRoom(room, 'ROOM_EXPIRED', 'Room expired');
        continue;
      }
      if (!room.playback.isPlaying) continue;
      const currentTrack = room.currentIndex >= 0 ? room.queue[room.currentIndex] : null;
      if (!currentTrack) continue;

      const payload = {
        type: 'sync_pulse',
        roomCode: room.roomCode,
        serverTimeMs: now,
        stateRevision: room.playback.stateRevision,
        queueRevision: room.playback.queueRevision,
        currentIndex: room.currentIndex,
        currentTrackId: currentTrack.trackId,
        isPlaying: room.playback.isPlaying,
        basePositionMs: room.playback.basePositionMs,
        scheduledStartServerTimeMs: room.playback.scheduledStartServerTimeMs,
        effectivePositionMs: this.computeEffectivePositionMs(room, now),
        playbackRate: room.playback.playbackRate,
      };
      this._broadcastJson(room, payload);
    }
  }

  _removeParticipant(room, participantId, reason) {
    const participant = room.participants.get(participantId);
    if (!participant) return;

    if (participant.disconnectTimer) {
      clearTimeout(participant.disconnectTimer);
      participant.disconnectTimer = null;
    }
    if (participant.ws) {
      this.socketBindings.delete(participant.ws);
      participant.ws = null;
    }
    room.participants.delete(participantId);

    if (room.participants.size === 0) {
      this._closeAndDeleteRoom(room, 'ROOM_EMPTY', 'All participants left the room');
      return;
    }

    if (room.hostId === participantId) {
      if (room.hostTransferTimer) {
        clearTimeout(room.hostTransferTimer);
        room.hostTransferTimer = null;
      }
      this._transferHostImmediately(room, reason);
    } else {
      this._broadcastRoomState(room, 'participant_updated');
    }
  }

  _evictDisconnectedParticipant(room, participantId) {
    const participant = room.participants.get(participantId);
    if (!participant || participant.connectionState === 'CONNECTED') return;
    this._removeParticipant(room, participantId, 'HOST_DISCONNECTED');
  }

  _maybeTransferHost(room, formerHostId, reason) {
    if (room.hostId !== formerHostId) return;
    const formerHost = room.participants.get(formerHostId);
    if (formerHost && formerHost.connectionState === 'CONNECTED') return;
    this._transferHostImmediately(room, reason);
  }

  _transferHostImmediately(room, reason) {
    const candidates = Array.from(room.participants.values()).sort((a, b) => a.joinedAt - b.joinedAt);
    const connectedCandidate = candidates.find((p) => p.connectionState === 'CONNECTED');
    const nextHost = connectedCandidate || candidates[0];

    if (!nextHost) {
      this._closeAndDeleteRoom(room, 'ROOM_EMPTY', 'Room closed because no participants remain');
      return;
    }

    for (const p of room.participants.values()) {
      p.isHost = p.participantId === nextHost.participantId;
    }
    room.hostId = nextHost.participantId;
    room.playback.stateRevision += 1;
    room.playback.lastAction = 'HOST_TRANSFERRED';

    const now = this.now();
    this._broadcastJson(room, {
      type: 'host_changed',
      newHostId: nextHost.participantId,
      newHostName: nextHost.displayName,
      reason,
      serverTimeMs: now,
      room: this._serializeRoom(room, now),
    });
  }

  _checkEmptyRoomTimer(room) {
    const anyConnected = Array.from(room.participants.values())
      .some((p) => p.connectionState === 'CONNECTED');
    if (anyConnected) {
      this._cancelEmptyRoomTimer(room);
      return;
    }
    if (!room.emptyRoomTimer) {
      room.emptyRoomTimer = setTimeout(() => {
        room.emptyRoomTimer = null;
        const stillAnyConnected = Array.from(room.participants.values())
          .some((p) => p.connectionState === 'CONNECTED');
        if (!stillAnyConnected) {
          this._closeAndDeleteRoom(room, 'ROOM_INACTIVE', 'Room closed due to inactivity');
        }
      }, this.emptyRoomGraceMs);
      room.emptyRoomTimer.unref?.();
    }
  }

  _cancelEmptyRoomTimer(room) {
    if (room.emptyRoomTimer) {
      clearTimeout(room.emptyRoomTimer);
      room.emptyRoomTimer = null;
    }
  }

  _closeAndDeleteRoom(room, reason, message) {
    this._clearRoomTimers(room);
    const now = this.now();
    this._broadcastJson(room, {
      type: 'room_closed',
      roomCode: room.roomCode,
      reason,
      message,
      serverTimeMs: now,
    });
    for (const p of room.participants.values()) {
      if (p.disconnectTimer) clearTimeout(p.disconnectTimer);
      if (p.ws) {
        this.socketBindings.delete(p.ws);
      }
    }
    this.rooms.delete(room.roomCode);
  }

  _clearRoomTimers(room) {
    if (room.hostTransferTimer) {
      clearTimeout(room.hostTransferTimer);
      room.hostTransferTimer = null;
    }
    if (room.emptyRoomTimer) {
      clearTimeout(room.emptyRoomTimer);
      room.emptyRoomTimer = null;
    }
    for (const p of room.participants.values()) {
      if (p.disconnectTimer) {
        clearTimeout(p.disconnectTimer);
        p.disconnectTimer = null;
      }
    }
  }

  _detachSocket(ws) {
    const existing = this.socketBindings.get(ws);
    if (!existing) return;
    this.socketBindings.delete(ws);
    const room = this.rooms.get(existing.roomCode);
    if (room) {
      this._removeParticipant(room, existing.participantId, 'HOST_LEFT');
    }
  }

  _requireRoomParticipant(ws) {
    const binding = this.socketBindings.get(ws);
    if (!binding) {
      this._sendError(ws, 'NOT_IN_ROOM', 'Join or create a room first');
      return null;
    }
    const room = this.rooms.get(binding.roomCode);
    const now = this.now();
    if (!room || room.expiresAt <= now) {
      if (room) this._closeAndDeleteRoom(room, 'ROOM_EXPIRED', 'Room expired');
      this._sendError(ws, 'ROOM_EXPIRED', 'Room expired');
      return null;
    }
    const participant = room.participants.get(binding.participantId);
    if (!participant) {
      this._sendError(ws, 'UNAUTHORIZED', 'Participant membership invalid');
      return null;
    }
    return { room, participant };
  }

  _touchRoom(room, now) {
    room.expiresAt = Math.min(now + this.roomTtlMs, room.hardExpiresAt);
  }

  _recordCommandId(room, commandId) {
    room.seenCommandIds.push(commandId);
    room.seenCommandIdSet.add(commandId);
    if (room.seenCommandIds.length > 200) {
      const oldest = room.seenCommandIds.shift();
      room.seenCommandIdSet.delete(oldest);
    }
  }

  _clampPosition(positionMs, durationMs) {
    if (!Number.isFinite(positionMs) || positionMs < 0) return 0;
    if (Number.isFinite(durationMs) && durationMs > 0) {
      return Math.min(positionMs, durationMs);
    }
    return positionMs;
  }

  _serializeRoom(room, now) {
    const currentTrack = room.currentIndex >= 0 ? room.queue[room.currentIndex] : null;
    return {
      roomId: room.roomId,
      roomCode: room.roomCode,
      createdAt: room.createdAt,
      expiresAt: room.expiresAt,
      hostId: room.hostId,
      controlMode: room.controlMode,
      currentIndex: room.currentIndex,
      currentTrack,
      queue: room.queue,
      participants: Array.from(room.participants.values()).map((p) => ({
        participantId: p.participantId,
        displayName: p.displayName,
        isHost: p.isHost,
        connectionState: p.connectionState,
        joinedAt: p.joinedAt,
        qualityLabel: p.qualityLabel,
      })),
      playback: {
        isPlaying: room.playback.isPlaying,
        basePositionMs: room.playback.basePositionMs,
        anchorServerTimeMs: room.playback.anchorServerTimeMs,
        scheduledStartServerTimeMs: room.playback.scheduledStartServerTimeMs,
        effectivePositionMs: this.computeEffectivePositionMs(room, now),
        playbackRate: room.playback.playbackRate,
        stateRevision: room.playback.stateRevision,
        queueRevision: room.playback.queueRevision,
        lastCommandId: room.playback.lastCommandId,
        lastUpdatedBy: room.playback.lastUpdatedBy,
        lastAction: room.playback.lastAction,
      },
    };
  }

  _broadcastRoomState(room, type, excludeParticipantId = null) {
    const now = this.now();
    this._broadcastJson(
      room,
      {
        type,
        serverTimeMs: now,
        room: this._serializeRoom(room, now),
      },
      excludeParticipantId
    );
  }

  _broadcastJson(room, payload, excludeParticipantId = null) {
    const serialized = JSON.stringify(payload);
    for (const p of room.participants.values()) {
      if (excludeParticipantId && p.participantId === excludeParticipantId) continue;
      if (p.ws && p.connectionState === 'CONNECTED') {
        p.ws.sendText(serialized);
      }
    }
  }

  _sendError(ws, code, message) {
    ws.sendJson({
      type: 'error',
      code,
      message,
      serverTimeMs: this.now(),
    });
  }
}
