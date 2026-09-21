# Distributed Food Ordering System

A modular, multi-process distributed systems project implemented in native Java and Java RMI.

---

## 1. Directory Structure

```text
distributed-food-ordering/
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── foodordering/
│                   ├── model/
│                   │   ├── DistributedEvent.java
│                   │   ├── EventType.java
│                   │   ├── NodeInfo.java
│                   │   ├── NodeRole.java
│                   │   ├── Order.java
│                   │   ├── OrderSnapshot.java
│                   │   ├── ReplicationAck.java
│                   │   ├── ReplicationEvent.java
│                   │   ├── Restaurant.java
│                   │   └── RiderAcceptance.java
│                   ├── api/
│                   │   ├── gateway/
│                   │   │   └── GatewayRemote.java
│                   │   └── node/
│                   │       └── OrderNodeRemote.java
│                   ├── gateway/
│                   │   └── GatewayServer.java
│                   ├── node/
│                   │   ├── OrderNodeServer.java
│                   │   ├── OrderNode1.java
│                   │   ├── OrderNode2.java
│                   │   └── OrderNode3.java
│                   ├── client/
│                   │   ├── customer/
│                   │   │   └── CustomerClient.java
│                   │   └── rider/
│                   │       └── RiderClient.java
│                   ├── clock/
│                   │   ├── LamportClock.java
│                   │   └── PhysicalClockSynchronizer.java
│                   ├── storage/
│                   │   ├── EventLog.java
│                   │   ├── OrderStore.java
│                   │   └── ReplicationManager.java
│                   ├── monitoring/
│                   │   ├── HeartbeatMonitor.java
│                   │   └── HeartbeatManager.java
│                   ├── election/
│                   │   ├── BullyElectionManager.java
│                   │   └── BullyElection.java
│                   ├── config/
│                   │   └── ClusterConfig.java
│                   ├── util/
│                   │   └── RmiUtils.java
│                   └── demo/
│                       ├── FullSystemDemo.java
│                       ├── SystemVerificationTest.java
│                       ├── LamportClockTest.java
│                       ├── ReplicationTest.java
│                       ├── Step1Demo.java
│                       ├── Step2Demo.java
│                       └── Step3Demo.java
│
├── data/
│   ├── node1/events.log
│   ├── node2/events.log
│   └── node3/events.log
│
├── archive/
│   └── legacy/
│
├── docs/
│   └── architecture/
│       └── ARCHITECTURE.md
│
├── Dockerfile
├── docker-compose.yml
└── README.md
```

---

## 2. Compilation

Compile all active source files into the `bin/` directory:

```bash
mkdir -p bin
javac -d bin $(find src/main/java -name "*.java")
```

---

## 3. Running the Complete 22-Step Coherent Demo

To run the complete 22-step integration pass (spawning Gateway, Nodes 1–3, Customer, Riders R1–R3, demonstrating order placement, multithreading, physical clock sync, Lamport logical clocks, replication, disk persistence, primary crash, heartbeat failure detection, Bully leader election, dynamic Gateway failover, querying pre-failover state, creating post-failover state, and recovering the failed node as BACKUP):

```bash
java -cp bin com.foodordering.demo.FullSystemDemo
```

---

## 4. Running the 15-Requirement Automated Test Suite

To run the automated test suite verifying all 15 core architectural requirements:

```bash
java -cp bin com.foodordering.demo.SystemVerificationTest
```

---

## 5. Running the System Manually Across Terminals

Open four separate terminals and run the servers:

### Terminal 1 — Order Node 1 (Initial PRIMARY)
```bash
java -cp bin com.foodordering.node.OrderNode1
```
*Listens on port 1101, binds `OrderNode1` as PRIMARY, persists to `data/node1/events.log`.*

### Terminal 2 — Order Node 2 (BACKUP)
```bash
java -cp bin com.foodordering.node.OrderNode2
```
*Listens on port 1102, binds `OrderNode2` as BACKUP, runs HeartbeatMonitor targeting Node 1, persists to `data/node2/events.log`.*

### Terminal 3 — Order Node 3 (BACKUP)
```bash
java -cp bin com.foodordering.node.OrderNode3
```
*Listens on port 1103, binds `OrderNode3` as BACKUP, runs HeartbeatMonitor targeting Node 1, persists to `data/node3/events.log`.*

### Terminal 4 — Gateway Server
```bash
java -cp bin com.foodordering.gateway.GatewayServer
```
*Listens on port 1099, binds `GatewayService`, routes requests to the current PRIMARY node, and handles dynamic failover notifications.*

### Terminal 5 — Customer Client (Interactive)
```bash
java -cp bin com.foodordering.client.customer.CustomerClient
```

### Terminal 6 — Rider Client (Interactive)
```bash
java -cp bin com.foodordering.client.rider.RiderClient R1
```

---

## 6. Unit & Component Test Commands

### Storage & Replication Unit Tests
Verifies local append-only persistence, log replay into `OrderStore`, duplicate idempotency, and catch-up log slicing:
```bash
java -cp bin com.foodordering.demo.ReplicationTest
```

### Lamport Clock Unit Tests
Verifies Lamport logical clock rules and concurrent event tie-breaking:
```bash
java -cp bin com.foodordering.demo.LamportClockTest
```

---

## 7. Architectural Scope & Implementation Reality

### Genuinely Implemented in Code:
- **Java RMI Remote Procedure Calls**: Customer/Rider -> Gateway -> OrderNode; inter-node replication and Bully election via RMI.
- **Concurrency**: `ConcurrentHashMap`, thread-safe `Order` state mutations, and worker pool `ExecutorService`.
- **Physical Clock Synchronization**: Cristian's algorithm with RTT adjustment and offset tracking.
- **Lamport Logical Clocks**: Atomic CAS scalar logical clocks on every node/client, tracking distributed causal event history.
- **Application-State Replication**: Primary broadcasts serialized `ReplicationEvent` objects to Backups and collects explicit `ReplicationAck` responses.
- **Crash Durability**: Local append-only `events.log` files with `FileOutputStream.getFD().sync()` forcing hardware disk writes.
- **Heartbeat Failure Detection**: Scheduled periodic health probes from Backups to Primary; triggers election upon timeout.
- **Distributed Bully Leader Election**: Higher-ID nodes challenge and preempt lower-ID nodes; highest live ID assumes PRIMARY role and announces COORDINATOR.
- **Gateway Dynamic Failover**: Gateway updates routing table upon leader announcement and dynamically falls back to active leaders.
- **Node Recovery**: Stale or restarted nodes replay local disk logs and pull missing events from the active primary before rejoining as BACKUP.

### Explicitly Excluded (No False Claims):
- **NO Database Engine**: No PostgreSQL, MySQL, MongoDB, SQLite, H2, JPA, Hibernate, or Spring Data. All state is maintained in-memory in Java collections backed by application text logs.
- **NO Microservices / Enterprise Frameworks**: No Spring Boot, Quarkus, or Micronaut.
- **NO HTTP / REST Gateways**: No NGINX, Express, or Spring Cloud Gateway. The Gateway is a pure Java RMI server.
- **NO Distributed Log / Message Queues**: No Apache Kafka, RabbitMQ, or ActiveMQ.
# Distributed-Food-Ordering-System
