# Distributed Food Ordering System — Architecture Documentation

---

## 1. What this project is

This project is a small, working **Distributed Food Ordering System** written in pure Java using **Java Remote Method Invocation (RMI)**. It models how real food ordering platforms manage concurrent customer orders, delivery rider bids, server failures, and state replication without depending on heavy third-party enterprise frameworks or database engines.

The system is distributed because its tasks are split across multiple independent operating-system processes:
- A **Customer** can view restaurant menus, place orders, and check live order progress through a logical clock.
- **Delivery Riders** can synchronize their local physical clocks with the server, compete to accept available delivery jobs, report arrivals at restaurants, and mark orders as delivered.
- A **Gateway Server** acts as the single door for clients, routing their requests to whichever backend order-processing server is currently in charge.
- Three **Order Nodes** store the orders, replicate updates to each other, monitor each other for crashes, elect a new leader if the main node fails, and recover their state from disk when restarted.

---

## 2. The big picture

Here is how all the pieces connect together:

```text
               +----------------------+       +----------------------+
               | CustomerClient (JVM) |       |  RiderClient (JVM)   |
               +----------+-----------+       +----------+-----------+
                          |                              |
                          +---------- Java RMI ----------+
                                         |
                                         v
                             +-----------------------+
                             |  GatewayServer (JVM)  |
                             |  Port 1099            |
                             +-----------+-----------+
                                         |  (forwards client writes)
                                         v
                  +----------------------------------------------+
                  |                                              |
                  v                                              v
          +---------------+       Replication            +---------------+
          |  OrderNode 1  | ---------------------------> | OrderNode 2/3 |
          |    PRIMARY    | <----------- ACKs ---------- |    BACKUP     |
          |  Port 1101    |                              |  Port 1102/3  |
          +---------------+                              +---------------+
          | Store  +  Log |    <-- Heartbeats & Bully -- | Store  +  Log |
          +---------------+                              +---------------+
                  |                                              |
             data/node1/                                    data/node2/3/
              events.log                                     events.log
```

- **Customer & Rider Clients**: Run on user machines. They only know about the Gateway Server.
- **Gateway Server**: Runs on port 1099. It receives client calls and forwards write operations to the current **PRIMARY** order node.
- **OrderNode 1 (Initial PRIMARY)**: Runs on port 1101. It processes orders, saves events to its local disk log, and copies them to the backup nodes.
- **OrderNode 2 & 3 (Initial BACKUPS)**: Run on ports 1102 and 1103. They apply copied events to stay synchronized with the primary, write them to their own disk logs, and send back acknowledgments (ACKs).

---

## 3. Every folder

