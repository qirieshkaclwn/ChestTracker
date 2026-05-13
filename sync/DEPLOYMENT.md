# Deployment Guide: ChestTracker Sync API

This document provides instructions for deploying the ChestTracker Synchronization API.

## 1. Requirements
- **Python 3.9+**
- **PostgreSQL** (with `JSONB` support)
- **FastAPI** & **SQLAlchemy**
- **Alembic** (for migrations)
- **Uvicorn** (for running the server)

## 2. Environment Setup

### Option A: Using venv (Standard Python)
1. **Create a virtual environment**:
   ```bash
   python -m venv venv
   source venv/bin/activate  # Windows: venv\Scripts\activate
   ```
2. **Install dependencies**:
   ```bash
   pip install fastapi uvicorn sqlalchemy psycopg2-binary alembic pydantic python-dotenv
   ```

### Option B: Using Conda
1. **Create the environment**:
   ```bash
   conda env create -f environment.yml
   ```
2. **Activate it**:
   ```bash
   conda activate ctsync
   ```

3. Environment Variables:
   Create a `.env` file or set the following environment variables:
   - `DATABASE_URL`: `postgresql://user:password@localhost:5432/chesttracker`
   - `API_TOKEN`: Your secret token for client authentication.

## 3. Database Migrations

You can apply migrations using either **Alembic** (recommended) or **Raw SQL**.

### Option A: Using Alembic (Python)
Alembic tracks migration versions in the `alembic_version` table.
```bash
alembic upgrade head
```

### Option B: Using Raw SQL
If you prefer not to use Python tools for migrations, apply the SQL files in `sync/migrations/`:
1. **Apply migration**:
   ```bash
   psql -d chesttracker -f sync/migrations/0001_initial_migration.sql
   ```
2. **Rollback (if needed)**:
   ```bash
   psql -d chesttracker -f sync/migrations/0001_initial_migration_down.sql
   ```

## 4. Running the Server

Start the API using `uvicorn`:
```bash
uvicorn main:app --host 0.0.0.0 --port 8000
```

The API will be available at `http://localhost:8000`. You can access the interactive documentation at `http://localhost:8000/docs`.

## 5. Deployment with Docker (Daemonization)

This will run the API server in the background. **Note: You must have a PostgreSQL database already running on your host machine.**

1. **Install Docker and Docker Compose**.
2. **Configure Environment**:
   Copy `.env.example` to `.env` and update the `DATABASE_URL`. 
   **Crucial:** Use `host.docker.internal` instead of `localhost` to let Docker talk to your host's database.
   ```bash
   cp .env.example .env
   ```
3. **Start the API**:
   ```bash
   docker compose up -d --build
   ```
4. **Apply Migrations**:
   ```bash
   docker compose exec api alembic upgrade head
   ```

To view logs: `docker compose logs -f`.
To stop: `docker compose down`.

## 6. Security Note
- Ensure `API_TOKEN` is kept secret and shared only with trusted mod users.
- In production, use HTTPS (e.g., via Nginx reverse proxy).
- It is recommended to use a dedicated database user with restricted permissions.
