# Velvet Rummy Den — Multiplayer Backend & Web Client

Zero-dependency Node.js 22 HTTP + RFC-6455 WebSocket multiplayer server and full playable web client.

## Run Locally

```bash
node backend/server.mjs
```

The server listens on `http://0.0.0.0:8787` (override via `PORT`) and serves:
- Web client at `/`
- REST matchmaking & actions at `/api/tables/*`
- WebSocket table sync at `/ws`
- Health endpoint at `/api/health`

## Run Automated Two-Session Tests

```bash
node --test backend/tests/rummy.test.mjs
```

## Connect Android App to a Public Deployment

Deploy the `backend/` folder to any Node.js 20+ host (Cloud Run, Fly.io, Railway, etc.), then set `MULTIPLAYER_BASE_URL` in the AI Studio **Secrets** panel (no rebuild of UI or server-address dialog needed).