| Folder | What is stored there | Why it exists | Code or Data? |
| :--- | :--- | :--- | :--- |
| `src/main/java/com/foodordering/model/` | Domain objects and serializable data transfer classes (`Order`, `OrderSnapshot`, `NodeInfo`, etc.) | Holds all data models passed over the network or kept in memory. | Code |
| `src/main/java/com/foodordering/api/gateway/` | RMI interface `GatewayRemote` | Defines what public actions clients can call on the Gateway. | Code |
| `src/main/java/com/foodordering/api/node/` | RMI interface `OrderNodeRemote` | Defines the internal methods that the Gateway and peer nodes use to talk to each order node. | Code |
| `src/main/java/com/foodordering/gateway/` | Implementation of `GatewayServer` | Contains the gateway routing process and Cristian time server. | Code |
| `src/main/java/com/foodordering/node/` | `OrderNodeServer` logic and launcher wrappers (`OrderNode1/2/3`) | Implements the core order-processing server and node entry points. | Code |
| `src/main/java/com/foodordering/client/customer/` | `CustomerClient` | Interactive and scripted command-line customer application. | Code |
| `src/main/java/com/foodordering/client/rider/` | `RiderClient` | Interactive and scripted delivery rider bidding application. | Code |
| `src/main/java/com/foodordering/clock/` | `PhysicalClockSynchronizer` and `LamportClock` | Houses Cristian's clock algorithm and Lamport logical clock logic. | Code |
| `src/main/java/com/foodordering/storage/` | `OrderStore`, `EventLog`, and `ReplicationManager` | Manages in-memory maps, append-only disk files, and node-to-node replication. | Code |
| `src/main/java/com/foodordering/monitoring/` | `HeartbeatMonitor` and `HeartbeatManager` | Runs background health checks to detect server crashes. | Code |
| `src/main/java/com/foodordering/election/` | `BullyElectionManager` and `BullyElection` | Coordinates the Bully leader election algorithm. | Code |
| `src/main/java/com/foodordering/config/` | `ClusterConfig` | Keeps static hostnames, ports, and service URL helpers. | Code |
| `src/main/java/com/foodordering/util/` | `RmiUtils` | Helper methods for creating and locating local RMI registries. | Code |
| `src/main/java/com/foodordering/demo/` | Integration demos and unit tests (`FullSystemDemo`, `SystemVerificationTest`, etc.) | Houses automated test suites to verify that the system works end-to-end. | Code |
| `data/node1/`, `data/node2/`, `data/node3/` | Local text files named `events.log` | Stores the durable append-only event log for each node. | **Data** |
| `archive/legacy/` | Old monolithic prototype files (`FoodOrderingServer`, `FoodOrderingClient`, etc.) | Safely holds superseded legacy code so it does not pollute the active build. | Archived |
| `docs/architecture/` | Architectural documentation (`ARCHITECTURE.md`) | Explains how the entire system works. | Documentation |

---

## 4. Every important class

| File | What it does | Why it exists |
| :--- | :--- | :--- |
| `Order.java` | Server-side entity with order details, rider acceptance bids, and event history. | Holds the mutable order state on each node; all mutation methods are `synchronized` for thread-safety. |
| `OrderSnapshot.java` | Read-only, immutable copy of an order. | Allows the server to safely return order state to clients over RMI without thread conflicts. |
| `Restaurant.java` | Simple catalog containing restaurant names and food menus. | Provides menu browsing for customers. |
| `RiderAcceptance.java` | Record storing a rider's ID, estimated physical action timestamp, receipt timestamp, and Lamport timestamp. | Represents one rider's bid on an open delivery job. |
| `NodeInfo.java` | Stores node metadata: ID, IP, port, current role (`PRIMARY`/`BACKUP`), and status (`ALIVE`/`DEAD`). | Allows the gateway and nodes to track the cluster topology. |
| `NodeRole.java` | Enum with values `PRIMARY` and `BACKUP`. | Explicitly tags what role a node currently performs. |
| `ReplicationEvent.java` | Serializable record of a state change with version, timestamps, and order data. | Transmitted across the network to replicate state changes to other nodes and written to disk. |
| `ReplicationAck.java` | Confirmation message returned by a backup node to the primary. | Lets the primary know whether a backup successfully received and saved an event. |
| `DistributedEvent.java` | Entry in an order's causal history log ordered by Lamport clock. | Demonstrates causal ordering on individual order milestones. |
| `GatewayRemote.java` | Public Java RMI interface exposed by the gateway. | Contract for clients to connect, read menus, place orders, and sync physical time. |
| `OrderNodeRemote.java` | Internal Java RMI interface implemented by order nodes. | Contract for the gateway to forward orders, and for peers to replicate data and run elections. |
| `GatewayServer.java` | RMI server implementation for the gateway. | Single entry point that forwards client calls to the current primary and handles failover. |
| `OrderNodeServer.java` | Full backend server engine for an order node. | Coordinates in-memory state, disk logging, replication, heartbeats, and leader elections. |
| `OrderNode1.java` | Main method launcher that starts an `OrderNodeServer` on port 1101 as `PRIMARY`. | Clean entry point for starting Node 1. |
| `OrderNode2.java` | Main method launcher that starts an `OrderNodeServer` on port 1102 as `BACKUP`. | Clean entry point for starting Node 2. |
| `OrderNode3.java` | Main method launcher that starts an `OrderNodeServer` on port 1103 as `BACKUP`. | Clean entry point for starting Node 3. |
| `CustomerClient.java` | Command-line client program for customers. | Connects to the gateway to browse menus, submit orders, and check status. |
| `RiderClient.java` | Command-line client program for riders. | Connects to the gateway to sync physical time, bid on orders, and mark deliveries. |
| `PhysicalClockSynchronizer.java` | Implements Cristian's clock synchronization algorithm. | Lets riders calculate their clock offset against the server using Round-Trip Time (RTT). |
| `LamportClock.java` | Implements a scalar Lamport Logical Clock. | Advances logical timestamps on local events, message sends, and message receipts. |
| `OrderStore.java` | In-memory container wrapping a `ConcurrentHashMap<Integer, Order>`. | Fast in-memory state table; enforces idempotent application so duplicate events are ignored. |
| `EventLog.java` | Append-only text file manager for `events.log`. | Persists events to disk with `sync()` durability, and replays them on node startup. |
| `ReplicationManager.java` | Handles network broadcast of events from primary to backups. | Sends replication events over RMI, collects ACKs, and pulls missing events during catch-up. |
| `HeartbeatMonitor.java` / `HeartbeatManager.java` | Background scheduled task that checks if the primary is still alive. | Detects primary node crashes so an election can be started. |
| `BullyElectionManager.java` / `BullyElection.java` | Implements the distributed Bully leader election algorithm. | Ensures that when the primary dies, the live node with the highest ID takes over. |
| `ClusterConfig.java` | Central configuration class with constants. | Keeps default hostnames, port numbers (1099, 1101, 1102, 1103), and service names. |
| `RmiUtils.java` | Utility helper for Java RMI. | Finds or creates local RMI registries without throwing port-binding conflicts. |

