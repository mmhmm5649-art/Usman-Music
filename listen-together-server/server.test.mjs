import assert from 'node:assert/strict';
import test from 'node:test';
import { createListenTogetherServer } from './server.mjs';

const SAMPLE_TRACK_1 = {
  trackId: 'jiosaavn_track_001',
  provider: 'JIO_SAAVN',
  title: 'Naatu Naatu',
  artist: 'Rahul Sipligunj, Kaala Bhairava',
  album: 'RRR',
  artworkUrl: 'https://c.saavncdn.com/001/rrr-500x500.jpg',
  durationMs: 215000,
};

const SAMPLE_TRACK_2 = {
  trackId: 'jiosaavn_track_002',
  provider: 'JIO_SAAVN',
  title: 'Kesariya',
  artist: 'Arijit Singh',
  album: 'Brahmastra',
  artworkUrl: 'https://c.saavncdn.com/002/brahmastra-500x500.jpg',
  durationMs: 268000,
};

class TestWsClient {
  constructor(url) {
    this.url = url;
    this.ws = new WebSocket(url);
    this.queue = [];
    this.waiters = [];
    this.closed = false;

    this.ready = new Promise((resolve, reject) => {
      this.ws.addEventListener('open', () => resolve());
      this.ws.addEventListener('error', (err) => reject(err));
    });

    this.ws.addEventListener('message', (event) => {
      const msg = JSON.parse(String(event.data));
      const idx = this.waiters.findIndex((w) => w.predicate(msg));
      if (idx >= 0) {
        const waiter = this.waiters.splice(idx, 1)[0];
        clearTimeout(waiter.timer);
        waiter.resolve(msg);
      } else {
        this.queue.push(msg);
      }
    });

    this.ws.addEventListener('close', () => {
      this.closed = true;
    });
  }

  send(obj) {
    this.ws.send(JSON.stringify(obj));
  }

  nextMessage(predicate = () => true, timeoutMs = 3000) {
    const existingIdx = this.queue.findIndex(predicate);
    if (existingIdx >= 0) {
      return Promise.resolve(this.queue.splice(existingIdx, 1)[0]);
    }
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        const idx = this.waiters.indexOf(waiter);
        if (idx >= 0) this.waiters.splice(idx, 1);
        reject(new Error(`Timed out after ${timeoutMs}ms waiting for WebSocket message`));
      }, timeoutMs);
      const waiter = { predicate, resolve, reject, timer };
      this.waiters.push(waiter);
    });
  }

  close() {
    if (!this.closed) {
      this.ws.close();
    }
  }
}

async function startTestServer(options = {}) {
  const instance = createListenTogetherServer({
    port: 0,
    host: '127.0.0.1',
    syncPulseIntervalMs: 60_000,
    ...options,
  });
  const { port } = await instance.start();
  return {
    instance,
    baseHttpUrl: `http://127.0.0.1:${port}`,
    wsUrl: `ws://127.0.0.1:${port}/ws`,
  };
}

