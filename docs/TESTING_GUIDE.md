# Distributed Food Ordering System — Complete Testing Guide

This guide explains exactly how to run and test the project from start to finish.

The system has been verified end-to-end to ensure testing is completely repeatable:
**start the system → verify servers → run customer → run riders → test clocks → test replication → kill primary → test election/failover → restart node → test recovery.**

---

# 1. What is running?

The system has four long-running server processes:

1. **GatewayServer** (`com.foodordering.gateway.GatewayServer`) — Port `1099`
2. **OrderNode1** (`com.foodordering.node.OrderNode1`) — Port `1101`
3. **OrderNode2** (`com.foodordering.node.OrderNode2`) — Port `1102`
4. **OrderNode3** (`com.foodordering.node.OrderNode3`) — Port `1103`

The normal starting state is:

```text
Node 1 = PRIMARY
Node 2 = BACKUP
Node 3 = BACKUP
```

Customer (`CustomerClient`) and Rider (`RiderClient`) are clients. They are transient client applications and are not part of the permanent server cluster.

---

# 2. Docker containers

The Docker setup defined in `docker-compose.yml` provides:

| Container Name | Service Name | Purpose | Port |
|---|---|---|---|
| `gateway-server` | `gateway` | Receives requests from clients and forwards them to PRIMARY | `1099` |
| `order-node-1` | `node1` | Order Node 1, initially PRIMARY | `1101` |
| `order-node-2` | `node2` | Order Node 2, initially BACKUP | `1102` |
| `order-node-3` | `node3` | Order Node 3, initially BACKUP | `1103` |

There are **4 long-running server containers** using `network_mode: host` to support Java RMI port binding directly.

Customer and Rider clients can be run directly from the host machine using `java -cp bin ...` or started as temporary containers (`docker compose run --rm customer` / `docker compose run --rm rider`).

---

# 3. Before starting

Prerequisites:
- Java JDK 17 or higher
- Docker & Docker Compose

Verify:

```bash
docker --version
docker compose version
```

Make sure both commands return valid versions before continuing.

---

# 4. Clean old containers and data

From the project root:

```bash
docker compose down
```

For a completely clean test with fresh logs:

```bash
docker compose down --volumes
rm -f data/node1/events.log data/node2/events.log data/node3/events.log
```

---

# 5. Build the project

From the project root:

```bash
docker compose build
```

Or, if building on the host directly:

```bash
mkdir -p bin
javac -d bin $(find src/main/java -name "*.java")
```

---

# 6. Start the four server containers

Start all four server services in the background:

```bash
docker compose up -d gateway node1 node2 node3
```

Check the status of running containers:

```bash
docker compose ps
```

Expected result:

```text
NAME             IMAGE          COMMAND                  SERVICE   STATUS
gateway-server   dc-gateway     "java -cp bin com.f..."   gateway   running
order-node-1     dc-node1       "java -cp bin com.f..."   node1     running
order-node-2     dc-node2       "java -cp bin com.f..."   node2     running
order-node-3     dc-node3       "java -cp bin com.f..."   node3     running
```

All four must be in the `running` state before proceeding.

---

# 7. Check server logs

Inspect each server separately to verify that RMI services are registered and healthy.

### Gateway:
```bash
docker compose logs gateway
```
Look for:
```text
Gateway Server Running on port 1099
RMI Endpoint: rmi://localhost:1099/GatewayService
Initial PRIMARY Node: Node 1 (port 1101)
Initial BACKUP Nodes : Node 2 (1102), Node 3 (1103)
```

### Node 1 (PRIMARY):
```bash
docker compose logs node1
```
Look for:
```text
Order Node 1 Started Successfully
Role: PRIMARY
RMI Port: 1101
RMI URL: rmi://localhost:1101/OrderNode1
Storage: data/node1/events.log
```

### Node 2 (BACKUP):
```bash
docker compose logs node2
```
Look for:
```text
Order Node 2 Started Successfully
Role: BACKUP
RMI Port: 1102
[HEARTBEAT] Node 2 started heartbeat monitor targeting PRIMARY Node 1
```

