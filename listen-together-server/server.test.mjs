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