test('health endpoint and room creation + joining + participant presence', async () => {
  const { instance, baseHttpUrl, wsUrl } = await startTestServer();
  const host = new TestWsClient(wsUrl);
  const guest = new TestWsClient(wsUrl);

  try {
    await Promise.all([host.ready, guest.ready]);

    const rootRes = await fetch(`${baseHttpUrl}/`);
    assert.equal(rootRes.status, 200);
    const rootJson = await rootRes.json();
    assert.equal(rootJson.status, 'ok');
    assert.equal(rootJson.endpoints.websocket, '/ws');

    const headHealthRes = await fetch(`${baseHttpUrl}/health`, { method: 'HEAD' });
    assert.equal(headHealthRes.status, 200);

    const healthRes = await fetch(`${baseHttpUrl}/health`);
    assert.equal(healthRes.status, 200);
    const healthJson = await healthRes.json();
    assert.equal(healthJson.status, 'ok');

    host.send({
      type: 'create_room',
      displayName: 'Phone A',
      qualityLabel: 'HD',
      initialTrack: SAMPLE_TRACK_1,
      initialPositionMs: 12000,
      initialIsPlaying: true,
    });
    const hostJoined = await host.nextMessage((m) => m.type === 'room_joined');
    assert.equal(hostJoined.room.roomCode.length, 6);
    assert.equal(hostJoined.room.participants.length, 1);
    assert.equal(hostJoined.room.participants[0].isHost, true);
    assert.equal(hostJoined.room.currentTrack.trackId, SAMPLE_TRACK_1.trackId);
    assert.ok(hostJoined.sessionToken.length >= 32);

    const statusRes = await fetch(`${baseHttpUrl}/api/rooms/${hostJoined.room.roomCode}/status`);
    assert.equal(statusRes.status, 200);
    const statusJson = await statusRes.json();
    assert.equal(statusJson.exists, true);
    assert.equal(statusJson.participantCount, 1);

    guest.send({
      type: 'join_room',
      roomCode: hostJoined.room.roomCode,
      displayName: 'Phone B',
      qualityLabel: 'Data Saver',
    });
    const [guestJoined, hostParticipantUpdate] = await Promise.all([
      guest.nextMessage((m) => m.type === 'room_joined'),
      host.nextMessage((m) => m.type === 'participant_updated'),
    ]);

    assert.equal(guestJoined.room.roomCode, hostJoined.room.roomCode);
    assert.equal(guestJoined.room.participants.length, 2);
    assert.equal(guestJoined.room.currentTrack.trackId, SAMPLE_TRACK_1.trackId);
    assert.equal(guestJoined.room.playback.isPlaying, true);
    assert.equal(hostParticipantUpdate.room.participants.length, 2);

    // Quality labels stay independent per participant
    const phoneA = hostParticipantUpdate.room.participants.find((p) => p.displayName === 'Phone A');
    const phoneB = hostParticipantUpdate.room.participants.find((p) => p.displayName === 'Phone B');
    assert.equal(phoneA.qualityLabel, 'HD');
    assert.equal(phoneB.qualityLabel, 'Data Saver');
  } finally {
    host.close();
    guest.close();
    await instance.close();
  }
});

test('invalid and expired room codes are rejected cleanly', async () => {
  const { instance, wsUrl } = await startTestServer({ roomTtlMs: 80 });
  const host = new TestWsClient(wsUrl);
  const guest = new TestWsClient(wsUrl);

  try {
    await Promise.all([host.ready, guest.ready]);

    guest.send({ type: 'join_room', roomCode: 'BAD', displayName: 'Guest' });
    const invalidErr = await guest.nextMessage((m) => m.type === 'error');
    assert.equal(invalidErr.code, 'INVALID_ROOM_CODE');

    guest.send({ type: 'join_room', roomCode: 'ZZZZZZ', displayName: 'Guest' });
    const notFoundErr = await guest.nextMessage((m) => m.type === 'error');
    assert.equal(notFoundErr.code, 'ROOM_NOT_FOUND');

    host.send({ type: 'create_room', displayName: 'Host' });
    const created = await host.nextMessage((m) => m.type === 'room_joined');

    // Wait for short TTL to expire
    await new Promise((r) => setTimeout(r, 120));

    guest.send({ type: 'join_room', roomCode: created.room.roomCode, displayName: 'Late Guest' });
    const expiredErr = await guest.nextMessage((m) => m.type === 'error');
    assert.equal(expiredErr.code, 'ROOM_EXPIRED');
  } finally {
    host.close();
    guest.close();
    await instance.close();
  }
});