---

## 5. Why do we have three Order Nodes?

We keep three order nodes so that the food ordering system **never stops working if a single computer or process crashes**.

- In our cluster, exactly **one node is the PRIMARY** (initially Node 1). Only the PRIMARY is allowed to create new orders, accept rider bids, and finalize delivery assignments.
- The other **two nodes are BACKUPS** (Node 2 and Node 3). They do not take orders directly from clients. Instead, they receive copies of every event from the primary and keep identical state in memory and on disk.
- If Node 1 crashes, Node 2 and Node 3 detect that it stopped responding. They run an election, promote the live node with the highest ID (Node 3) to become the new PRIMARY, and continue serving customer orders without data loss.

---

## 6. Why do we have GatewayServer?

The Gateway Server solves a very practical distributed systems problem: **clients should not need to know which internal backend server is currently alive or in charge**.

1. **Single Public Entry Point**: Customer and rider apps connect to `rmi://localhost:1099/GatewayService`. They never need to hard-code the addresses of Node 1, Node 2, or Node 3.
2. **Transparent Request Forwarding**: When a customer places an order, the Gateway looks up which node is currently `PRIMARY` and forwards the call over RMI.
3. **Dynamic Failover**: When a primary node dies and the remaining nodes elect a new leader, the new leader notifies the Gateway. The Gateway immediately redirects future client requests to the new leader without clients needing to restart.
4. **Time Reference**: The Gateway serves as the centralized physical time server for riders running Cristian's clock algorithm.

---

## 7. Why do we have OrderNodeServer and OrderNode1/2/3?

We separate the code into `OrderNodeServer.java` and three tiny files (`OrderNode1.java`, `OrderNode2.java`, `OrderNode3.java`) to follow the **Don't Repeat Yourself (DRY)** principle:

- `OrderNodeServer.java` contains **100% of the actual logic**: how orders are created, how threads run, how events are written to disk, how replication messages are sent, and how elections work.
- `OrderNode1.java`, `OrderNode2.java`, and `OrderNode3.java` are just **tiny 10-line startup launchers**. Each one simply calls:
  ```java
  OrderNodeServer.startServer("1", NodeRole.PRIMARY, 1101); // in OrderNode1
  OrderNodeServer.startServer("2", NodeRole.BACKUP, 1102);  // in OrderNode2
  OrderNodeServer.startServer("3", NodeRole.BACKUP, 1103);  // in OrderNode3
  ```