### Node 3 (BACKUP):
```bash
docker compose logs node3
```
Look for:
```text
Order Node 3 Started Successfully
Role: BACKUP
RMI Port: 1103
[HEARTBEAT] Node 3 started heartbeat monitor targeting PRIMARY Node 1
```

---

# 8. Verify the initial cluster

The initial cluster topology is:

```text
               Gateway (1099)
                     |
         +-----------+-----------+
         |                       |
         v                       v
    Node 1 (1101)           Node 2 (1102) & Node 3 (1103)
       PRIMARY                         BACKUPS
```

Do not continue until all three Order Nodes and the Gateway are up and responding.

---

# 9. Start the Customer Client

### Method A — Run from the host (Recommended for interactive prompts)
```bash
java -cp bin com.foodordering.client.customer.CustomerClient
```

### Method B — Run using Docker
```bash
docker compose run --rm customer
```

The client connects to:
```text
rmi://localhost:1099/GatewayService
```
The client never talks directly to an Order Node; all traffic flows through the Gateway.

---

# 10. Browse the menu

From the interactive menu in `CustomerClient`:

1. Select option `1` (`Browse Restaurant Menus`).
2. Verify the Gateway returns the menu catalog:
   ```text
   ================= RESTAURANT MENUS =================
   Restaurant: Pizza Palace (ID: REST_01)
      - Margherita Pizza     : $12.99
      - Deluxe Pizza         : $16.50
   Restaurant: Burger Barn (ID: REST_02)
      - Classic Cheeseburger : $9.50
      - Mega Burger          : $13.75
   ====================================================
   ```
3. Check Gateway logs (`docker compose logs gateway`). You will see that the customer communicated solely with the Gateway.

---

# 11. Create the first order

From `CustomerClient`:

1. Select option `2` (`Place Pizza Order`) or option `3` (`Place Custom Order`).
2. Enter:
   - Customer Name: `Alice`
   - Restaurant Name: `Pizza Palace`
   - Item Name: `Deluxe Pizza`
   - Quantity: `2`

Expected flow:
```text
CustomerClient  --- placeOrder(Lamport=1) --->  GatewayServer
GatewayServer   --- placeOrder(Lamport=2) --->  OrderNode1 (PRIMARY)
OrderNode1 creates Order, persists to data/node1/events.log, replicates to N2 and N3
```

Check the console output:
```text
>>> [SUCCESS] Order placed!
>>> Distributed Order ID: #1001 | Lamport Sent: 1
>>> Status: WAITING_FOR_RIDER
```

---

# 12. Check the first order number

The system uses a safe, collision-free distributed Order ID formula:
$$\text{orderId} = (\text{nodeId} \times 1000) + \text{orderSequence}$$

Because Node 1 is PRIMARY, the first order is `#1001`. If Node 3 later becomes primary, its orders start at `#3001`. This guarantees no collisions even across failovers.

Verify the status using option `4` in `CustomerClient` and enter `1001`.
Expected status: `WAITING_FOR_RIDER`.

---

# 13. Test multithreading

To verify that concurrent orders are processed asynchronously by the `ExecutorService` thread pool:

In a separate terminal on the host, run:
```bash
java -cp bin com.foodordering.client.customer.CustomerClient place Bob "Burger Barn" "Mega Burger" 1
java -cp bin com.foodordering.client.customer.CustomerClient place Charlie "Pizza Palace" "Margherita Pizza" 1
java -cp bin com.foodordering.client.customer.CustomerClient place Dave "Burger Barn" "Classic Cheeseburger" 2
```

Check Node 1 logs (`docker compose logs node1`):
```text
[pool-1-thread-1] Order #1002 prepared and WAITING_FOR_RIDER on Node 1
[pool-1-thread-2] Order #1003 prepared and WAITING_FOR_RIDER on Node 1
[pool-1-thread-3] Order #1004 prepared and WAITING_FOR_RIDER on Node 1
```
This confirms that requests are handled concurrently through the bounded thread pool.