test('HOST_ONLY vs EVERYONE permissions, synchronized playback, queue, and anti-double-skip', async () => {
  const { instance, wsUrl } = await startTestServer({ skipDebounceMs: 500 });
  const host = new TestWsClient(wsUrl);
  const guest = new TestWsClient(wsUrl);

  try {
    await Promise.all([host.ready, guest.ready]);

    host.send({
      type: 'create_room',
      displayName: 'Host',
      initialQueue: [SAMPLE_TRACK_1, SAMPLE_TRACK_2],
      initialIndex: 0,
      initialIsPlaying: false,
      controlMode: 'HOST_ONLY',
    });
    const hostJoined = await host.nextMessage((m) => m.type === 'room_joined');

    guest.send({ type: 'join_room', roomCode: hostJoined.room.roomCode, displayName: 'Guest' });
    await guest.nextMessage((m) => m.type === 'room_joined');
    await host.nextMessage((m) => m.type === 'participant_updated');

    // Guest is forbidden in HOST_ONLY mode
    guest.send({ type: 'playback_command', commandId: 'cmd-1', action: 'PLAY' });
    const forbidden = await guest.nextMessage((m) => m.type === 'error');
    assert.equal(forbidden.code, 'FORBIDDEN');

    // Guest cannot switch control mode
    guest.send({ type: 'set_control_mode', controlMode: 'EVERYONE' });
    const modeForbidden = await guest.nextMessage((m) => m.type === 'error');
    assert.equal(modeForbidden.code, 'FORBIDDEN');

    // Host starts playback -> both receive scheduled future start timestamp
    host.send({ type: 'playback_command', commandId: 'cmd-play', action: 'PLAY', positionMs: 5000 });
    const [hostPlay, guestPlay] = await Promise.all([
      host.nextMessage((m) => m.type === 'playback_state'),
      guest.nextMessage((m) => m.type === 'playback_state'),
    ]);
    assert.equal(hostPlay.room.playback.isPlaying, true);
    assert.equal(guestPlay.room.playback.basePositionMs, 5000);
    assert.ok(guestPlay.room.playback.scheduledStartServerTimeMs > guestPlay.serverTimeMs);

    // Host pauses playback
    host.send({ type: 'playback_command', commandId: 'cmd-pause', action: 'PAUSE', positionMs: 5400 });
    const [hostPause, guestPause] = await Promise.all([
      host.nextMessage((m) => m.type === 'playback_state'),
      guest.nextMessage((m) => m.type === 'playback_state'),
    ]);
    assert.equal(hostPause.room.playback.isPlaying, false);
    assert.equal(guestPause.room.playback.isPlaying, false);
    assert.equal(guestPause.room.playback.basePositionMs, 5400);

    // Host enables EVERYONE mode
    host.send({ type: 'set_control_mode', controlMode: 'EVERYONE' });
    const [, guestModeUpdate] = await Promise.all([
      host.nextMessage((m) => m.type === 'room_state'),
      guest.nextMessage((m) => m.type === 'room_state'),
    ]);
    assert.equal(guestModeUpdate.room.controlMode, 'EVERYONE');

    // Simultaneous NEXT from host and guest -> advances once (anti-double-skip)
    host.send({ type: 'playback_command', commandId: 'cmd-next-1', action: 'NEXT' });
    guest.send({ type: 'playback_command', commandId: 'cmd-next-2', action: 'NEXT' });

    const guestNext = await guest.nextMessage((m) => m.type === 'playback_state' && m.room.currentIndex === 1);
    assert.equal(guestNext.room.currentTrack.trackId, SAMPLE_TRACK_2.trackId);

    // Wait briefly and verify index did not wrap back to 0 from the second simultaneous NEXT
    await new Promise((r) => setTimeout(r, 80));
    guest.send({ type: 'sync_request' });
    const synced = await guest.nextMessage((m) => m.type === 'playback_state' && m.room.currentIndex === 1);
    assert.equal(synced.room.currentIndex, 1);

    // Guest seeks cleanly in EVERYONE mode
    guest.send({ type: 'playback_command', commandId: 'cmd-seek', action: 'SEEK', positionMs: 42000 });
    const hostSeek = await host.nextMessage((m) => m.type === 'playback_state' && m.room.playback.basePositionMs === 42000);
    assert.equal(hostSeek.room.playback.basePositionMs, 42000);
  } finally {
    host.close();
    guest.close();
    await instance.close();
  }
});

test('time_sync_ping returns accurate 4-timestamp NTP pong', async () => {
  const { instance, wsUrl } = await startTestServer();
  const client = new TestWsClient(wsUrl);
  try {
    await client.ready;
    const t0 = Date.now();
    client.send({ type: 'time_sync_ping', seq: 7, clientSendTimeMs: t0 });
    const pong = await client.nextMessage((m) => m.type === 'time_sync_pong');
    assert.equal(pong.seq, 7);
    assert.equal(pong.clientSendTimeMs, t0);
    assert.ok(pong.serverReceiveTimeMs >= t0 - 5000);
    assert.ok(pong.serverSendTimeMs >= pong.serverReceiveTimeMs);
  } finally {
    client.close();
    await instance.close();
  }
});

