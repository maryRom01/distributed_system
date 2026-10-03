# Replicated Log

A primitive replicated log implementation in Java: one **Master** node and two **Secondary** (Follower) nodes, communicating over HTTP, with blocking replication (Master waits for ACKs from all Secondaries before completing a write).

## Architecture

- **Master** — exposes `POST /append` (append a message) and `GET /list` (return all messages). On every `POST`, Master replicates the message to all Secondaries over HTTP and blocks until all of them acknowledge receipt.
- **Secondary** (Follower) — exposes `GET /list` (return all replicated messages) and an internal `POST /replicate` endpoint used by Master to push new entries.

## Prerequisites

- **Java** — the project targets Java 11 bytecode (see `pom.xml`). Tested with JDK 26 installed locally; any JDK 11+ will work.
- **Maven** — either:
    - IntelliJ IDEA's bundled Maven (no separate install needed), **or**
    - A standalone Maven installation on your PATH (if you want to run `mvn` from a terminal)
- **Docker Desktop** — required to run the system in containers.

## Build

The project uses the Maven Shade plugin to produce two separate runnable "fat jars" (dependencies bundled in) from a single `package` run:
- `target/master-app.jar` → entry point `MasterApp`
- `target/follower-app.jar` → entry point `FollowerApp`

### Option A — IntelliJ (Maven tool window)

1. Open the **Maven** tab on the right edge of IntelliJ.
2. Expand **Lifecycle**.
3. Double-click **clean**, then double-click **package**.
4. Confirm `target/master-app.jar` and `target/follower-app.jar` were created.

### Option B — Command line (requires Maven on PATH)

```bash
mvn clean package
```

Same result: both jars appear under `target/`.

## Run locally (without Docker)

### Start the Secondaries first

Each Secondary reads its port from the first command-line argument (defaults to `8001` if omitted):

```bash
java -jar target/follower-app.jar 8001
java -jar target/follower-app.jar 8002
```

(Run these in two separate terminals, since each blocks the terminal while running.)

### Start the Master

```bash
java -jar target/master-app.jar
```

Master listens on port `8000` and is pre-configured to reach its Secondaries at `http://localhost:8001` and `http://localhost:8002` for local (non-Docker) runs.

> **Note:** the follower URLs are currently hardcoded in `MasterApp.java`. For local runs they point to `localhost`; for Docker runs (below) they must point to container names instead — see the Docker section.

## Run with Docker

Each node runs in its own container. Containers communicate using a custom Docker network, which lets them resolve each other by **container name** instead of `localhost`.

### 1. Build the jars first

Make sure `target/master-app.jar` and `target/follower-app.jar` exist and are up to date (see **Build** above) — the Docker images copy these jars in directly.

### 2. Build the Docker images

```bash
docker build -t master-app .
docker build -f Dockerfile.follower -t follower-app .
```

### 3. Create a custom network

```bash
docker network create replicated-log-net
```

### 4. Run the Secondaries

```bash
docker run -d --name follower1 --network replicated-log-net -p 8001:8001 follower-app 8001
docker run -d --name follower2 --network replicated-log-net -p 8002:8002 follower-app 8002
```

### 5. Run the Master

```bash
docker run -d --name master --network replicated-log-net -p 8000:8000 master-app
```

> Master's follower URLs must be set to the container names (`http://follower1:8001`, `http://follower2:8002`) in `MasterApp.java` for this to work — `localhost` inside a container refers to that container itself, not the others.

### Useful Docker commands

```bash
docker ps                      # list running containers
docker logs master              # view Master's console output / stack traces
docker logs follower1
docker stop master follower1 follower2
docker rm master follower1 follower2
```

To reset everything (stop and remove all containers):

```bash
docker stop $(docker ps -aq)
docker rm $(docker ps -aq)
```

## Testing / Verifying replication

With all three nodes running (locally or in Docker), append a message via Master:

```bash
curl -X POST http://localhost:8000/append -d "hello world"
```

Expected response: `OK`

Then confirm the message replicated to all nodes:

```bash
curl http://localhost:8000/list
curl http://localhost:8001/list
curl http://localhost:8002/list
```

All three should return the same JSON array, e.g.:

```json
[{"index":0,"timestamp":"2026-10-03T21:22:41.361542Z","message":"hello world"}]
```

this stops everything Docker-related:
```aiignore
docker stop $(docker ps -aq) && docker rm $(docker ps -aq)
```