---

# 14. Start Rider 1 (R1)

In a new terminal window, start Rider R1:

```bash
java -cp bin com.foodordering.client.rider.RiderClient R1
```
*(Or via Docker: `docker compose run --rm rider java -cp bin com.foodordering.client.rider.RiderClient R1`)*

---

# 15. Start Rider 2 (R2) & Rider 3 (R3)

Open two more terminals and start independent rider processes:

```bash
# Terminal for R2
java -cp bin com.foodordering.client.rider.RiderClient R2

# Terminal for R3
java -cp bin com.foodordering.client.rider.RiderClient R3
```

Now you have three active delivery riders competing for orders.

---

# 16. Synchronize physical clocks

In each rider console, select option `1` (`Synchronize Physical Clock`).

Observe the Cristian's algorithm diagnostics printed by `PhysicalClockSynchronizer`:
```text
--- [CRISTIAN PHYSICAL CLOCK SYNCHRONIZATION] ---
Client Request Sent (t0)     : 19:41:50.265 (1789999910265 ms)
Server Physical Timestamp (Ts): 19:41:50.265 (1789999910265 ms)
Client Response Recv (t1)    : 19:41:50.265 (1789999910265 ms)
Round Trip Time (RTT)        : 0 ms
Estimated One-Way Delay      : 0 ms
Calculated Clock Offset      : +0 ms
Current Synchronized Time    : 19:41:50.265
(Accuracy bounded within +/- 0.0 ms, assuming symmetric latency)
-------------------------------------------------
```
The client stores this offset and applies it to every subsequent action timestamp.

---

# 17. Demonstrate rider acceptance

In Rider R1's terminal:
1. Select option `2` (`Accept Delivery Opportunity`).
2. Order ID: `1001`.
3. Network delay: `0`.

In Rider R2's terminal:
1. Select option `2`.
2. Order ID: `1001`.
3. Network delay: `0`.

The server records both acceptances in the order's `riderAcceptances` map:
- `R1 -> physicalTimestamp, serverReceiptTimestamp, lamportTimestamp`
- `R2 -> physicalTimestamp, serverReceiptTimestamp, lamportTimestamp`

---

# 18. Verify physical clock behavior

Check the diagnostics logged on Node 1:
```text
[RIDER ACCEPTED]
Order=#1001
Source=R1
PhysicalTime=1789999808321
Lamport=6
```
The server stores the rider's **physical action timestamp** (when the rider tapped the screen) separately from the **server receipt timestamp** (when the RMI message reached the machine).

> **Viva Note**: The physical clock estimates the time the action occurred on the client; it does *not* prove network packet arrival order.

---

# 19. Finalize assignment

In any rider terminal (or via CustomerClient):
Select option `3` (`Finalize Assignment`) and enter Order ID `1001`.

Expected result:
```text
Result: Order #1001 successfully ASSIGNED to Rider R1 (Physical action time: 1789999808321, Server received: 1789999808383)
```
The server sorts bids by earliest synchronized physical timestamp, breaking ties by receipt timestamp, and transitions the order status to `ASSIGNED`.

---

# 20. Test Lamport clock

Check the order event history using option `6` in `RiderClient` (or option `4` in `CustomerClient`):

```text
--- Distributed Event Log (Ordered by Lamport Logical Clock) ---
  1. [ORDER_CREATED] Order=#1001 | Source=Alice | Lamport=4
  2. [RIDER_ACCEPTED] Order=#1001 | Source=R1 | Lamport=6 (Physical Time: 1789999808321)
  3. [RIDER_ACCEPTED] Order=#1001 | Source=R2 | Lamport=8 (Physical Time: 1789999808384)
  4. [ASSIGNED] Order=#1001 | Source=OrderNode-1 | Lamport=11 (Assigned to: R1)
```
Notice how each step strictly advances the Lamport logical clock according to the rule:
$$L_{\text{new}} = \max(L_{\text{local}}, L_{\text{incoming}}) + 1$$