This guarantees that all three nodes run identical, bug-free server logic with different port and role configurations, rather than maintaining three duplicate copies of server code.

---

## 8. Complete order flow

Here is the exact step-by-step lifecycle of an order in the system:

```text
CustomerClient          GatewayServer          OrderNode 1 (PRIMARY)         Backups (N2, N3)
      |                       |                          |                          |
 1.   |-- placeOrder() ------>|                          |                          |
 2.   |                       |-- placeOrder() --------->|                          |
 3.   |                       |                          | 3a. Generate Order #1001 |
      |                       |                          | 3b. Advance Lamport clock|
      |                       |                          | 3c. OrderStore.apply()   |
      |                       |                          | 3d. EventLog.append()    |
 4.   |                       |                          | 4.  replicateEvent() --->|
      |                       |                          |<--- ReplicationAck ------|
 5.   |                       |                          | 5.  Async preparation    |
      |                       |<-- return orderId 1001 --|                          |
 6.   |<-- return #1001 ------|                          |                          |
```

1. **Customer Places Order**: Customer Alice selects "Deluxe Pizza" and calls `gateway.placeOrder(...)`. Alice's client attaches its current Lamport logical timestamp.
2. **Gateway Forwards**: The Gateway advances its own Lamport clock and forwards the call to the PRIMARY (Node 1).
3. **Primary Creates the Order**:
   - Node 1 updates its Lamport clock using the receive rule: $L = \max(L, L_{\text{incoming}}) + 1$.
   - It generates a unique order ID (`1001`) using `(nodeId * 1000) + sequence`.
   - It applies the `ORDER_CREATED` event to its in-memory `OrderStore`.
   - It flushes the event to `data/node1/events.log` on disk.
4. **Primary Replicates**: Node 1 sends the event to Node 2 and Node 3. Both backups write it to their own disk logs, update their in-memory maps, and return an ACK.
5. **Background Preparation**: Node 1 submits a preparation task to its `ExecutorService` thread pool. After preparation, the order status transitions to `WAITING_FOR_RIDER`.
6. **Rider Clock Sync**: Riders R1, R2, and R3 call `gateway.getServerTime()` to synchronize their physical clocks using Cristian's algorithm.
7. **Riders Accept (Bidding)**: Each rider clicks "Accept". The rider client attaches its estimated physical action timestamp and its logical Lamport clock.
8. **Primary Finalizes Assignment**: Node 1 inspects all bids and assigns the order to the rider whose synchronized physical timestamp was earliest.
9. **Replication & Persistence**: The assignment event is persisted to disk and replicated to all backups with explicit ACKs.
10. **Delivery Completion**: The assigned rider reports arrival at the restaurant (checked against a 10-minute deadline) and marks the order `DELIVERED`.

---

## 9. Physical clock synchronization

### Why does it exist?
In a distributed food delivery app, multiple delivery riders on different phones may try to accept the same lucrative order at roughly the same second. Because each phone's internal hardware clock drifts, we cannot trust raw phone timestamps without synchronization.

### Who uses it?
`RiderClient` uses `PhysicalClockSynchronizer` to synchronize with the `GatewayServer`.

### What algorithm is used?
**Cristian's Algorithm with Round-Trip Time (RTT) adjustment**:
1. Rider records local time $t_0$ and asks Gateway for the current time.
2. Gateway returns its physical timestamp $T_{\text{server}}$.
3. Rider records response receipt time $t_1$.
4. Total network round-trip time is $\text{RTT} = t_1 - t_0$.
5. Assuming symmetric network latency, the server timestamp when the reply arrives is estimated as $T_{\text{server}} + \frac{\text{RTT}}{2}$.
6. The rider computes its clock offset:
   $$\text{Offset} = T_{\text{server}} - \left(t_0 + \frac{\text{RTT}}{2}\right)$$
