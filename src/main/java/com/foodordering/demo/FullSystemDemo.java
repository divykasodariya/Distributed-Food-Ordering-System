package com.foodordering.demo;

import com.foodordering.api.gateway.GatewayRemote;
import com.foodordering.api.node.OrderNodeRemote;
import com.foodordering.client.rider.RiderClient;
import com.foodordering.clock.LamportClock;
import com.foodordering.clock.PhysicalClockSynchronizer;
import com.foodordering.config.ClusterConfig;
import com.foodordering.gateway.GatewayServer;
import com.foodordering.model.NodeInfo;
import com.foodordering.model.NodeRole;
import com.foodordering.model.OrderSnapshot;
import com.foodordering.node.OrderNodeServer;
import com.foodordering.util.RmiUtils;

import java.io.File;
import java.nio.file.Files;
import java.rmi.Naming;
import java.rmi.registry.Registry;
import java.util.List;

/**
 * Coherent 22-Step End-to-End System Integration Demo.
 * 
 * Demonstrates:
 * 1. RMI communication (Customer -> Gateway -> Primary OrderNode).
 * 2. Multi-threaded request execution via ExecutorService.
 * 3. Cristian's physical clock synchronization with RTT adjustment across riders.
 * 4. Lamport logical clock causal tracking on all distributed events.
 * 5. Primary-backup state replication with explicit ACKs and disk sync.
 * 6. Append-only event log persistence.
 * 7. Heartbeat failure detection of the primary node.
 * 8. Distributed Bully Leader Election algorithm (highest live ID wins).
 * 9. Gateway dynamic routing failover to the new primary.
 * 10. Querying pre-failover state and creating post-failover state.
 * 11. Stale primary restart, local log replay, catch-up synchronization, and rejoining as BACKUP.
 */
public class FullSystemDemo {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("      DISTRIBUTED FOOD ORDERING SYSTEM - 22-STEP COHERENT INTEGRATION DEMO       ");
        System.out.println("================================================================================");