---

# 21. Test replication

Look at the logs across all three nodes:

### Node 1 log:
```text
[N1 -> N2] replication event #1001 (type=ASSIGNED, ver=5, eventId=EVT-1-5)
[N2 -> N1] ACK (applied successfully at version 5)
[N1 -> N3] replication event #1001 (type=ASSIGNED, ver=5, eventId=EVT-1-5)
[N3 -> N1] ACK (applied successfully at version 5)
```

### Node 2 and Node 3 logs:
```text
[N2] event applied (Order=#1001, Type=ASSIGNED, Ver=5)
[N3] event applied (Order=#1001, Type=ASSIGNED, Ver=5)
```
Order 1001 is now replicated in memory across all three nodes.

---

# 22. Verify local persistence

Inspect the persistent log files stored on disk:

```bash
cat data/node1/events.log
cat data/node2/events.log
cat data/node3/events.log
```

Expected content in all three files:
```text
1|EVT-1-1|1001|ORDER_CREATED|1|4|NULL|Alice|Pizza Palace|Deluxe Pizza|2|NULL|...
2|EVT-1-2|1001|RIDER_ACCEPTED|1|6|1789999808321|Alice|Pizza Palace|Deluxe Pizza|2|R1|...
3|EVT-1-3|1001|RIDER_ACCEPTED|1|8|1789999808384|Alice|Pizza Palace|Deluxe Pizza|2|R2|...
4|EVT-1-4|1001|RIDER_ACCEPTED|1|10|1789999808389|Alice|Pizza Palace|Deluxe Pizza|2|R3|...
5|EVT-1-5|1001|ASSIGNED|1|11|NULL|Alice|Pizza Palace|Deluxe Pizza|2|R1|...
```
All three files contain identical, durable event records flushed with `sync()`.

---

# 23. Test duplicate replication

In `com.foodordering.demo.ReplicationTest` (run via `java -cp bin com.foodordering.demo.ReplicationTest`), Test 3 explicitly verifies that sending the exact same event ID twice causes the second attempt to be safely dropped with `"Duplicate ignored"` and no state corruption.

---

# 24. Test backup restart

Stop Node 2:
```bash
docker compose stop node2
```

Start Node 2 again:
```bash
docker compose start node2
```

Inspect Node 2 logs:
```bash
docker compose logs node2
```
Expected output:
```text
[N2] loading EventLog from data/node2/events.log...
[N2] rebuilding state: 5 event(s) applied, 1 active order(s) reconstructed in OrderStore.
synchronizing missing events from rmi://localhost:1101/OrderNode1...
[CATCH-UP] Local version is 5. Primary reported 0 missing event(s).
rejoining as BACKUP.
```
Node 2 restored its state from disk and rejoined as BACKUP.

---

# 25. Test PRIMARY failure

This is the central high-availability experiment.

Make sure Order 1001 exists on all nodes, then stop the PRIMARY node:

```bash
docker compose stop node1
```
*(Do NOT stop Node 2 or Node 3!)*

---

# 26. Watch heartbeat failure detection

Follow the logs of Node 2 and Node 3:
```bash
docker compose logs -f node2 node3
```

Expected sequence:
```text
[HEARTBEAT WARN] Node 2 missed heartbeat from PRIMARY Node 1 (1/2)
[HEARTBEAT WARN] Node 2 missed heartbeat from PRIMARY Node 1 (2/2)
[HEARTBEAT TIMEOUT] Node 2 confirmed PRIMARY Node 1 is DEAD!
[N2] Primary Node 1 failure detected by HeartbeatMonitor. Initiating Bully Election...
```

---

# 27. Watch Bully election

In the logs of Node 2 and Node 3, watch the election messages:

```text
[BULLY ELECTION] Node 2 initiated Leader Election!
[BULLY ELECTION] Node 2 challenging 1 higher node(s)...
[BULLY ELECTION] [N2 -> N3] Sending ELECTION message...
[BULLY ELECTION] Node 3 received ELECTION from Node 2 (My ID=3, Sender ID=2)
[BULLY ELECTION] Node 3 has higher ID than Node 2. Sending OK and taking over election.
[BULLY ELECTION] [N2 <- N3] Received OK answer from higher node.
[BULLY ELECTION] Node 2 yielding to higher node(s). Waiting for COORDINATOR announcement...

[BULLY ELECTION] Node 3 WON the election!
Transitioning role to PRIMARY / COORDINATOR.
[ROLE CHANGE] Node 3 changed role from BACKUP to PRIMARY
[BULLY ELECTION] [N3 -> N2] Announcing COORDINATOR...
[BULLY ELECTION] Node 2 acknowledged new COORDINATOR: Node 3
[BULLY ELECTION] [N3 -> GATEWAY] Notifying Gateway of new PRIMARY...
```
Node 3 is now PRIMARY!

---

# 28. Verify Gateway changed its primary

Check Gateway logs:
```bash
docker compose logs gateway
```

Expected output:
```text
==================================================
  [GATEWAY ROUTING UPDATE] Primary Node updated -> Node 3
==================================================
```
The Gateway routing table now points directly to Node 3.

---

# 29. Create a new order after failover

With Node 1 still offline, place a new order using `CustomerClient`:

```bash
java -cp bin com.foodordering.client.customer.CustomerClient place Bob "Burger Barn" "Mega Burger" 1
```

Check Gateway and Node 3 logs:
```text
[GATEWAY ROUTING] Routing placeOrder(customer='Bob', item='Mega Burger') -> PRIMARY Node 3
[N3] local state updated for Order #3001 (ORDER_CREATED)
[N3 -> N2] replication event #3001 (type=ORDER_CREATED, ver=6, eventId=EVT-3-6)
[N2 -> N3] ACK (applied successfully at version 6)
[ORDER CREATED] Order=#3001
```
Order 3001 was successfully created on Node 3 and replicated to Node 2!

---

# 30. Retrieve the old order after failover

Query Order `#1001` (which was placed on Node 1 *before* it died):

```bash
java -cp bin com.foodordering.client.customer.CustomerClient status 1001
```

Expected output:
```text
Order #1001: Pizza Palace - Deluxe Pizza (Qty: 2)
Status: ASSIGNED | Assigned Rider: R1
```
The order is retrieved seamlessly from Node 3 because it was replicated before the crash.

---

# 31. Restart Node 1

Start Node 1 again:

```bash
docker compose start node1
```

Check Node 1 logs (`docker compose logs node1`):
```text
[N1] loading EventLog from data/node1/events.log...
[N1] rebuilding state: 5 event(s) applied, 1 active order(s) reconstructed in OrderStore.
synchronizing missing events from rmi://localhost:1103/OrderNode3 (Node 3)...
[CATCH-UP] Rejoining as BACKUP. Local version is 5. Contacting PRIMARY at rmi://localhost:1103/OrderNode3...
[CATCH-UP] Primary reported 1 missing event(s) to apply.
[CATCH-UP] Successfully synchronized and persisted 1 missing event(s). Local version now: 6
rejoining as BACKUP.
[HEARTBEAT] Node 1 started heartbeat monitor targeting PRIMARY Node 3 (interval=500ms)
```

Node 1:
1. Replayed its local log (events 1–5).
2. Found that Node 3 is now PRIMARY.
3. Downloaded missing Event 6 (Order 3001).
4. Persisted Event 6 to disk.
5. Rejoined strictly as **BACKUP**.

---

# 32. Verify the recovered node

Inspect Node 1's disk log:
```bash
cat data/node1/events.log
```
Notice that Event 6 (`EVT-3-6`, Order 3001) is now appended to Node 1's log, matching Node 3.
Both Order `#1001` and Order `#3001` are present and consistent across all three nodes!