7. Future rider actions calculate estimated physical time as:
   $$\text{Estimated Time} = \text{Local System Time} + \text{Offset}$$

### Simple Example
- Rider 1 clicks accept at estimated physical time `10:30:01.200`.
- Rider 2 clicks accept at estimated physical time `10:30:01.500`.
- Even if Rider 1 suffers a 100ms network transmission delay and Rider 1's network packet reaches the server *after* Rider 2's packet, the server compares their synchronized physical action timestamps and fairly awards the order to Rider 1.

> [!IMPORTANT]
> Synchronized physical clocks estimate when an action occurred on a client phone. **They do NOT prove which RMI network packet arrived at the server first.**

---

## 10. Lamport clock

### Why does it exist?
Physical clocks have measurement errors and network jitter. A **Lamport Logical Clock** tracks **causal ordering** (cause-and-effect): it ensures that every observer in the distributed system agrees that event $A$ happened before event $B$ ($A \to B$).

### The Two Simple Rules
1. **Local Event / Send Rule**: Before recording a local action or sending a message, increment your clock by 1:
   $$L = L + 1$$
2. **Receive Rule**: When receiving a message carrying remote timestamp $L_{\text{msg}}$, set your clock to the maximum of your local clock and $L_{\text{msg}}$, then add 1:
   $$L = \max(L, L_{\text{msg}}) + 1$$

### Example Causal Progression in the System
```text
Customer sends placeOrder()          [Lamport = 1]
Gateway forwards request to Primary  [Lamport = 2]
Primary creates Order 1001           [Lamport = 4]
Rider R1 submits acceptance bid      [Lamport = 6]
Primary assigns order to Rider R1    [Lamport = 11]
Order delivered to customer          [Lamport = 15]
```

### Concurrent Events & Total Ordering
If Rider R1 and Rider R2 act independently without talking to each other, both may produce an event with Lamport timestamp $L=6$. Lamport time alone cannot tell which happened first. To produce a deterministic total order, the system breaks ties using:
$$(L, \text{sourceId}, \text{eventId})$$

> [!NOTE]
> Lamport time is purely an integer counter of logical steps. **It does not represent real-world hours, minutes, or seconds.**

---

## 11. Threading

### Why is multithreading necessary?
At any given moment, multiple customers can be placing orders, multiple riders can be submitting acceptance bids, and peer nodes can be exchanging replication heartbeats over RMI simultaneously.

### How threads are managed:
1. **`ExecutorService`**:
   Inside `OrderNodeServer`, an `Executors.newFixedThreadPool(4)` is used. When an order is created, the asynchronous food preparation task is submitted to the pool. This prevents creating unbounded new threads for every incoming order.
2. **`ConcurrentHashMap`**:
   `OrderStore` stores active orders in a `ConcurrentHashMap<Integer, Order>`. This allows different threads to read or look up different orders concurrently without blocking each other.
3. **Why `ConcurrentHashMap` alone is NOT enough for `Order`**:
   `ConcurrentHashMap` only protects map operations (inserting or retrieving a key). It does **not** protect the inside of an `Order` object.
   - *Example*: If Rider 1 and Rider 2 accept Order 1001 at the exact same instant on two different RMI threads, both threads retrieve the same `Order` object from the map.
   - If `Order` methods were not thread-safe, both threads would modify `assignedRider` or the acceptance list simultaneously, corrupting the data.
   - Therefore, all mutating methods on `Order.java` (`addAcceptance`, `finalizeAssignment`, `arriveAtRestaurant`, `deliverOrder`) are explicitly declared `synchronized`.

---

## 12. Persistence

### What is kept in memory vs. on disk?
- **In Memory (RAM)**:
  `OrderStore` holds a `ConcurrentHashMap<Integer, Order>`. This gives microsecond read and update speeds while the server is running.
- **On Disk (Storage)**:
  `EventLog` writes every event to `data/node<id>/events.log` in an append-only, pipe-delimited text format.

### Why do we need both?
If a server only used RAM, all orders would vanish the instant the power cut or the process was killed. If a server only used disk without RAM, every read operation would require slow disk I/O.

