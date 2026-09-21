# Distributed Food Ordering System — Complete Architecture Document

## 1. System Overview

The Distributed Food Ordering System is a multi-process distributed systems project implemented in native Java and Java RMI. It is designed to model and demonstrate fundamental distributed systems algorithms without reliance on external enterprise middleware or database servers:

- **Remote Procedure Calls**: Pure Java RMI across clients, Gateway, and Order Nodes.
- **Concurrency Control**: Non-blocking concurrent structures (`ConcurrentHashMap`), synchronized state transitions, and thread pools (`ExecutorService`).
- **Physical Clock Synchronization**: Cristian's algorithm with Round-Trip Time (RTT) adjustment.
- **Logical Causal Ordering**: Lamport Logical Clocks with atomic lock-free CAS (`AtomicLong`).
- **Application-State Replication**: Primary-backup replication with explicit serialized ACKs and idempotent application.
- **Crash Durability**: Append-only local filesystem event logs (`events.log`) with hardware disk sync (`sync()`).
- **Failure Detection**: Scheduled heartbeat monitoring from Backup nodes to Primary.
- **Distributed Leader Election**: The Bully Algorithm with dynamic role transition.
- **Dynamic Routing Failover**: Gateway updates routing tables upon leader election and provides resilient failover.
- **Catch-up Recovery**: Replaying local logs and synchronizing missed state from the active primary before rejoining.

---

## 2. Complete Component Topology

```text
                               DISTRIBUTED FOOD ORDERING SYSTEM
 
                +-----------------------+             +-----------------------+
                | CustomerClient (JVM)  |             |   RiderClient (JVM)   |
                +-----------+-----------+             +-----------+-----------+
                            |                                     |
                            +------------- Java RMI --------------+
                                          |
                                          v
                              +-----------------------+
                              |  GatewayServer (JVM)  |
                              | Port: 1099            |
                              | Service: GatewayService|
                              +-----------+-----------+
                                          |  (routes to current PRIMARY)
                                          v
            +-----------------------------+-----------------------------+
            |                             |                             |
            v                             v                             v
    +---------------+             +---------------+             +---------------+
    | OrderNode1    |             | OrderNode2    |             | OrderNode3    |
    | Port: 1101    |             | Port: 1102    |             | Port: 1103    |
    | Initial:      |             | Initial:      |             | Initial:      |
    | PRIMARY       |             | BACKUP        |             | BACKUP        |
    +---------------+             +---------------+             +---------------+
    | Store + Log   |             | Store + Log   |             | Store + Log   |
    | HeartbeatMon  |<==Election=>| HeartbeatMon  |<==Election=>| HeartbeatMon  |
    | BullyElection |             | BullyElection |             | BullyElection |
    +---------------+             +---------------+             +---------------+
            |                             |                             |
       data/node1/                   data/node2/                   data/node3/
        events.log                    events.log                    events.log
```

---

## 3. Package & Responsibility Architecture

The codebase strictly adheres to the established package structure under `src/main/java/com/foodordering/`:

| Package | Key Classes | Core Responsibilities |
| :--- | :--- | :--- |
| **`model/`** | `Order`, `OrderSnapshot`, `ReplicationEvent`, `ReplicationAck`, `DistributedEvent`, `RiderAcceptance`, `Restaurant`, `NodeInfo`, `NodeRole`, `OrderStatus` | Immutable serializable DTOs and thread-safe entity state objects. |
| **`api/gateway/`** | `GatewayRemote` | Client-facing public RMI contract for ordering, browsing, status queries, and failover notifications. |
| **`api/node/`** | `OrderNodeRemote` | Inter-node RMI contract for writes, replication, catch-up slicing, Bully election, and heartbeats. |
| **`gateway/`** | `GatewayServer` | Central request router; routes writes to PRIMARY, handles failovers, and acts as Cristian time server. |
| **`node/`** | `OrderNodeServer`, `OrderNode1`, `OrderNode2`, `OrderNode3` | Autonomous JVM processes orchestrating state, replication, heartbeats, and leader election. |
| **`client/customer/`** | `CustomerClient` | Customer interface for menu inspection and order placement with logical Lamport timestamps. |
| **`client/rider/`** | `RiderClient` | Delivery rider interface; synchronizes physical clocks and submits acceptance bids. |
| **`clock/`** | `PhysicalClockSynchronizer`, `LamportClock` | Physical clock synchronization (Cristian's) and logical clock tracking (Lamport). |
| **`storage/`** | `OrderStore`, `EventLog`, `ReplicationManager` | In-memory thread-safe state table, append-only disk logging with `sync()`, and peer replication. |
| **`monitoring/`** | `HeartbeatMonitor` | Scheduled heartbeat prober tracking primary availability and detecting node failures. |
| **`election/`** | `BullyElectionManager` | Distributed Bully Leader Election algorithm implementation. |
| **`config/`** | `ClusterConfig` | Hostnames, port allocations (1099, 1101, 1102, 1103), and RMI URL generators. |
| **`util/`** | `RmiUtils` | RMI registry locator and lifecycle utilities. |
| **`demo/`** | `FullSystemDemo`, `SystemVerificationTest`, `ReplicationTest`, `LamportClockTest` | End-to-end multi-process verification suites and unit tests. |

---

## 4. Concurrency & State Management Audit

1. **`OrderStore`**:
   - Maintains active orders in a `ConcurrentHashMap<Integer, Order>`.
   - Idempotency is enforced using `appliedEvents = ConcurrentHashMap.newKeySet()`. If an event with an existing `eventId` arrives via replication or log replay, it is discarded safely.
2. **`Order` Thread-Safety**:
   - `Order` holds mutable state (`status`, `assignedRider`, `riderAcceptances`, `eventLog`).
   - Every state-mutating method (`addAcceptance`, `finalizeAssignment`, `arriveAtRestaurant`, `deliverOrder`) is explicitly `synchronized`.
   - `toSnapshot()` creates an immutable, defensive-copy DTO (`OrderSnapshot`) containing deep copies of collections so that client threads never encounter `ConcurrentModificationException`.
3. **`ExecutorService` Concurrency**:
   - Each `OrderNodeServer` maintains a bounded `ThreadPoolExecutor` (`Executors.newFixedThreadPool(4)`).
   - Order creation spawns asynchronous preparation tasks without unconstrained thread generation.
4. **Order ID Generation**:
   - Order IDs are generated deterministically per node using `(nodeNum * 1000) + orderSequence`.
   - This prevents ID collisions across nodes before, during, and after leader elections.

---

## 5. Distributed Systems Experiments & Protocols

### A. Physical Clock Synchronization (Cristian's Algorithm)
- **Problem**: Determining which rider clicked "ACCEPT" earliest in physical time.
- **Algorithm**:
  $$\text{RTT} = t_1 - t_0$$
  $$\text{Offset} = T_{\text{server}} - \left(t_0 + \frac{\text{RTT}}{2}\right)$$
  $$\text{Estimated Time} = T_{\text{local}} + \text{Offset}$$
- **Limitation**: Assumes symmetric network latency ($\text{RTT}/2$). In asymmetric networks, physical accuracy is bounded within $\pm \frac{\text{RTT}}{2}$.

### B. Logical Clock Ordering (Lamport Clocks)
- **Problem**: Determining causal precedence ($a \to b$) across independent distributed processes.
- **Rules**:
  - Local event / Send: $L = L + 1$
  - Receive message with timestamp $L_{\text{msg}}$: $L = \max(L, L_{\text{msg}}) + 1$
- **Deterministic Total Ordering**: For concurrent events where neither caused the other ($L(a) = L(b)$), ties are broken deterministically using:
  $$(L, \text{sourceId}, \text{eventId})$$

### C. Application-State Replication vs. Database Replication
- **Why This is NOT Database Replication**:
  There is no database daemon (PostgreSQL/MySQL/MongoDB) or storage engine WAL streaming. Instead, replication is implemented at the application layer:
  - High-level domain Java objects (`ReplicationEvent`) are transmitted via Java RMI.
  - Events are appended to local application text logs (`events.log`) with `sync()`.
  - Backups apply events directly to in-memory `ConcurrentHashMap` collections.

### D. Bully Leader Election & Failover Sequence
When Primary (Node 1) crashes:
```text
Node 2 (Backup)                  Node 3 (Backup)                 Gateway Server
      |                                |                               |
      | 1. Heartbeat timeout           |                               |
      | 2. ELECTION challenge          |                               |
      +------------------------------->|                               |
      |                                | 3. ID 3 > 2 -> Send OK        |
      |<-------------------------------+                               |
      | 4. Yield & wait                | 5. Start own election         |
      |                                | 6. Highest live ID -> WIN!    |
      |                                | 7. Role = PRIMARY             |
      | 8. COORDINATOR announcement    |                               |
      |<-------------------------------+                               |
      |    (Node 2 role = BACKUP)      | 9. notifyLeaderChanged("3")   |
      |                                +------------------------------>|
      |                                |                               | (Routes -> N3)
```

### E. Node Recovery (Log Replay + State Catch-Up)
When Node 1 restarts:
1. Replays its local `events.log` from disk into `OrderStore`.
2. Contacts the Gateway to identify the current PRIMARY (Node 3).
3. Invokes `Node3.getMissingEvents(localVersion)` to pull events that occurred during its downtime.
4. Applies missing events to `OrderStore`, appends them to disk, and starts its `HeartbeatMonitor`.
5. Rejoins the cluster strictly as `BACKUP`.

---

## 6. Honest Limitations & Architectural Boundaries

1. **Gateway Single Point of Failure (SPOF)**:
   While Order Nodes have automatic failover via Bully election, the Gateway process is currently a single instance. If Gateway crashes, clients cannot connect. (In production, this would require a VIP, ZooKeeper/etcd, or DNS failover).
2. **Crash-Stop vs. Byzantine Failures**:
   The system assumes crash-stop failures. It does not handle Byzantine (arbitrary/malicious) failures or silent network corruption.
3. **Network Partitions (Split-Brain Risk)**:
   The Bully algorithm does not use a quorum/majority consensus (like Paxos or Raft). In the presence of a network partition separating nodes, two nodes could both believe they are primary.
4. **Log Compaction**:
   `events.log` is strictly append-only. Long-running systems would eventually require checkpointing and snapshot compaction.