---

# 33. Full test order summary

For a comprehensive evaluation or viva demo, follow this exact sequence:

```text
 1. docker compose build
 2. docker compose up -d gateway node1 node2 node3
 3. docker compose ps
 4. docker compose logs gateway
 5. docker compose logs node1
 6. docker compose logs node2
 7. docker compose logs node3
 8. Start CustomerClient (place Order 1001)
 9. Start Rider R1 and R2
10. Synchronize rider physical clocks via Cristian's algorithm
11. R1 and R2 accept Order 1001
12. Finalize assignment (awards to R1 based on earliest physical action time)
13. Inspect Lamport logical clock timestamps on DistributedEvents
14. Inspect replication ACKs on Node 2 and Node 3
15. Inspect data/node*/events.log on disk
16. Stop node2 (docker compose stop node2)
17. Restart node2 (docker compose start node2) and verify catch-up
18. Stop PRIMARY node1 (docker compose stop node1)
19. Watch heartbeat timeout on node2 and node3
20. Watch Bully leader election promote Node 3
21. Verify Gateway updates routing to Node 3
22. Place Order 3001 through Gateway (serviced by new Primary Node 3)
23. Retrieve old Order 1001 through Gateway (serviced by new Primary Node 3)
24. Restart node1 (docker compose start node1)
25. Verify Node 1 replays local log, pulls Order 3001 from Node 3, and rejoins as BACKUP
```

---

# 34. Useful Docker commands

| Action | Command |
| :--- | :--- |
| **Check container status** | `docker compose ps` |
| **Start all 4 servers** | `docker compose up -d gateway node1 node2 node3` |
| **Stop all servers** | `docker compose down` |
| **Stop one node (e.g. Node 1)** | `docker compose stop node1` |
| **Start one node (e.g. Node 1)** | `docker compose start node1` |
| **Restart one node** | `docker compose restart node1` |
| **View live logs of a node** | `docker compose logs -f node1` |
| **View all logs together** | `docker compose logs -f` |
| **Rebuild from scratch** | `docker compose build --no-cache` |
| **Open shell inside a container** | `docker compose exec node1 sh` |

---

# 35. Troubleshooting Guide

### 1. Customer cannot connect
- **Symptom**: `java.rmi.NotBoundException: GatewayService` or `Connection refused`.
- **Fix**: Check `docker compose ps` to ensure `gateway-server` is running. Check `docker compose logs gateway`.

### 2. Gateway cannot contact primary
- **Symptom**: `[GATEWAY ERROR] Primary Node 1 failed...`
- **Fix**: Check `docker compose logs node1`. Ensure port 1101 is not occupied by a lingering host process (`lsof -i :1101`).

### 3. Riders cannot synchronize clock
- **Symptom**: `RemoteException` during `gateway.getServerTime()`.
- **Fix**: Verify Gateway connectivity. Ensure network connection to port 1099 is open.

### 4. Replication fails
- **Symptom**: `[REPLICATION WARNING] N2 unavailable for replication`.
- **Fix**: Check `docker compose logs node2`. Verify port 1102 is reachable from the host.

### 5. Election does not happen
- **Symptom**: Node 1 is stopped, but no election logs appear on Node 2 or Node 3.
- **Fix**: Wait at least 1500ms for the 2 heartbeat timeouts to expire. Check `docker compose logs node2`.

### 6. Gateway still sends requests to dead Node 1
- **Symptom**: Gateway throws connection error rather than routing to Node 3.
- **Fix**: Ensure Node 3 won the election and invoked `gateway.notifyLeaderChanged("3")`. Check `docker compose logs gateway`.

---

# 36. Final expected architecture during the complete demo

