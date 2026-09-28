# System Demo Flow

This guide describes how to demonstrate the distributed system features, such as fault tolerance, replication, and leader election.

## 1. Startup
Start the cluster using Docker Compose:
```bash
docker compose up -d gateway node1 node2 node3
```

Check the logs to verify everything is running:
```bash
docker compose logs gateway
# Expected: Gateway Server Running, routing to Node 1 (PRIMARY)
```

## 2. Order Placement
Start the Customer Client interactively:
```bash
docker compose run --rm customer
```
- Browse the menu and place a new order.
- The Customer Client communicates with the Gateway, which forwards the request to Node 1.
- Node 1 assigns an Order ID (e.g., `#1001`), saves it to disk, and replicates it to Node 2 and Node 3.

## 3. Rider Bidding & Clocks
Open two new terminals and start two Rider Clients:
```bash
docker compose run --rm rider java -cp bin com.foodordering.client.rider.RiderClient R1
docker compose run --rm rider java -cp bin com.foodordering.client.rider.RiderClient R2
```
- In both Rider terminals, choose **Synchronize Physical Clock**. This demonstrates Cristian's algorithm for adjusting the rider's local clock offset.
- Both riders accept the delivery opportunity for order `#1001`.
- The Primary server (Node 1) finalizes the assignment based on the earliest synchronized physical timestamp.

## 4. Lamport Clock Validation
Check the event history in the Customer or Rider client to observe Lamport Logical Clock behavior.
Every event (`ORDER_CREATED`, `RIDER_ACCEPTED`, `ASSIGNED`) strictly advances the Lamport clock counter, establishing causal order.

## 5. Failover & Bully Election
Kill the Primary node (Node 1) to demonstrate fault tolerance:
```bash
docker compose stop node1
```
Monitor the logs of the remaining nodes:
```bash
docker compose logs -f node2 node3
```
- **Heartbeat Detection**: Node 2 and 3 notice missed heartbeats from Node 1.
- **Bully Election**: Node 2 challenges Node 3. Node 3 (highest ID) wins and announces itself as the new PRIMARY.
- **Gateway Update**: The Gateway automatically redirects traffic to Node 3.

## 6. Post-Failover Validation
Place another order using the Customer client. The order will be successfully handled by the new Primary (Node 3) and assigned an ID starting with `3` (e.g., `#3001`).

Retrieve the status of the first order (`#1001`). It is seamlessly available from Node 3, proving that replication was successful before the crash.

## 7. Node Recovery
Restart the failed Node 1:
```bash
docker compose start node1
```
Check its logs:
```bash
docker compose logs node1
```
- Node 1 rebuilds its state from `data/node1/events.log`.
- It connects to the new Primary (Node 3) to download missing events (Catch-up).
- It quietly rejoins the cluster as a BACKUP.
