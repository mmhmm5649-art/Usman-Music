# USMAN MUSIC — Listen Together WebSocket Backend

Lightweight, zero-dependency Node.js (ESM) WebSocket synchronization backend for USMAN MUSIC Listen Together rooms.

## Features
- Authoritative room state (room codes, participant presence, shared queue, playback timeline)
- NTP-style 4-timestamp clock offset synchronization (`time_sync_ping` / `time_sync_pong`)
- Scheduled future playback timestamps (`scheduledStartServerTimeMs`) and 4s periodic `sync_pulse`
- Control modes: `HOST_ONLY` vs `EVERYONE` enforced server-side
- Cryptographically random 6-character room codes + per-participant opaque `sessionToken` for secure reconnection
- Automatic host transfer on host leave or disconnect grace expiry
- Command idempotency (`commandId`), simultaneous `NEXT`/`PREV` deduplication, per-IP and per-socket rate limiting
- Health check (`GET /health`) and room status check (`GET /api/rooms/:code/status`)

## Local Development
```bash
cd listen-together-server
npm start
# WebSocket endpoint: ws://127.0.0.1:8080/ws
# Health endpoint:    http://127.0.0.1:8080/health
```

Run automated tests:
```bash
cd listen-together-server
npm test
```

## Production Deployment (WSS over TLS)

### Option 1 — Render Blueprint (Recommended HTTPS + WSS)
1. Push this repository to GitHub (the root `render.yaml` Blueprint is auto-detected by Render).
2. In Render Dashboard, choose **New -> Blueprint**, connect your GitHub repository, and click **Apply**.
3. Render builds and starts `listen-together-server` on Node 22 with automatic TLS and `/health` probing.
4. Copy the live service URL shown in Render (e.g., `https://<your-service>.onrender.com`). Your WebSocket endpoint is `wss://<your-service>.onrender.com/ws`.
5. Verify your deployed server end-to-end (health check + 2-client WebSocket room sync):
   ```bash
   cd listen-together-server
   node verify-deployment.mjs https://<your-service>.onrender.com
   ```
   > **Free Tier Note:** Render Free web services spin down after ~15 minutes of inactivity (30–60s cold start on first request). For always-on rooms without cold starts, upgrade the service plan from `free` to `starter` in Render.

### Option 2 — Railway
1. Connect your repository and set the service root directory to `listen-together-server`.
2. Start command: `node server.mjs`.
3. Enable public networking to obtain `wss://<your-app>.up.railway.app/ws`.

### Option 3 — Fly.io
```bash
cd listen-together-server
fly launch --copy-config
fly deploy
# Endpoint: wss://usman-music-listen-together.fly.dev/ws
```

### Option 4 — Google Cloud Run
```bash
cd listen-together-server
gcloud run deploy usman-music-listen-together \
  --source . \
  --region asia-south1 \
  --allow-unauthenticated \
  --session-affinity \
  --timeout=3600 \
  --max-instances=1
# Endpoint: wss://<service-url>/ws
```
> Note: Single-instance deployment (`--max-instances=1` / single node) keeps in-memory rooms consistent across all connected phones. For horizontal multi-instance scaling, back `RoomManager` with Redis Pub/Sub.

## Configuring Both Android Phones
- **In-App (No Rebuild Required):** Open **USMAN MUSIC -> Listen Together -> Server Settings (gear icon)** on both Phone A and Phone B, enter your deployed `wss://<your-service>.onrender.com/ws`, tap **Test Connection**, and tap **Save**.
- **Baked Into APK Builds:** Set `LISTEN_TOGETHER_WS_URL=wss://<your-service>.onrender.com/ws` in your GitHub Actions repository secrets (or AI Studio Secrets panel) so newly built APKs use the endpoint automatically.
