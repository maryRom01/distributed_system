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

list of commands
```aiignore
mvn clean package
docker build -t master-app .
docker build -f Dockerfile.follower -t follower-app .
docker stop $(docker ps -aq) && docker rm $(docker ps -aq)
docker network create replicated-log-net
docker run -d --name follower1 --network replicated-log-net -p 8001:8001 follower-app 8001
docker run -d --name follower2 --network replicated-log-net -p 8002:8002 follower-app 8002
docker run -d --name master --network replicated-log-net -p 8000:8000 master-app
docker ps

curl -X POST http://localhost:8000/append -d "docker verification after refactor"
curl http://localhost:8000/list
curl http://localhost:8001/list
curl http://localhost:8002/list

docker logs master
docker logs follower1
docker logs follower2

docker stop $(docker ps -aq) && docker rm $(docker ps -aq)
```

################################################

Replicated Log (Iteration 2)

A primitive replicated log in Java: one Master and two Secondaries (followers) communicating over HTTP. Master supports a tunable write concern (w) and guarantees total ordering of messages on all nodes.

Architecture

Master
POST /append?w=N - appends the message (request body) to the in-memory log and replicates it to all Secondaries.
GET /list - returns all messages as JSON.

Secondary (Follower)
GET /list - returns all replicated messages as JSON.
POST /replicate - internal endpoint used only by Master.

Transport - interface between Master and Secondaries.
HttpTransport - real HTTP (used in production / Docker).
MockedTransport - in-memory fake used by the test harness (delay, message reordering).

Write concern (w)

w is passed as a query parameter and says how many nodes must have the message before Master responds to the client:

w	Master responds after
1	Master only
2	Master + 1 Secondary
3	Master + 2 Secondaries
If w is omitted, Master waits for all nodes (w = number of secondaries + 1).
Invalid w (outside 1..secondaries+1, or not a number) returns 400 Bad Request.
Master always sends the message to all Secondaries in parallel; w only controls how many ACKs it waits for. The rest of the replication continues in the background.
Master handles requests concurrently, so a slow w=3 request does not block a w=1 request.

Example:

bash
curl -i -X POST "http://localhost:8000/append?w=2" -d "hello"
Total ordering
Master assigns a unique, sequential index to every message (index assignment is synchronized, so concurrent requests never get the same index).
A Secondary applies messages strictly in index order:
index == next expected -> appended to the log, then any buffered messages that now fit are appended too;
index > next expected (arrived early) -> kept in a buffer until the missing messages arrive;
index < next expected (duplicate) -> ignored.
So even if messages arrive out of order, every Secondary ends up with the same order as Master.

Prerequisites

Java - the project targets Java 11 bytecode (see pom.xml); developed with JDK 26. Any JDK 11+ works.
Maven - IntelliJ's bundled Maven, or a standalone mvn on PATH.
Docker Desktop - only needed to run the system in containers.

Build

Produces target/master-app.jar and target/follower-app.jar.

IntelliJ: Maven tool window -> Lifecycle -> clean, then package. Command line:

bash
mvn clean package

Run tests

The test harness (TestNG + MockedTransport) runs entirely in one process. Docker is not needed.

bash
mvn test

(or run ReplicationTest from IntelliJ). Some tests use artificial delays of several seconds, so the suite takes a while.

What is covered (src/test/java/ReplicationTest.java):

Test	Verifies
testDelayOnFollowerA / testDelayOnFollowerB	Master blocks until the delayed Secondary ACKs (w = all)
testNoDelayReplication	No delay -> near-instant replication
testFullReplicationAcrossAllNodes	Master and both Secondaries have the same messages in the same order
testParallelReplicationTiming	Replication is parallel: delays 1s/3s -> ~3s, not 4s
testReorderedMessagesAreOrderedOnFollower	reorderMessages delivers messages in reverse order; the Secondary still ends with the correct order
acceptanceTestForIteration2	Acceptance scenario: S2 delayed; Msg1 w=1, Msg2 w=2 return immediately; Msg3 w=3 waits for S2; Msg4 w=1 is not blocked; S2 is temporarily behind, then catches up (eventual consistency)

MockedTransport API:

registerFollower(destination, follower)
setDelay(destination, ms) / removeDelay(destination)
reorderMessages(destination, count) - holds count messages, then delivers them in reverse order; stopReordering(destination)

Delay and reordering are emulated by the test harness, not inside Docker.

Run locally (without Docker)

In MasterApp.java use the localhost follower URLs (comment out the Docker ones):

java
master.addFollower("http://localhost:8001");
master.addFollower("http://localhost:8002");

Then, in three terminals (Secondaries first):

bash
java -jar target/follower-app.jar 8001
java -jar target/follower-app.jar 8002
java -jar target/master-app.jar
Run with Docker

In MasterApp.java use the container-name URLs (comment out the localhost ones), then rebuild the jars:

java
master.addFollower("http://follower1:8001");
master.addFollower("http://follower2:8002");
bash
# 1. Build images
docker build -t master-app .
docker build -f Dockerfile.follower -t follower-app .

# 2. Custom network (containers reach each other by name)
docker network create replicated-log-net

# 3. Secondaries, then Master
docker run -d --name follower1 --network replicated-log-net -p 8001:8001 follower-app 8001
docker run -d --name follower2 --network replicated-log-net -p 8002:8002 follower-app 8002
docker run -d --name master --network replicated-log-net -p 8000:8000 master-app

Useful commands:

bash
docker ps
docker logs master
docker stop $(docker ps -aq) && docker rm $(docker ps -aq)   # stop and remove everything
Manual check
bash
curl -i -X POST "http://localhost:8000/append?w=1" -d "a"
curl -i -X POST "http://localhost:8000/append?w=3" -d "b"
curl -i -X POST "http://localhost:8000/append?w=5" -d "c"     # 400
curl http://localhost:8000/list
curl http://localhost:8001/list
curl http://localhost:8002/list
Logging

Uses java.util.logging (INFO/SEVERE). In Docker, view it with docker logs <container>.

Known limitations
Perfect link assumed: if a send to a Secondary throws, the ACK is never counted (the request could hang for that w).
Follower URLs are hardcoded in MasterApp.java (switch between local and Docker by hand).
No real network delay in Docker; delay/reordering are emulated by MockedTransport in tests.