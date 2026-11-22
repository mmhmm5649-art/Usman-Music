#!/usr/bin/env node
/**
 * Live Deployment Verification Script for USMAN MUSIC Listen Together Backend.
 *
 * Usage:
 *   node verify-deployment.mjs https://usman-music-listen-together.onrender.com
 *   node verify-deployment.mjs wss://usman-music-listen-together.onrender.com/ws
 *   LISTEN_TOGETHER_WS_URL=wss://... node verify-deployment.mjs
 *
 * Verifies:
 *   1. HTTPS GET /health responds with 200 OK & valid JSON (with cold-start retries).
 *   2. Two real concurrent WebSocket clients (Host A & Guest B) connect to /ws.
 *   3. Room creation (6-char code) + public room status endpoint.
 *   4. Guest joins Host's room and both receive presence updates.
 *   5. NTP clock sync (time_sync_ping / time_sync_pong) and RTT measurement.
 *   6. Synchronized play_track broadcast with scheduledStartServerTimeMs.
 *   7. Clean host end_room teardown leaving zero stray rooms.
 */

import assert from 'node:assert/strict';

function normalizeTargets(rawInput) {
  const input = String(rawInput || '').trim();
  if (!input) {
    throw new Error(
      'Missing server URL.\n' +
      'Usage: node verify-deployment.mjs https://<your-service>.onrender.com\n' +
      '   or: node verify-deployment.mjs wss://<your-service>.onrender.com/ws'
    );
  }

  let parsed;
  try {
    parsed = new URL(input.includes('://') ? input : `https://${input}`);
  } catch {
    throw new Error(`Invalid URL: "${input}"`);
  }

  const isSecure = parsed.protocol === 'https:' || parsed.protocol === 'wss:';
  const isPlain = parsed.protocol === 'http:' || parsed.protocol === 'ws:';
  if (!isSecure && !isPlain) {
    throw new Error(`Unsupported protocol "${parsed.protocol}". Use https://, wss://, http://, or ws://`);
  }

  const httpProtocol = isSecure ? 'https:' : 'http:';
  const wsProtocol = isSecure ? 'wss:' : 'ws:';

  let basePath = parsed.pathname.replace(/\/+$/, '');
  if (basePath.endsWith('/ws')) {
    basePath = basePath.slice(0, -3);
  } else if (basePath.endsWith('/health')) {
    basePath = basePath.slice(0, -7);
  }

  const httpBase = `${httpProtocol}//${parsed.host}${basePath}`;
  const wsUrl = `${wsProtocol}//${parsed.host}${basePath}/ws`;

  return { httpBase, healthUrl: `${httpBase}/health`, wsUrl };
}

class VerifyWsClient {
  constructor(url) {
    this.ws = new WebSocket(url);
    this.queue = [];
    this.waiters = [];
    this.closed = false;

    this.ready = new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`WebSocket connection timed out: ${url}`)), 15_000);
      this.ws.addEventListener('open', () => {
        clearTimeout(timer);
        resolve();
      });
      this.ws.addEventListener('error', (err) => {
        clearTimeout(timer);
        reject(new Error(`WebSocket error connecting to ${url}: ${err.message || 'connection failed'}`));
      });
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

  send(payload) {
    this.ws.send(JSON.stringify(payload));
  }

  nextMessage(predicate = () => true, timeoutMs = 8_000) {
    const idx = this.queue.findIndex(predicate);
    if (idx >= 0) {
      return Promise.resolve(this.queue.splice(idx, 1)[0]);
    }
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        const i = this.waiters.indexOf(waiter);
        if (i >= 0) this.waiters.splice(i, 1);
        reject(new Error(`Timed out after ${timeoutMs}ms waiting for expected WebSocket event`));
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

async function fetchHealthWithWakeup(healthUrl, maxAttempts = 6) {
  let lastError = null;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 15_000);
      const res = await fetch(healthUrl, { signal: controller.signal });
      clearTimeout(timeout);
      if (res.ok) {
        const json = await res.json();
        return json;
      }
      lastError = new Error(`HTTP ${res.status} from ${healthUrl}`);
    } catch (err) {
      lastError = err;
    }
    if (attempt < maxAttempts) {
      console.log(`  Attempt ${attempt}/${maxAttempts} did not respond yet (Render Free instances may take 30-60s to wake). Retrying in 5s...`);
      await new Promise((r) => setTimeout(r, 5_000));
    }
  }
  throw lastError || new Error(`Health check failed for ${healthUrl}`);
}