### How crash durability works:
Whenever an order is created or updated, `EventLog.append(event)` writes the line to disk and immediately calls:
```java
fos.getFD().sync(); // Forces operating system buffers to flush to physical storage
```
This guarantees the data is physically on disk before returning an ACK to the client or primary.

### What happens on restart?
When a node starts up:
1. It opens `data/node<id>/events.log`.
2. It replays every logged event chronologically into its empty in-memory `OrderStore`.
3. It completely restores its memory to the exact state it had before shutting down.

---

## 13. Replication

Replication ensures that all three order nodes have identical state.

```text
PRIMARY (Node 1)                     BACKUP (Node 2)                   BACKUP (Node 3)
      |                                     |                                 |
      | 1. OrderStore.apply()               |                                 |
      | 2. EventLog.append() + sync()       |                                 |
      |                                     |                                 |
      | 3. replicateEvent(event) ---------->|                                 |
      |                                     | 3a. OrderStore.apply()          |
      |                                     | 3b. EventLog.append() + sync()  |
      | 4. <-- ReplicationAck(true) --------|                                 |
      |                                     |                                 |
      | 5. replicateEvent(event) -------------------------------------------->|
      |                                     |                                 | 5a. OrderStore.apply()
      |                                     |                                 | 5b. EventLog.append() + sync()
      | 6. <-- ReplicationAck(true) ------------------------------------------|
```

- **What is copied?** High-level domain `ReplicationEvent` objects (containing order ID, customer name, restaurant, items, status, Lamport timestamp, and sequential version number).
- **What is an ACK?** A `ReplicationAck` object returned by the backup confirming whether the event was successfully saved.
- **Handling duplicate deliveries**: Network retries or log replays might deliver the same event twice. `OrderStore` tracks applied event IDs in a thread-safe `Set<String>`. If an event ID has already been applied, it is safely ignored as a no-op (`Duplicate ignored`).
- **If one backup is offline**: If Node 2 is down, the primary catches the network exception, logs a warning, and continues serving customers. When Node 2 restarts, it contacts the primary and downloads only the missing events.

> [!NOTE]
> This is **Application-State Replication** implemented via Java RMI and Java collections. It is **NOT** database replication (such as PostgreSQL WAL streaming or MySQL binlogs).

---

## 14. Heartbeats and failure detection

### Who monitors whom?
Each BACKUP node runs a `HeartbeatMonitor` background daemon thread targeting the current PRIMARY node.

### How it works:
1. Every **500 milliseconds**, the backup issues an RMI `heartbeat()` or `ping()` probe to the primary.
2. If the primary responds, the backup resets its failure counter and marks the primary `ALIVE`.
3. If the primary throws a `RemoteException` or connection timeout, the backup increments its missed-heartbeat count.
4. If **2 consecutive heartbeats fail**, the backup marks the primary as `DEAD` and triggers leader election.

> [!WARNING]
> In distributed systems, a heartbeat timeout is a **failure-detection decision** based on communication loss. It does not physically prove the machine was destroyed; it means the primary is unreachable within the agreed time window.

---

## 15. Bully election

When the PRIMARY (Node 1) crashes, the remaining nodes hold a **Bully Leader Election** to choose the new leader. In the Bully algorithm, **the live node with the highest numeric ID always wins**.

### Step-by-Step Example
Our nodes have IDs `1`, `2`, and `3`.

```text
Node 2 (ID: 2)                   Node 3 (ID: 3)                 Gateway Server
      |                                |                               |
 1.   | Detects Node 1 is DEAD         |                               |
 2.   |-- ELECTION message ----------->|                               |
 3.   |                                | ID 3 > 2 -> Replies OK        |
      |<-- OK answer ------------------| Starts its own election       |
 4.   | Yields and waits               | No higher nodes exist!        |
 5.   |                                | Node 3 WINS the election!     |
 6.   |                                | Sets role = PRIMARY           |
 7.   |<-- COORDINATOR announcement ---|                               |
 8.   | (Node 2 stays BACKUP)          |-- notifyLeaderChanged("3") -->|
      |                                |                               | (Gateway routes to N3)
```