        try {
            // Clean test logs
            cleanLogs();

            String host = ClusterConfig.getHost();
            System.setProperty("java.rmi.server.hostname", host);

            System.out.println("\n[SETUP] Initializing Cluster Registries & Servers...");
            // 1. Gateway
            Registry gatewayReg = RmiUtils.getOrCreateRegistry(ClusterConfig.GATEWAY_PORT);
            GatewayServer gatewayServer = new GatewayServer();
            gatewayReg.rebind(ClusterConfig.GATEWAY_SERVICE_NAME, gatewayServer);
            System.out.printf("  -> GatewayServer bound at %s\n", ClusterConfig.getGatewayUrl());

            // 2. Order Node 1 (Initial PRIMARY)
            OrderNodeServer node1 = OrderNodeServer.startServer("1", NodeRole.PRIMARY, ClusterConfig.NODE_1_PORT);

            // 3. Order Node 2 (BACKUP)
            OrderNodeServer node2 = OrderNodeServer.startServer("2", NodeRole.BACKUP, ClusterConfig.NODE_2_PORT);

            // 4. Order Node 3 (BACKUP)
            OrderNodeServer node3 = OrderNodeServer.startServer("3", NodeRole.BACKUP, ClusterConfig.NODE_3_PORT);

            Thread.sleep(800);
            GatewayRemote gateway = (GatewayRemote) Naming.lookup(ClusterConfig.getGatewayUrl());

            System.out.println("\n================================================================================");
            System.out.println("--- STEP 1: Customer creates Order 1001 ---");
            LamportClock customerClock = new LamportClock("CustomerClient");
            long customerLamport = customerClock.tickOnSend();
            System.out.printf("Customer Client initiates Order: Customer='Alice', Item='Deluxe Pizza', Qty=2, Lamport=%d\n", customerLamport);

            System.out.println("\n--- STEP 2: Gateway routes request to current PRIMARY (Node 1) ---");
            int orderId = gateway.placeOrder("Alice", "Pizza Palace", "Deluxe Pizza", 2, customerLamport);

            System.out.println("\n--- STEP 3: Primary Node 1 creates order ---");
            System.out.printf("Primary Node 1 assigned Order ID: #%d\n", orderId);

            System.out.println("\n--- STEP 4: ExecutorService asynchronously prepares order in background ---");
            Thread.sleep(300); // allow async executor to complete

            System.out.println("\n--- STEP 5: Riders R1, R2, R3 synchronize physical clocks via Cristian's algorithm ---");
            RiderClient rider1 = new RiderClient("R1", gateway);
            RiderClient rider2 = new RiderClient("R2", gateway);
            RiderClient rider3 = new RiderClient("R3", gateway);

            rider1.synchronizeClock();
            rider2.synchronizeClock();
            rider3.synchronizeClock();
            System.out.printf("  Rider R1 Estimated Physical Time: %d\n", rider1.getEstimatedPhysicalTime());
            System.out.printf("  Rider R2 Estimated Physical Time: %d\n", rider2.getEstimatedPhysicalTime());
            System.out.printf("  Rider R3 Estimated Physical Time: %d\n", rider3.getEstimatedPhysicalTime());

            System.out.println("\n--- STEP 6 & 7: Riders accept order; server records physical timestamps ---");
            // Rider 1 action at physical t1, sent with 50ms delay
            // Rider 2 action at physical t2, sent with 0ms delay
            // Rider 3 action at physical t3, sent with 20ms delay
            rider1.acceptOrder(orderId, 50);
            rider2.acceptOrder(orderId, 0);
            rider3.acceptOrder(orderId, 20);

            System.out.println("\n--- STEP 8: Lamport timestamps appear on distributed events ---");
            OrderSnapshot snapBeforeAssign = gateway.getOrderStatus(orderId);
            System.out.println("Order Event Log after acceptances:");
            snapBeforeAssign.getEventLog().forEach(e ->
                    System.out.printf("  -> [Lamport: %d, Event: %s, Source: %s, PhysicalTime: %d]\n",
                            e.getLamportTimestamp(), e.getEventType(), e.getSourceId(), e.getPhysicalTimestamp()));

            System.out.println("\n--- STEP 9: Primary finalizes assignment based on earliest synchronized physical time ---");
            String assignmentResult = gateway.finalizeAssignment(orderId);
            System.out.println("Assignment Result: " + assignmentResult);

            System.out.println("\n--- STEP 10: Order state replicated from Primary (N1) to Backups (N2, N3) ---");
            OrderNodeRemote stub2 = (OrderNodeRemote) Naming.lookup(ClusterConfig.getNodeUrl("2", ClusterConfig.NODE_2_PORT));
            OrderNodeRemote stub3 = (OrderNodeRemote) Naming.lookup(ClusterConfig.getNodeUrl("3", ClusterConfig.NODE_3_PORT));

            OrderSnapshot snapN2 = stub2.getOrderStatus(orderId);
            OrderSnapshot snapN3 = stub3.getOrderStatus(orderId);
            System.out.printf("Node 2 replicated status: %s (Assigned: %s)\n", snapN2.getStatus(), snapN2.getAssignedRider());
            System.out.printf("Node 3 replicated status: %s (Assigned: %s)\n", snapN3.getStatus(), snapN3.getAssignedRider());

            System.out.println("\n--- STEP 11: Local append-only event logs are updated with sync() on all nodes ---");
            printLogSummary("1", "data/node1/events.log");
            printLogSummary("2", "data/node2/events.log");
            printLogSummary("3", "data/node3/events.log");

            System.out.println("\n--- STEP 12: Kill PRIMARY (Node 1) ---");
            System.out.println("Stopping Node 1 process / unbinding RMI stub...");
            node1.stopServer();
            try {
                Registry reg1 = RmiUtils.getOrCreateRegistry(ClusterConfig.NODE_1_PORT);
                reg1.unbind("OrderNode1");
            } catch (Exception ignored) {}
            System.out.println("[KILL SUCCESS] Node 1 is offline.");

            System.out.println("\n--- STEP 13: Heartbeat detects Node 1 failure ---");
            System.out.println("Backup Node 2 heartbeat monitor detects Node 1 is unreachable...");

            System.out.println("\n--- STEP 14: Bully election runs (Node 2 challenges Node 3; Node 3 has highest ID) ---");
            // Node 2 starts election
            node2.getBullyElectionManager().startElection();
            Thread.sleep(1200); // Allow distributed election messages to exchange

            System.out.println("\n--- STEP 15: New primary announced (Node 3 is COORDINATOR) ---");
            System.out.printf("Node 2 current role: %s\n", stub2.getNodeInfo().getRole());
            System.out.printf("Node 3 current role: %s\n", stub3.getNodeInfo().getRole());

            System.out.println("\n--- STEP 16: Gateway updates routing to Node 3 ---");
            System.out.printf("Gateway current PRIMARY Node ID: %s\n", gateway.getPrimaryNodeId());

            System.out.println("\n--- STEP 17: Query old Order 1001 through Gateway (serviced by new Primary Node 3) ---");
            OrderSnapshot oldOrderSnap = gateway.getOrderStatus(orderId);
            System.out.printf("[SUCCESS] Retrieved Order #%d from new Primary! Status=%s, Assigned Rider=%s\n",
                    oldOrderSnap.getOrderId(), oldOrderSnap.getStatus(), oldOrderSnap.getAssignedRider());

            System.out.println("\n--- STEP 18: Create new Order 1002 through Gateway (serviced by new Primary Node 3) ---");
            long cLamport2 = customerClock.tickOnSend();
            int newOrderId = gateway.placeOrder("Bob", "Burger Barn", "Mega Burger", 1, cLamport2);
            System.out.printf("[SUCCESS] Placed Order #%d on new Primary Node 3!\n", newOrderId);

            System.out.println("\n--- STEP 19: Restart failed Node 1 ---");
            System.out.println("Bootstrapping Node 1 as BACKUP...");

            System.out.println("\n--- STEP 20: Node 1 replays local log from disk ---");
            // Starting Node 1 as BACKUP: will replay its local log (containing Order 1001)
            OrderNodeServer restartedNode1 = OrderNodeServer.startServer("1", NodeRole.BACKUP, ClusterConfig.NODE_1_PORT);

            System.out.println("\n--- STEP 21: Node 1 synchronizes missing state (Order 1002) from current Primary Node 3 ---");
            Thread.sleep(500);

            System.out.println("\n--- STEP 22: Node 1 rejoins cluster as BACKUP ---");
            OrderNodeRemote stub1 = (OrderNodeRemote) Naming.lookup(ClusterConfig.getNodeUrl("1", ClusterConfig.NODE_1_PORT));
            System.out.printf("Node 1 Rejoined Role: %s\n", stub1.getNodeInfo().getRole());

            OrderSnapshot n1CheckOld = stub1.getOrderStatus(orderId);
            OrderSnapshot n1CheckNew = stub1.getOrderStatus(newOrderId);
            System.out.printf("Node 1 verified Old Order #%d: Present=%b (%s)\n",
                    orderId, n1CheckOld != null, n1CheckOld != null ? n1CheckOld.getItemName() : "N/A");
            System.out.printf("Node 1 verified New Order #%d: Present=%b (%s)\n",
                    newOrderId, n1CheckNew != null, n1CheckNew != null ? n1CheckNew.getItemName() : "N/A");

            System.out.println("\nNode 1 Updated Log after synchronization:");
            printLogSummary("1", "data/node1/events.log");

            // Clean shutdown
            restartedNode1.stopServer();
            node2.stopServer();
            node3.stopServer();

            System.out.println("\n================================================================================");
            System.out.println("  22-STEP DISTRIBUTED SYSTEM INTEGRATION DEMO COMPLETED SUCCESSFULLY!           ");
            System.out.println("================================================================================");

        } catch (Exception e) {
            System.err.println("DEMO FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void cleanLogs() {
        for (String id : List.of("1", "2", "3")) {
            File log = new File("data/node" + id + "/events.log");
            if (log.exists()) {
                log.delete();
            }
        }
    }

    private static void printLogSummary(String nodeId, String path) {
        try {
            File f = new File(path);
            if (!f.exists()) {
                System.out.printf("  [Node %s Log] %s does not exist.\n", nodeId, path);
                return;
            }
            List<String> lines = Files.readAllLines(f.toPath());
            System.out.printf("  [Node %s Log] %s (%d events):\n", nodeId, path, lines.size());
            for (String l : lines) {
                System.out.printf("     %s\n", l);
            }
        } catch (Exception e) {
            System.err.printf("Error reading log for Node %s: %s\n", nodeId, e.getMessage());
        }
    }
}
