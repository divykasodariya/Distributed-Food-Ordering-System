# Distributed Food Ordering System

A modular, multi-process distributed systems project implemented in native Java and Java RMI. 

This project explores application-level state machine replication and recovery using in-memory collections and disk event sourcing, without relying on third-party frameworks like Spring Boot, message queues, or external databases.

---

## 1. Directory Structure

```text
distributed-food-ordering/
├── src/
│   └── main/java/com/foodordering/
│       ├── api/         # RMI remote interfaces
│       ├── client/      # Customer and Rider interactive CLI clients
│       ├── clock/       # Lamport and Cristian clock implementations
│       ├── config/      # Cluster configuration constants
│       ├── election/    # Bully Leader Election algorithm
│       ├── gateway/     # Gateway Server implementation
│       ├── model/       # Data transfer objects and entities
│       ├── monitoring/  # Heartbeat and failure detection
│       ├── node/        # OrderNodeServer core engine
│       ├── storage/     # Event log persistence and replication
│       └── util/        # RMI utility helpers
├── data/                # Persistent node logs (node1, node2, node3)
├── docs/                # Detailed system documentation
├── Dockerfile
├── docker-compose.yml
└── README.md
```

---

## 2. Distributed Concepts Demonstrated

- **Java RMI Remote Procedure Calls**: Client-to-Gateway and Node-to-Node communication.
- **Lamport Logical Clocks**: Tracking distributed causal event history.
- **Physical Clock Synchronization**: Cristian's algorithm with RTT adjustment.
- **Application-State Replication**: Primary broadcasts serialized events to Backups.
- **Crash Durability**: Append-only events log flushed with physical disk syncs.
- **Heartbeat Failure Detection**: Scheduled periodic health probes.
- **Distributed Bully Leader Election**: Dynamic primary promotion upon failure.
- **Dynamic Failover**: Gateway updates routing upon leader announcement.
- **Smooth Weighted Round-Robin Load Balancing**: Gateway offloads read queries across available nodes (Primary weight: 1, Backup weight: 3) to optimize CPU/disk utilization.
- **Node Recovery**: Stale or restarted nodes replay logs and catch-up from primary.
- **Concurrency**: Thread-safe operations using `ConcurrentHashMap` and thread pools.

For an in-depth explanation of these concepts, see [docs/DISTRIBUTED_CONCEPTS.md](docs/DISTRIBUTED_CONCEPTS.md).

---

## 3. How to Run

### Using Docker Compose (Recommended)

1. **Start the Cluster**:
   ```bash
   docker compose up -d gateway node1 node2 node3
   ```
   *(This launches a Gateway Server, 1 Primary Node, and 2 Backup Nodes)*

2. **Run the Customer Client**:
   ```bash
   docker compose run --rm customer
   ```
   *(Interactive prompts will guide you to place orders)*

3. **Run a Rider Client**:
   ```bash
   docker compose run --rm rider
   ```
   *(Use multiple terminals to start multiple riders)*

For detailed testing workflows (including testing failures and recovery), see [docs/DEMO_FLOW.md](docs/DEMO_FLOW.md).

### Compiling and Running Locally (Without Docker)

Compile the project:
```bash
mkdir -p bin
javac -d bin $(find src/main/java -name "*.java")
```

Run in separate terminals:
```bash
# Start Gateway
java -cp bin com.foodordering.gateway.GatewayServer

# Start Order Nodes (Node 1 as Primary, Nodes 2/3 as Backups)
java -cp bin com.foodordering.node.OrderNodeServer 1 PRIMARY 1101
java -cp bin com.foodordering.node.OrderNodeServer 2 BACKUP 1102
java -cp bin com.foodordering.node.OrderNodeServer 3 BACKUP 1103

# Run Clients
java -cp bin com.foodordering.client.customer.CustomerClient
java -cp bin com.foodordering.client.rider.RiderClient R1
```
