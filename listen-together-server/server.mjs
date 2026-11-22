import crypto from 'node:crypto';
import fs from 'node:fs';
import http from 'node:http';
import { pathToFileURL } from 'node:url';
import { RoomManager } from './room-manager.mjs';
import { upgradeWebSocket, WsCloseCode } from './ws-protocol.mjs';

function timingSafeEqualString(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const bufA = Buffer.from(a, 'utf8');
  const bufB = Buffer.from(b, 'utf8');
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

function getClientIp(req) {
  const xff = req.headers['x-forwarded-for'];
  if (typeof xff === 'string' && xff.trim().length > 0) {
    return xff.split(',')[0].trim();
  }
  return req.socket?.remoteAddress || 'unknown';
}

class TokenBucket {
  constructor(capacity, refillPerSec) {
    this.capacity = capacity;
    this.refillPerSec = refillPerSec;
    this.tokens = capacity;
    this.lastRefillMs = Date.now();
  }

  consume(cost = 1) {
    const now = Date.now();
    const elapsedSec = Math.max(0, (now - this.lastRefillMs) / 1000);
    this.tokens = Math.min(this.capacity, this.tokens + elapsedSec * this.refillPerSec);
    this.lastRefillMs = now;
    if (this.tokens < cost) return false;
    this.tokens -= cost;
    return true;
  }
}

export function createListenTogetherServer(options = {}) {
  const port = options.port ?? Number(process.env.PORT || 8080);
  const host = options.host ?? (process.env.HOST || '0.0.0.0');
  const apiKey = options.apiKey ?? (process.env.LISTEN_TOGETHER_API_KEY || '');
  const allowedOrigins = (options.allowedOrigins ?? (process.env.ALLOWED_ORIGINS || ''))
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);

  const maxConnectionsPerIp = options.maxConnectionsPerIp ?? Number(process.env.MAX_CONNECTIONS_PER_IP || 20);
  const maxUpgradesPerMinutePerIp = options.maxUpgradesPerMinutePerIp ?? Number(process.env.MAX_UPGRADES_PER_MIN_PER_IP || 60);
  const heartbeatIntervalMs = options.heartbeatIntervalMs ?? Number(process.env.WS_HEARTBEAT_MS || 25_000);
  const socketRateLimitCapacity = options.socketRateLimitCapacity ?? 40;
  const socketRateLimitRefillPerSec = options.socketRateLimitRefillPerSec ?? 10;

  const roomManager = new RoomManager({
    roomTtlMs: options.roomTtlMs ?? Number(process.env.ROOM_TTL_MS || 6 * 60 * 60 * 1000),
    hostTransferGraceMs: options.hostTransferGraceMs ?? Number(process.env.HOST_TRANSFER_GRACE_MS || 20_000),
    participantDisconnectGraceMs: options.participantDisconnectGraceMs ?? Number(process.env.PARTICIPANT_DISCONNECT_GRACE_MS || 60_000),
    emptyRoomGraceMs: options.emptyRoomGraceMs ?? Number(process.env.EMPTY_ROOM_GRACE_MS || 5 * 60 * 1000),
    playLeadTimeMs: options.playLeadTimeMs ?? 350,
    trackChangeLeadTimeMs: options.trackChangeLeadTimeMs ?? 650,
    skipDebounceMs: options.skipDebounceMs ?? 450,
    syncPulseIntervalMs: options.syncPulseIntervalMs ?? 4000,
    cleanupIntervalMs: options.cleanupIntervalMs ?? 15_000,
  });

  const activeSockets = new Set();
  /** @type {Map<string, number>} ip -> active connection count */
  const ipConnections = new Map();
  /** @type {Map<string, number[]>} ip -> upgrade timestamps in last 60s */
  const ipUpgradeWindow = new Map();

  const startedAtMs = Date.now();

  const server = http.createServer((req, res) => {
    const parsedUrl = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);

    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Cache-Control', 'no-store');

    if ((req.method === 'GET' || req.method === 'HEAD') && parsedUrl.pathname === '/') {
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      if (req.method === 'HEAD') {
        res.end();
        return;
      }
      res.end(JSON.stringify({
        status: 'ok',
        service: 'usman-music-listen-together',
        endpoints: {
          health: '/health',
          websocket: '/ws',
          roomStatus: '/api/rooms/:code/status',
        },
        serverTimeMs: Date.now(),
      }));
      return;
    }

    if ((req.method === 'GET' || req.method === 'HEAD') && parsedUrl.pathname === '/health') {
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
      if (req.method === 'HEAD') {
        res.end();
        return;
      }
      res.end(JSON.stringify({
        status: 'ok',
        service: 'usman-music-listen-together',
        serverTimeMs: Date.now(),
        uptimeSec: Math.floor((Date.now() - startedAtMs) / 1000),
        stats: roomManager.getStats(),
      }));
      return;
    }

    const roomStatusMatch = parsedUrl.pathname.match(/^\/api\/rooms\/([A-Za-z0-9]{4,12})\/status$/);
    if (req.method === 'GET' && roomStatusMatch) {
      if (apiKey) {
        const providedKey = String(req.headers['x-api-key'] || '');
        if (!timingSafeEqualString(providedKey, apiKey)) {
          res.writeHead(401, { 'Content-Type': 'application/json; charset=utf-8' });
          res.end(JSON.stringify({ error: 'UNAUTHORIZED' }));
          return;
        }
      }
      const status = roomManager.getRoomPublicStatus(roomStatusMatch[1]);
      res.writeHead(status.exists ? 200 : 404, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(status));
      return;
    }

    res.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ error: 'NOT_FOUND' }));
  });

  // Prevent 502 keep-alive races behind reverse proxies (Render, Cloudflare, ALB)
  server.keepAliveTimeout = 65_000;
  server.headersTimeout = 66_000;

  server.on('upgrade', (req, socket, head) => {
    const parsedUrl = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
    if (parsedUrl.pathname !== '/ws') {
      socket.write('HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n');
      socket.destroy();
      return;
    }

    // Optional origin check for browser clients
    const origin = req.headers.origin;
    if (allowedOrigins.length > 0 && origin && !allowedOrigins.includes(origin)) {
      socket.write('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n');
      socket.destroy();
      return;
    }

    // Optional API key authentication
    if (apiKey) {
      const providedKey = String(req.headers['x-api-key'] || '');
      if (!timingSafeEqualString(providedKey, apiKey)) {
        socket.write('HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n');
        socket.destroy();
        return;
      }
    }

    // Per-IP connection & upgrade rate limit
    const ip = getClientIp(req);
    const now = Date.now();
    const recentUpgrades = (ipUpgradeWindow.get(ip) || []).filter((ts) => now - ts < 60_000);
    if (recentUpgrades.length >= maxUpgradesPerMinutePerIp) {
      socket.write('HTTP/1.1 429 Too Many Requests\r\nConnection: close\r\n\r\n');
      socket.destroy();
      return;
    }
    recentUpgrades.push(now);
    ipUpgradeWindow.set(ip, recentUpgrades);

    const activeForIp = ipConnections.get(ip) || 0;
    if (activeForIp >= maxConnectionsPerIp) {
      socket.write('HTTP/1.1 429 Too Many Requests\r\nConnection: close\r\n\r\n');
      socket.destroy();
      return;
    }

    const ws = upgradeWebSocket(req, socket, head);
    if (!ws) return;

    ipConnections.set(ip, activeForIp + 1);
    activeSockets.add(ws);

    const bucket = new TokenBucket(socketRateLimitCapacity, socketRateLimitRefillPerSec);

    ws.on('message', (rawText) => {
      // Allow lightweight time sync pings more generously
      const isTimeSync = rawText.includes('"time_sync_ping"');
      if (!bucket.consume(isTimeSync ? 0.25 : 1)) {
        ws.sendJson({
          type: 'error',
          code: 'RATE_LIMITED',
          message: 'Too many requests; please slow down',
          serverTimeMs: Date.now(),
        });
        return;
      }
      roomManager.handleMessage(ws, rawText);
    });

    ws.on('close', () => {
      activeSockets.delete(ws);
      const count = (ipConnections.get(ip) || 1) - 1;
      if (count <= 0) {
        ipConnections.delete(ip);
      } else {
        ipConnections.set(ip, count);
      }
      roomManager.handleDisconnect(ws);
    });

    ws.on('error', () => {
      // Handled via close
    });
  });

  const heartbeatTimer = setInterval(() => {
    for (const ws of activeSockets) {
      if (!ws.isAlive) {
        ws.terminate();
        continue;
      }
      ws.isAlive = false;
      ws.ping();
    }
    // Prune stale IP upgrade windows
    const now = Date.now();
    for (const [ip, timestamps] of ipUpgradeWindow.entries()) {
      const fresh = timestamps.filter((ts) => now - ts < 60_000);
      if (fresh.length === 0) ipUpgradeWindow.delete(ip);
      else ipUpgradeWindow.set(ip, fresh);
    }
  }, heartbeatIntervalMs);
  heartbeatTimer.unref?.();

  return {
    server,
    roomManager,
    start() {
      return new Promise((resolve, reject) => {
        server.once('error', reject);
        server.listen(port, host, () => {
          server.removeListener('error', reject);
          const addr = server.address();
          resolve({
            host: typeof addr === 'object' && addr ? addr.address : host,
            port: typeof addr === 'object' && addr ? addr.port : port,
          });
        });
      });
    },
    close() {
      clearInterval(heartbeatTimer);
      for (const ws of Array.from(activeSockets)) {
        ws.close(WsCloseCode.GOING_AWAY, 'Server shutting down');
      }
      roomManager.destroy();
      return new Promise((resolve) => {
        server.close(() => resolve());
      });
    },
  };
}

// Run directly when executed as main module
let isMain = false;
if (process.argv[1]) {
  try {
    isMain = import.meta.url === pathToFileURL(fs.realpathSync(process.argv[1])).href;
  } catch {
    isMain = import.meta.url === pathToFileURL(process.argv[1]).href;
  }
}
if (isMain) {
  const instance = createListenTogetherServer();
  instance.start().then(({ host, port }) => {
    console.log(`[USMAN Listen Together] Server listening on http://${host}:${port} (WebSocket: /ws, Health: /health)`);
  }).catch((err) => {
    console.error('[USMAN Listen Together] Failed to start server:', err);
    process.exit(1);
  });

  const shutdown = async (signal) => {
    console.log(`[USMAN Listen Together] Received ${signal}, shutting down gracefully...`);
    await instance.close();
    process.exit(0);
  };
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));
}