```text
Before Failure:
              Gateway (1099)
                    |
                 Node 1 (1101)
                 PRIMARY
                 /     \
                /       \
          Node 2 (1102) Node 3 (1103)
          BACKUP        BACKUP

After Node 1 Failure:
              Gateway (1099)
                    |
                 Node 3 (1103)
                 PRIMARY
                 /     \
                /       \
          Node 1 (1101) Node 2 (1102)
          [DEAD]        BACKUP

After Node 1 Recovery:
              Gateway (1099)
                    |
                 Node 3 (1103)
                 PRIMARY
                 /     \
                /       \
          Node 1 (1101) Node 2 (1102)
          BACKUP        BACKUP
```

---

# 37. Key Viva Voce Questions

1. **Why do we have a Gateway?**
   Clients connect to a single address (`1099`) and do not need to know internal node addresses or which node is primary.
2. **Why do we need three Order Nodes?**
   To provide fault tolerance: if one node crashes, the other two continue operating without data loss.
3. **Why is only one node primary?**
   To prevent split-brain conflicts and provide a single linear event order for writes.
4. **Why is the Order map concurrent?**
   `ConcurrentHashMap` permits non-blocking concurrent reads and lookups across multiple RMI threads.
5. **Why is Order itself synchronized?**
   `ConcurrentHashMap` only protects map entry operations; methods inside `Order` must be synchronized to prevent race conditions during concurrent rider bids.
6. **Why use ExecutorService?**
   To execute order preparation tasks in a bounded thread pool (size 4) rather than creating unbounded OS threads.
7. **Why synchronize rider clocks?**
   To fairly evaluate physical action timestamps across drifting rider phones when bidding on orders.
8. **Why do we also have Lamport clocks?**
   To establish a consistent causal sequence of events ($a \to b$) across independent distributed processes.
9. **What is the difference between physical and logical time?**
   Physical time estimates real-world clock time; logical time measures causal steps and event dependencies.
10. **What exactly is replicated?**
    High-level domain `ReplicationEvent` objects carrying order state, Lamport timestamps, and sequential version numbers.
11. **Where is the persistent data?**
    In local append-only text logs (`data/node<id>/events.log`) flushed with `sync()`.
12. **Why don't we need PostgreSQL for this experiment?**
    The project explores application-level state machine replication and recovery using in-memory collections and disk event sourcing.
13. **How do heartbeats detect failure?**
    Backups ping the primary every 500ms; 2 consecutive missed pings triggers a failure declaration.
14. **How does Bully election select a new primary?**
    A challenging node pings higher nodes; if no higher node replies, the highest live ID declares itself PRIMARY.
15. **How does Gateway learn the new primary?**
    The elected coordinator calls `gateway.notifyLeaderChanged(newLeaderId)` and the Gateway updates its routing table.
16. **What happens to existing orders after primary failure?**
    They remain safe in memory and on disk on the surviving nodes because they were replicated prior to the crash.
17. **What happens when the old primary comes back?**
    It replays its local log, pulls missing events from the current primary, and rejoins strictly as a BACKUP.
18. **What happens if a replication message is received twice?**
    `OrderStore` checks `appliedEvents`; duplicates are safely ignored as no-ops.
19. **What happens if one backup is unavailable?**
    The primary logs a warning, continues serving clients, and the offline node catches up when it returns.
20. **What part of the system is simplified compared with production?**
    Single Gateway instance (production uses redundant VIPs); Bully election assumes no network partitions (production uses Raft/Paxos quorums).

---

# 38. Final Rule for Evaluations

- **Do NOT say**: *"We have PostgreSQL replication."*
  **Say**: *"We replicate application state events from the primary Order Node to backup Order Nodes over Java RMI and persist events locally."*
- **Do NOT say**: *"The Lamport clock gives real-world time."*
  **Say**: *"The Lamport clock provides logical causal ordering of distributed events."*
- **Do NOT say**: *"Physical clock synchronization proves who reached the server first."*
  **Say**: *"It estimates the physical time the rider performed the action on their device."*
- **Do NOT say**: *"Node 3 becomes primary because it has better hardware."*
  **Say**: *"The Bully election algorithm deterministically elects the live node with the highest numeric ID."*
