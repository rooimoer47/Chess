# Chess — v1.0.1

A multiplayer chess app with bot opponents, game history, and replay.
Live at **[chessnuts.eu](https://chessnuts.eu)**.

## Features

- Play against another human (same browser) or a bot
- Bot difficulty levels: Random · Alan · Barbara · Claude
- Clock options: no clock, 1 min, 10 min
- Board and piece themes
- Choose your color or play random
- Full game history with move-by-move replay
- User accounts with persistent preferences

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot, Spring Data JDBC |
| Frontend | TypeScript, React, Vite |
| Database | PostgreSQL 17 |
| Container | Docker, Docker Compose |
| Reverse proxy | nginx + Let's Encrypt |

---

## Running locally

There are two ways to run the app locally: **Docker** (easiest, one command) or **dev mode** (hot reload, for active development).

### Option A — Docker (recommended)

Requires: Docker

```bash
git clone https://github.com/rooimoer47/Chess.git
cd Chess
```

Create a `.env` file in the project root:

```env
DB_URL=jdbc:postgresql://db:5432/chess
DB_USER=chess
DB_PASSWORD=changeme
CHESS_JWT_SECRET=any-random-string-here
CHESS_COOKIE_SECURE=false
```

Start the stack:

```bash
docker compose -f docker-compose.local.yml up --build
```

Open **http://localhost:8080** in your browser.

A pgAdmin instance is also available at **http://localhost:5050**
(login: `admin@chessnuts.eu` / `admin`, then connect to host `db`).

---

### Option B — Dev mode (hot reload)

Requires: Java 21, Maven, Node 18+, a running PostgreSQL instance

#### 1. Start the database

```bash
docker compose -f docker-compose.local.yml up db -d
```

#### 2. Create a `.env` file

```env
DB_URL=jdbc:postgresql://localhost:5432/chess
DB_USER=chess
DB_PASSWORD=changeme
CHESS_JWT_SECRET=any-random-string-here
CHESS_COOKIE_SECURE=false
```

#### 3. Start the backend

```bash
./run-backend.sh
```

The API is now running at **http://localhost:8080**.

#### 4. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173** in your browser. The Vite dev server proxies `/api` and `/ws` to the backend, so hot reload works without rebuilding the jar.