test('disconnect, reconnect with session token, and host departure transfer', async () => {
  const { instance, wsUrl } = await startTestServer({
    hostTransferGraceMs: 120,
    participantDisconnectGraceMs: 5000,
  });

  const host = new TestWsClient(wsUrl);
  const guest = new TestWsClient(wsUrl);
  let guestReconnect = null;

  try {
    await Promise.all([host.ready, guest.ready]);

    host.send({ type: 'create_room', displayName: 'Host A', initialTrack: SAMPLE_TRACK_1, initialIsPlaying: true });
    const hostJoined = await host.nextMessage((m) => m.type === 'room_joined');

    guest.send({ type: 'join_room', roomCode: hostJoined.room.roomCode, displayName: 'Guest B' });
    const guestJoined = await guest.nextMessage((m) => m.type === 'room_joined');
    await host.nextMessage((m) => m.type === 'participant_updated');

    // Guest drops connection abruptly
    guest.close();
    const reconnectingNotice = await host.nextMessage((m) =>
      m.type === 'participant_updated' &&
      m.room.participants.some((p) => p.participantId === guestJoined.participantId && p.connectionState === 'RECONNECTING')
    );
    assert.equal(reconnectingNotice.room.participants.length, 2);

    // Wrong token must be rejected
    guestReconnect = new TestWsClient(wsUrl);
    await guestReconnect.ready;
    guestReconnect.send({
      type: 'reconnect_room',
      roomCode: hostJoined.room.roomCode,
      participantId: guestJoined.participantId,
      sessionToken: 'wrong-token',
    });
    const unauth = await guestReconnect.nextMessage((m) => m.type === 'error');
    assert.equal(unauth.code, 'UNAUTHORIZED');

    // Valid token restores Guest B and recovers live playback state
    guestReconnect.send({
      type: 'reconnect_room',
      roomCode: hostJoined.room.roomCode,
      participantId: guestJoined.participantId,
      sessionToken: guestJoined.sessionToken,
    });
    const restored = await guestReconnect.nextMessage((m) => m.type === 'room_joined');
    assert.equal(restored.reconnected, true);
    assert.equal(restored.room.playback.isPlaying, true);
    assert.equal(restored.room.currentTrack.trackId, SAMPLE_TRACK_1.trackId);

    // Host explicitly leaves -> Guest B automatically promoted to Host
    host.send({ type: 'leave_room' });
    const hostChanged = await guestReconnect.nextMessage((m) => m.type === 'host_changed');
    assert.equal(hostChanged.newHostId, guestJoined.participantId);
    assert.equal(hostChanged.reason, 'HOST_LEFT');
    assert.equal(hostChanged.room.hostId, guestJoined.participantId);
  } finally {
    host.close();
    guest.close();
    guestReconnect?.close();
    await instance.close();
  }
});

test('per-socket rate limiting protects room server from command floods', async () => {
  const { instance, wsUrl } = await startTestServer({
    socketRateLimitCapacity: 5,
    socketRateLimitRefillPerSec: 0.1,
  });
  const client = new TestWsClient(wsUrl);
  try {
    await client.ready;
    for (let i = 0; i < 8; i++) {
      client.send({ type: 'sync_request' });
    }
    const rateErr = await client.nextMessage((m) => m.type === 'error' && m.code === 'RATE_LIMITED');
    assert.equal(rateErr.code, 'RATE_LIMITED');
  } finally {
    client.close();
    await instance.close();
  }
});

test('only host can end room, broadcasting room_ended and removing room state', async () => {
  const { instance, wsUrl, baseHttpUrl } = await startTestServer();
  const host = new TestWsClient(wsUrl);
  const guest = new TestWsClient(wsUrl);
  try {
    await Promise.all([host.ready, guest.ready]);

    host.send({ type: 'create_room', displayName: 'Host A' });
    const created = await host.nextMessage((m) => m.type === 'room_joined');
    const code = created.room.roomCode;

    guest.send({ type: 'join_room', roomCode: code, displayName: 'Guest B' });
    await guest.nextMessage((m) => m.type === 'room_joined');
    await host.nextMessage((m) => m.type === 'participant_updated');

    // Guest attempting to end room is rejected
    guest.send({ type: 'end_room' });
    const forbidden = await guest.nextMessage((m) => m.type === 'error');
    assert.equal(forbidden.code, 'FORBIDDEN');

    // Host ends room -> both host and guest receive room_ended
    host.send({ type: 'end_room' });
    const [hostEnded, guestEnded] = await Promise.all([
      host.nextMessage((m) => m.type === 'room_ended'),
      guest.nextMessage((m) => m.type === 'room_ended'),
    ]);
    assert.equal(hostEnded.reason, 'HOST_ENDED_ROOM');
    assert.equal(guestEnded.reason, 'HOST_ENDED_ROOM');

    const statusRes = await fetch(`${baseHttpUrl}/api/rooms/${code}/status`);
    const statusJson = await statusRes.json();
    assert.equal(statusJson.exists, false);
  } finally {
    host.close();
    guest.close();
    await instance.close();
  }
});