1. Node 1 is killed.
2. Node 2's heartbeat monitor detects Node 1 is dead. Node 2 starts an election by sending an `ELECTION` message to all nodes with a higher ID (Node 3).
3. Node 3 has a higher ID ($3 > 2$). Node 3 responds `OK` to Node 2, telling Node 2 to back down.
4. Node 3 starts its own election by checking for nodes with an ID higher than 3.
5. Since there are no higher nodes in the cluster, **Node 3 declares victory**!
6. Node 3 sets its internal role to `NodeRole.PRIMARY`.
7. Node 3 broadcasts a `COORDINATOR` message to all lower peers (Node 2 acknowledges and remains `BACKUP`).
8. Node 3 calls `gateway.notifyLeaderChanged("3")` so the Gateway knows where to send future orders.

---

## 16. Failover

Failover is the complete sequence of events that keeps the food ordering system running when the leader crashes:

```text
[1. Replication]        Primary replicates Order 1001 to Node 2 and Node 3.
                                 |
[2. Crash]              Primary (Node 1) crashes / is killed.
                                 |
[3. Detection]          HeartbeatMonitor on Node 2 detects missed heartbeats.
                                 |
[4. Election]           Bully election runs; Node 3 wins and assumes PRIMARY role.
                                 |
[5. Gateway Update]     Node 3 calls gateway.notifyLeaderChanged("3").
                        Gateway updates its internal routing table to Node 3.
                                 |
[6. New Traffic]        Customer queries Order 1001 -> Gateway asks Node 3 -> SUCCEEDS!
                        Customer places Order 1002 -> Gateway routes to Node 3 -> SUCCEEDS!
```

Because state was continuously replicated before the crash, **no orders are lost**, and clients experience seamless continuity.

---

## 17. Recovery

What happens when failed Node 1 comes back online after being repaired or restarted?

1. **Local Replay**: Node 1 starts up. It reads its own `data/node1/events.log` from disk and reconstructs all orders it knew about prior to crashing (e.g. Order 1001).
2. **Find Current Leader**: Node 1 queries the Gateway to discover who is the current active PRIMARY. The Gateway reports that **Node 3 is the new PRIMARY**.
3. **Fetch Missing State**: Node 1 sees its local log stopped at version 5. It calls `Node3.getMissingEvents(afterVersion = 5)` over RMI.
4. **Apply and Catch-up**: Node 3 returns all events that occurred while Node 1 was dead (such as Order 1002). Node 1 applies them to its `OrderStore` and appends them to its local disk log.
5. **Rejoin as BACKUP**: Node 1 starts its `HeartbeatMonitor` targeting Node 3 and quietly rejoins the cluster strictly as a **BACKUP** node. It does not bully Node 3.

---

## 18. Docker

The repository includes a `Dockerfile` and a `docker-compose.yml` file to demonstrate containerized multi-process execution:

### Containerized Topology Table

| Container Name | Purpose / Executable | RMI Port | Network Mode |
| :--- | :--- | :--- | :--- |
| `order-node-1` | Runs initial PRIMARY node (`com.foodordering.node.OrderNode1`) | 1101 | `host` |
| `order-node-2` | Runs BACKUP node (`com.foodordering.node.OrderNode2`) | 1102 | `host` |
| `order-node-3` | Runs BACKUP node (`com.foodordering.node.OrderNode3`) | 1103 | `host` |
| `gateway-server`| Runs client Gateway router (`com.foodordering.gateway.GatewayServer`) | 1099 | `host` |

- **How they communicate**: All four server containers run using `network_mode: host` so that Java RMI registries on ports 1099, 1101, 1102, and 1103 can be contacted seamlessly without complex RMI NAT port-forwarding issues.
- **Where clients run**: `CustomerClient` and `RiderClient` run on the host machine (or in separate transient console containers) connecting to the Gateway at port 1099.
- **Local Alternative**: Running locally without Docker using standard `java -cp bin ...` commands is fully supported and recommended for interactive testing.

