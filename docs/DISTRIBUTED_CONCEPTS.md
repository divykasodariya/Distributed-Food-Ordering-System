# Distributed Concepts 

This project explores application-level distributed system concepts through a food ordering architecture.

## 1. System Architecture
The system consists of:
- **Gateway Server**: A single entry point for clients (Customer/Rider), routing requests to the active Primary node and acting as a physical time server.
- **Order Nodes (1 Primary, 2 Backups)**: The backend nodes responsible for state management, data replication, and fault tolerance.

## 2. Java RMI (Remote Method Invocation)
All inter-process communication is handled via Java RMI.
- **Clients -> Gateway**: Clients invoke methods like `placeOrder` and `getServerTime` over RMI.
- **Gateway -> Node**: The Gateway forwards client writes to the current Primary node.
- **Node -> Node**: The nodes use RMI to replicate events, exchange heartbeats, and conduct Bully elections.

## 3. Persistent Event Log and Replication
To ensure fault tolerance without an external database, the system uses an Application-State Replication mechanism.
- The Primary node applies changes (events) to its in-memory `OrderStore` and appends them to a local disk log (`events.log`) with a physical `sync()`.
- The Primary node sends the `ReplicationEvent` to all Backup nodes over RMI.
- Backups persist the event to their own logs and return a `ReplicationAck`.

## 4. Node Recovery
When a failed node restarts:
- It replays its local `events.log` into memory to reconstruct its state.
- It queries the Gateway to find the current active Primary.
- It requests any missing events (Catch-up) from the Primary that occurred during its downtime.
- It quietly rejoins the cluster as a Backup.

## 5. Heartbeat Failure Detection
Backups monitor the health of the Primary using a `HeartbeatMonitor`:
- Backups issue periodic RMI pings (every 500ms) to the Primary.
- If 2 consecutive heartbeats fail (due to connection timeout or `RemoteException`), the Primary is marked as DEAD, triggering a leader election.

## 6. Bully Leader Election
When the Primary dies, remaining nodes hold an election.
- The Bully algorithm is used, meaning the live node with the highest numeric ID always wins.
- A challenging node sends an `ELECTION` message to higher-ID nodes. If none respond, it declares itself the new leader.
- The new leader broadcasts a `COORDINATOR` message and updates the Gateway's routing table (Dynamic Failover).

## 7. Distributed Clocks
The system employs both logical and physical clocks for different purposes.

### Lamport Logical Clocks
Lamport clocks track causal orderings ($A \to B$) across distributed processes.
- **Send Rule**: Increment local clock by 1 before an action.
- **Receive Rule**: Set clock to $\max(\text{local}, \text{incoming}) + 1$.
Every order creation, acceptance, and assignment advances the Lamport clock.

### Cristian's Physical Clock Synchronization
Used to synchronize time between the Server (Gateway) and Delivery Riders.
- Rider queries the Gateway for the server time and measures the Round-Trip Time (RTT).
- The calculated offset is applied to the Rider's local clock to ensure fairness during concurrent bids.

## 8. Concurrency
- `ExecutorService`: Background order preparation tasks run on a bounded thread pool.
- `ConcurrentHashMap`: Used by the `OrderStore` for non-blocking concurrent map lookups.
- `synchronized`: Used for mutating operations on individual orders to prevent race conditions during concurrent rider bids.

## 9. Load Balancing & Read Offloading (Smooth Weighted Round-Robin)
To prevent the Primary node from becoming a CPU/IO bottleneck:
- **Write Offloading**: All state-modifying write operations (`placeOrder`, `acceptDelivery`, `deliverOrder`) are routed strictly to the **PRIMARY** node to guarantee event ordering and log replication.
- **Read Offloading (Smooth WRR)**: Read-only queries (`getOrderStatus`) are load-balanced across all healthy nodes (Primary and Backups) using Nginx's **Smooth Weighted Round-Robin** algorithm.
  - **PRIMARY Node Weight**: Assigned a lower weight (`1`) so its resources remain dedicated to writes and disk synchronization.
  - **BACKUP Nodes Weight**: Assigned higher weights (`3`) to maximize utilization of otherwise idle backup nodes.
- **Dynamic Topology Adaptation**: When a leader election occurs or a node fails, the Gateway automatically clears and recalculates the WRR state without interrupting client requests.