test('Normal mode supports up to 10 devices and rejects the 11th participant cleanly', async () => {
  const { instance, wsUrl } = await startTestServer();
  const clients = [];
  try {
    const host = new TestWsClient(wsUrl);
    clients.push(host);
    await host.ready;

    host.send({ type: 'create_room', displayName: 'Phone 1', listeningMode: 'NORMAL' });
    const created = await host.nextMessage((m) => m.type === 'room_joined');
    assert.equal(created.room.listeningMode, 'NORMAL');
    assert.equal(created.room.maxParticipants, 10);
    assert.equal(created.room.participants[0].speakerRole, 'STEREO');

    // Join 9 more devices (total 10)
    for (let i = 2; i <= 10; i++) {
      const guest = new TestWsClient(wsUrl);
      clients.push(guest);
      await guest.ready;
      guest.send({ type: 'join_room', roomCode: created.room.roomCode, displayName: `Phone ${i}` });
      const joined = await guest.nextMessage((m) => m.type === 'room_joined');
      assert.equal(joined.room.participants.length, i);
      const me = joined.room.participants.find((p) => p.participantId === joined.participantId);
      assert.equal(me.speakerRole, 'STEREO');
    }

    // 11th device must be rejected with ROOM_FULL
    const eleventh = new TestWsClient(wsUrl);
    clients.push(eleventh);
    await eleventh.ready;
    eleventh.send({ type: 'join_room', roomCode: created.room.roomCode, displayName: 'Phone 11' });
    const err = await eleventh.nextMessage((m) => m.type === 'error');
    assert.equal(err.code, 'ROOM_FULL');
  } finally {
    clients.forEach((c) => c.close());
    await instance.close();
  }
});