---

## 19. What is NOT in this project

To maintain complete academic honesty during viva and evaluation, the following technologies are **explicitly NOT part of the codebase**:

- **NO Spring Boot / Microservice Frameworks**: The project uses pure native Java SE with `java.rmi.*` and `java.util.concurrent.*`.
- **NO Database Engine**: There is no PostgreSQL, MySQL, MongoDB, SQLite, H2, JPA, or Hibernate. All data is stored in Java collections and flat text logs.
- **NO HTTP / REST Gateway**: There is no NGINX, Express, or Spring Cloud Gateway. The Gateway is a pure Java RMI server.
- **NO Message Queues**: There is no Apache Kafka, RabbitMQ, or ActiveMQ. Event replication is performed over direct RMI calls.
- **NO Container Orchestrator**: There is no Kubernetes or Docker Swarm.

---

## 20. One complete picture

This single diagram summarizes the entire architecture, data flow, and distributed mechanisms:

```text
====================================================================================================
                             DISTRIBUTED FOOD ORDERING SYSTEM
====================================================================================================

      [Customer Client]                                           [Rider Client]
      - Places Order                                              - Syncs Physical Clock (Cristian)
      - Advances Lamport Clock                                    - Submits Bids with Action Time
             |                                                                   |
             +----------------------- Java RMI (Port 1099) ----------------------+
                                             |
                                             v
                                  +--------------------+
                                  |   GatewayServer    |  <-- Time Reference (Cristian)
                                  |   (Port 1099)      |  <-- Routes to PRIMARY
                                  |                    |  <-- Dynamic Failover Router
                                  +---------+----------+
                                            |
                         +------------------+------------------+
                         | (Client Write Operations)           |
                         v                                     |
               +--------------------+                          |
               | OrderNode 1 (1101) |                          |
               |   ROLE: PRIMARY    |                          |
               +--------------------+                          |
               | - OrderStore (RAM) |                          |
               | - EventLog (Disk)  |                          |
               | - ExecutorService  |                          |
               | - Lamport Clock    |                          |
               +---------+----------+                          |
                         |                                     |
                         | [ReplicationEvent + ACKs]           |
                         +------------------+                  |
                         |                  |                  |
                         v                  v                  |
               +--------------------+  +--------------------+  |
               | OrderNode 2 (1102) |  | OrderNode 3 (1103) |  |
               |   ROLE: BACKUP     |  |   ROLE: BACKUP     |  |
               +--------------------+  +--------------------+  |
               | - OrderStore (RAM) |  | - OrderStore (RAM) |  |
               | - EventLog (Disk)  |  | - EventLog (Disk)  |  |
               +---------+----------+  +---------+----------+  |
                         |                       |             |
                         | <== Heartbeat Probes =|             |
                         | <== Bully Election == |             |
                         +-----------------------+             |
                                     |                         |
                    [If Primary Node 1 Crashes]                |
                    1. Heartbeat times out                     |
                    2. Bully Election promotes Node 3          |
                    3. Node 3 announces to Gateway ------------+
                    4. Gateway routes new traffic to Node 3!
====================================================================================================
```

---

## 21. Legacy files explanation

The project history includes five legacy prototype files in `archive/legacy/`:

1. `archive/legacy/FoodOrderingServer.java`: Replaced by the modular multi-process architecture (`GatewayServer` + `OrderNodeServer`).
2. `archive/legacy/FoodOrderingClient.java`: Replaced by dedicated client applications (`CustomerClient` and `RiderClient`).
3. `archive/legacy/FoodOrderingService.java`: Superseded and cleanly separated into `GatewayRemote` and `OrderNodeRemote`.
4. `archive/legacy/CustomerService.java`: Merged into `GatewayRemote` and `GatewayServer`.
5. `archive/legacy/RiderService.java`: Merged into `GatewayRemote` and `GatewayServer`.

None of these legacy files are compiled into the application or used by active components. They are archived strictly for historical reference.
