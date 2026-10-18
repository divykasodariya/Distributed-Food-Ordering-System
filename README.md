# Distributed Food Ordering System for DC

A modular, multi-process distributed systems project implemented in native Java, Java RMI, Docker, and an interactive React + TypeScript + Vite frontend.

This project explores application-level state machine replication and recovery using in-memory collections and disk event sourcing, without relying on third-party frameworks like Spring Boot, message queues, or external databases.

---

## 1. Directory Structure

```text
distributed-food-ordering/
├── src/
│   └── main/java/com/foodordering/
│       ├── api/         # RMI remote interfaces (GatewayRemote, OrderNodeRemote)
│       ├── client/      # Customer and Rider interactive CLI clients
│       ├── clock/       # Lamport and Cristian clock implementations
│       ├── config/      # Cluster configuration constants
│       ├── election/    # Bully Leader Election algorithm
│       ├── gateway/     # Gateway Server implementation
│       ├── http/        # Built-in native Java HTTP & SSE bridge (:8080)
│       ├── model/       # Data transfer objects, entities, and SystemEvent telemetry
│       ├── monitoring/  # HeartbeatMonitor and EventBroadcaster
│       ├── node/        # OrderNodeServer core engine
│       ├── storage/     # EventLog persistence, OrderStore, and ReplicationManager
│       └── util/        # RMI utility helpers
├── frontend/            # React 19 + TypeScript + Vite + Tailwind CSS dashboard
│   ├── src/
│   │   ├── components/  # Shell layout and navigation
│   │   ├── hooks/       # useLiveStream (SSE hook)
│   │   ├── pages/       # 14 complete interactive pages
│   │   └── types/       # TypeScript schema definitions
├── data/                # Persistent node logs (node1, node2, node3)
├── docs/                # Detailed system documentation
├── Dockerfile
├── docker-compose.yml
└── README.md
```

---

## 2. Distributed Concepts Demonstrated

- **Java RMI Remote Procedure Calls**: Client-to-Gateway and Node-to-Node communication.
- **Lamport Logical Clocks**: Tracking distributed causal event history ($A \to B \implies L(A) < L(B)$).
- **Physical Clock Synchronization**: Cristian's algorithm with RTT adjustment and symmetric latency bounds.
- **Application-State Replication**: Primary broadcasts serialized events to Backups with explicit ACKs.
- **Crash Durability**: Append-only events log flushed with physical `sync()` disk flushes.
- **Heartbeat Failure Detection**: Scheduled periodic health probes (500ms, 2-failure threshold).
- **Distributed Bully Leader Election**: Dynamic primary promotion upon failure (highest numeric ID wins).
- **Dynamic Failover**: Gateway updates routing upon leader announcement without client downtime.
- **Smooth Weighted Round-Robin Load Balancing**: Gateway offloads read queries across available nodes (Primary weight: 1, Backup weight: 3).
- **Node Recovery**: Stale or restarted nodes replay logs and catch-up from primary.
- **Concurrency**: Thread-safe operations using `ConcurrentHashMap` and fixed thread pools.

---

## 3. How to Run

### Option A: Using Docker Compose (Cluster + HTTP Bridge)

1. **Start the Cluster and HTTP Bridge**:
   ```bash
   docker compose up -d gateway node1 node2 node3 http-bridge
   ```

2. **Launch the Frontend**:
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
   Open `http://localhost:5173` in your browser.

---

### Option B: Compiling and Running Locally (Native Multi-Process)

1. **Compile all Java sources**:
   ```bash
   mkdir -p bin
   javac -d bin $(find src/main/java -name "*.java")
   ```

2. **Start the Services in Separate Terminals**:
   ```bash
   # Terminal 1: Gateway Server
   java -cp bin com.foodordering.gateway.GatewayServer

   # Terminal 2: Order Node 1 (Primary)
   java -cp bin com.foodordering.node.OrderNodeServer 1 PRIMARY 1101

   # Terminal 3: Order Node 2 (Backup)
   java -cp bin com.foodordering.node.OrderNodeServer 2 BACKUP 1102

   # Terminal 4: Order Node 3 (Backup)
   java -cp bin com.foodordering.node.OrderNodeServer 3 BACKUP 1103

   # Terminal 5: HTTP & SSE Bridge
   java -cp bin com.foodordering.http.HttpBridgeServer 8080

   # Terminal 6: Frontend Development Server
   cd frontend
   npm run dev
   ```

3. **Open the Frontend**:
   Navigate to `http://localhost:5173` in your browser.

---

## 4. Frontend Application Pages

The frontend provides 14 dedicated interactive pages designed for academic and technical presentations:

1. **System Overview Dashboard (`/`)**: Cluster status, node roles, request throughput, latency graphs, and live telemetry feed.
2. **Interactive Cluster Topology (`/topology`)**: Visual graph connecting Customers, Riders, Gateway, and Order Nodes.
3. **Java RMI & Replication (`/rmi-replication`)**: Step-by-step sequence diagrams and state machine replication inspection.
4. **Lamport Logical Clocks (`/lamport-clocks`)**: Multi-lane event timeline verifying partial and causal ordering.
5. **Cristian Time Sync (`/cristian-sync`)**: Interactive latency injection, RTT measurement, and offset calculation.
6. **Bully Election & Failover (`/bully-election`)**: Node challenge hierarchy and leader election trigger.
7. **Heartbeat Monitor (`/heartbeat`)**: 500ms heartbeat status, failure detection, and health histories.
8. **Persistence & Recovery (`/persistence-recovery`)**: Raw disk log viewer (`events.log`) with replay status.
9. **Smooth Weighted Round-Robin (`/load-balancing`)**: Batch read generator verifying 1:3:3 distribution.
10. **Concurrency & Performance (`/concurrency`)**: Parallel request testing with throughput measurements.
11. **Customer Order App (`/customer-app`)**: Menu browsing, order placement, and live status tracking.
12. **Rider Portal (`/rider-app`)**: Multi-rider bidding, arrival confirmation, and delivery completion.
13. **Distributed Event Explorer (`/event-explorer`)**: Filterable, searchable audit log with JSON export.
14. **Guided Demonstrations (`/demo-scenarios`)**: Repeatable end-to-end scenarios with pass/fail evaluation.