test('Stereo Split mode enforces 2-device cap, LEFT/RIGHT roles, duplicate prevention, swap, slot release, reconnect, and return to NORMAL', async () => {
  const { instance, wsUrl } = await startTestServer();
  const host = new TestWsClient(wsUrl);
  const phone2 = new TestWsClient(wsUrl);
  const phone3 = new TestWsClient(wsUrl);
  const phone2b = new TestWsClient(wsUrl);
  let phone2Reconnect = null;

  try {
    await Promise.all([host.ready, phone2.ready, phone3.ready, phone2b.ready]);

    // 1. Create normal room with 2 devices
    host.send({ type: 'create_room', displayName: 'Phone Left', initialTrack: SAMPLE_TRACK_1, initialIsPlaying: true });
    const hostJoined = await host.nextMessage((m) => m.type === 'room_joined');
    const code = hostJoined.room.roomCode;
    assert.equal(hostJoined.room.listeningMode, 'NORMAL');

    phone2.send({ type: 'join_room', roomCode: code, displayName: 'Phone Right' });
    const phone2Joined = await phone2.nextMessage((m) => m.type === 'room_joined');
    await host.nextMessage((m) => m.type === 'participant_updated');

    // 2. Host switches room into STEREO_SPLIT -> host gets LEFT, phone2 gets RIGHT
    host.send({ type: 'set_listening_mode', listeningMode: 'STEREO_SPLIT' });
    const [hostSplit, p2Split] = await Promise.all([
      host.nextMessage((m) => m.type === 'room_state' && m.room.listeningMode === 'STEREO_SPLIT'),
      phone2.nextMessage((m) => m.type === 'room_state' && m.room.listeningMode === 'STEREO_SPLIT'),
    ]);
    assert.equal(hostSplit.room.maxParticipants, 2);
    assert.equal(p2Split.room.participants.find((p) => p.participantId === hostJoined.participantId).speakerRole, 'LEFT');
    assert.equal(p2Split.room.participants.find((p) => p.participantId === phone2Joined.participantId).speakerRole, 'RIGHT');

    // 3. 3rd participant joining STEREO_SPLIT room is rejected with STEREO_ROOM_FULL
    phone3.send({ type: 'join_room', roomCode: code, displayName: 'Phone 3' });
    const thirdErr = await phone3.nextMessage((m) => m.type === 'error');
    assert.equal(thirdErr.code, 'STEREO_ROOM_FULL');

    // 4. Duplicate role assignment without swap=true is rejected
    host.send({
      type: 'assign_speaker_role',
      participantId: phone2Joined.participantId,
      speakerRole: 'LEFT',
      swap: false,
    });
    const dupErr = await host.nextMessage((m) => m.type === 'error');
    assert.equal(dupErr.code, 'DUPLICATE_SPEAKER_ROLE');

    // 5. Swap LEFT/RIGHT cleanly with swap=true
    host.send({
      type: 'assign_speaker_role',
      participantId: hostJoined.participantId,
      speakerRole: 'RIGHT',
      swap: true,
    });
    const [hostSwapped, p2Swapped] = await Promise.all([
      host.nextMessage((m) => m.type === 'room_state' && m.room.participants.find((p) => p.participantId === hostJoined.participantId).speakerRole === 'RIGHT'),
      phone2.nextMessage((m) => m.type === 'room_state' && m.room.participants.find((p) => p.participantId === phone2Joined.participantId).speakerRole === 'LEFT'),
    ]);
    assert.equal(hostSwapped.room.participants.find((p) => p.participantId === hostJoined.participantId).speakerRole, 'RIGHT');
    assert.equal(p2Swapped.room.participants.find((p) => p.participantId === phone2Joined.participantId).speakerRole, 'LEFT');

    // 6. Reconnect preserves STEREO_SPLIT mode and assigned LEFT role
    phone2.close();
    await host.nextMessage((m) => m.type === 'participant_updated');
    phone2Reconnect = new TestWsClient(wsUrl);
    await phone2Reconnect.ready;
    phone2Reconnect.send({
      type: 'reconnect_room',
      roomCode: code,
      participantId: phone2Joined.participantId,
      sessionToken: phone2Joined.sessionToken,
    });
    const restored = await phone2Reconnect.nextMessage((m) => m.type === 'room_joined');
    assert.equal(restored.room.listeningMode, 'STEREO_SPLIT');
    assert.equal(restored.room.participants.find((p) => p.participantId === phone2Joined.participantId).speakerRole, 'LEFT');

    // 7. Phone 2 leaves -> slot is released -> new Phone 2B joins and gets vacant LEFT role
    phone2Reconnect.send({ type: 'leave_room' });
    await host.nextMessage((m) => m.type === 'participant_updated' && m.room.participants.length === 1);

    phone2b.send({ type: 'join_room', roomCode: code, displayName: 'Replacement Phone' });
    const p2bJoined = await phone2b.nextMessage((m) => m.type === 'room_joined');
    assert.equal(p2bJoined.room.participants.length, 2);
    assert.equal(p2bJoined.room.participants.find((p) => p.participantId === p2bJoined.participantId).speakerRole, 'LEFT');

    // 8. Switch back to NORMAL -> restores STEREO on all participants and allows 3rd phone to join
    host.send({ type: 'set_listening_mode', listeningMode: 'NORMAL' });
    const [hostNormal, p2bNormal] = await Promise.all([
      host.nextMessage((m) => m.type === 'room_state' && m.room.listeningMode === 'NORMAL'),
      phone2b.nextMessage((m) => m.type === 'room_state' && m.room.listeningMode === 'NORMAL'),
    ]);
    assert.equal(hostNormal.room.maxParticipants, 10);
    assert.ok(p2bNormal.room.participants.every((p) => p.speakerRole === 'STEREO'));

    // 9. Phone 3 joins now that room is NORMAL; trying to switch to STEREO_SPLIT with 3 devices fails cleanly
    phone3.send({ type: 'join_room', roomCode: code, displayName: 'Phone 3' });
    await phone3.nextMessage((m) => m.type === 'room_joined');
    host.send({ type: 'set_listening_mode', listeningMode: 'STEREO_SPLIT' });
    const limitErr = await host.nextMessage((m) => m.type === 'error');
    assert.equal(limitErr.code, 'STEREO_MODE_PARTICIPANT_LIMIT');
  } finally {
    host.close();
    phone2.close();
    phone3.close();
    phone2b.close();
    phone2Reconnect?.close();
    await instance.close();
  }
});