async function main() {
  const rawArg = process.argv[2] || process.env.LISTEN_TOGETHER_WS_URL || '';
  const { httpBase, healthUrl, wsUrl } = normalizeTargets(rawArg);

  console.log('=== USMAN MUSIC Listen Together Deployment Verification ===');
  console.log(`Health URL : ${healthUrl}`);
  console.log(`WS URL     : ${wsUrl}`);
  console.log('');

  // 1. Health Check
  console.log('[1/6] Checking /health endpoint...');
  const health = await fetchHealthWithWakeup(healthUrl);
  assert.equal(health.status, 'ok', 'Expected health.status === "ok"');
  assert.equal(health.service, 'usman-music-listen-together', 'Unexpected service identifier');
  console.log(`  OK — uptime: ${health.uptimeSec}s, activeRooms: ${health.stats?.activeRooms ?? 0}`);

  // 2. Open two concurrent WebSocket clients
  console.log('[2/6] Opening two real WebSocket connections (Host A & Guest B)...');
  const host = new VerifyWsClient(wsUrl);
  const guest = new VerifyWsClient(wsUrl);
  try {
    await Promise.all([host.ready, guest.ready]);
    console.log('  OK — Both WebSocket connections established.');

    // 3. Create Room
    console.log('[3/6] Creating room as Host A...');
    host.send({ type: 'create_room', displayName: 'Verify Host', qualityLabel: 'HD' });
    const created = await host.nextMessage((m) => m.type === 'room_joined');
    const roomCode = created.room.roomCode;
    assert.match(roomCode, /^[A-Z0-9]{6}$/, 'Expected 6-character alphanumeric room code');

    const statusRes = await fetch(`${httpBase}/api/rooms/${roomCode}/status`);
    assert.equal(statusRes.status, 200, 'Expected 200 from /api/rooms/:code/status');
    const statusJson = await statusRes.json();
    assert.equal(statusJson.exists, true);
    console.log(`  OK — Created live room ${roomCode} (verified via HTTP status API).`);

    // 4. Join Room from Guest B
    console.log('[4/6] Joining room as Guest B...');
    guest.send({ type: 'join_room', roomCode, displayName: 'Verify Guest', qualityLabel: 'Data Saver' });
    const [guestJoined, hostNotified] = await Promise.all([
      guest.nextMessage((m) => m.type === 'room_joined'),
      host.nextMessage((m) => m.type === 'participant_updated'),
    ]);
    assert.equal(guestJoined.room.roomCode, roomCode);
    assert.equal(hostNotified.room.participants.length, 2);
    console.log('  OK — Guest B joined room and Host A received participant presence update.');

    // 5. NTP Clock Sync + RTT measurement
    console.log('[5/6] Measuring NTP clock sync RTT...');
    const t0 = Date.now();
    guest.send({ type: 'time_sync_ping', clientSendTimeMs: t0 });
    const pong = await guest.nextMessage((m) => m.type === 'time_sync_pong');
    const t3 = Date.now();
    const rttMs = Math.max(0, t3 - t0);
    assert.equal(pong.clientSendTimeMs, t0);
    assert.ok(typeof pong.serverReceiveTimeMs === 'number' && typeof pong.serverSendTimeMs === 'number');
    console.log(`  OK — Clock sync succeeded (RTT: ${rttMs} ms).`);

    // 6. Synchronized Playback Broadcast + Host End Room cleanup
    console.log('[6/6] Verifying synchronized playback broadcast and clean end_room teardown...');
    host.send({
      type: 'playback_command',
      commandId: `verify_${Date.now()}`,
      action: 'SET_TRACK',
      track: {
        trackId: 'verify_track_001',
        provider: 'JIO_SAAVN',
        title: 'Verification Track',
        artist: 'USMAN MUSIC',
        album: 'Deployment Check',
        artworkUrl: '',
        durationMs: 180000,
      },
      positionMs: 0,
      autoPlay: true,
    });
    const guestSync = await guest.nextMessage((m) => m.type === 'playback_state');
    assert.equal(guestSync.room.currentTrack?.trackId, 'verify_track_001');
    assert.equal(guestSync.room.playback.isPlaying, true);
    assert.ok(guestSync.room.playback.scheduledStartServerTimeMs > 0);

    host.send({ type: 'end_room' });
    const [hostEnded, guestEnded] = await Promise.all([
      host.nextMessage((m) => m.type === 'room_ended'),
      guest.nextMessage((m) => m.type === 'room_ended'),
    ]);
    assert.equal(hostEnded.reason, 'HOST_ENDED_ROOM');
    assert.equal(guestEnded.reason, 'HOST_ENDED_ROOM');

    console.log('  OK — Synchronized playback broadcast and clean room teardown verified.');
    console.log('');
    console.log('ALL DEPLOYMENT CHECKS PASSED.');
    console.log(`Configure this exact URL in USMAN MUSIC on both Android phones:\n  ${wsUrl}`);
  } finally {
    host.close();
    guest.close();
  }
}

main().catch((err) => {
  console.error('\nDEPLOYMENT VERIFICATION FAILED:');
  console.error(err instanceof Error ? err.message : err);
  process.exit(1);
});
