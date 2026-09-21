package com.foodordering.demo;

import com.foodordering.api.gateway.GatewayRemote;
import com.foodordering.api.node.OrderNodeRemote;
import com.foodordering.client.rider.RiderClient;
import com.foodordering.config.ClusterConfig;
import com.foodordering.model.NodeInfo;
import com.foodordering.model.NodeRole;
import com.foodordering.model.OrderSnapshot;
import com.foodordering.model.ReplicationEvent;
import com.foodordering.node.OrderNodeServer;
import com.foodordering.storage.EventLog;
import com.foodordering.storage.OrderStore;
import com.foodordering.storage.ReplicationManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.rmi.Naming;
import java.util.List;

/**
 * Automated demonstration for Step 3:
 * 1. N1 PRIMARY, N2 BACKUP, N3 BACKUP replication workflow
 * 2. Multi-node event persistence and synchronous ACKs
 * 3. Inspection of data/node1, data/node2, data/node3 event logs
 * 4. Stale backup recovery upon restart (catch-up synchronization)
 */
public class Step3Demo {

    public static void main(String[] args) {
        try {
            System.out.println("===============================================================");
            System.out.println("  STEP 3 DEMO: PRIMARY-BACKUP APPLICATION REPLICATION & RECOVERY");
            System.out.println("===============================================================");

            String gatewayUrl = ClusterConfig.getGatewayUrl();
            GatewayRemote gateway = (GatewayRemote) Naming.lookup(gatewayUrl);

            // 1. Verify Topology
            System.out.println("\n[1] Current Topology from Gateway:");
            for (NodeInfo info : gateway.getNodeStatuses()) {
                System.out.println("    -> " + info);
            }

            // 2. Customer places order on Primary (Node 1)
            System.out.println("\n[2] Customer placing Order #1001 via Gateway -> PRIMARY Node 1:");
            int orderId = gateway.placeOrder("Customer Alice", "Restaurant A", "Pizza", 2, 1L);
            System.out.printf("    Order placed! Received ID: #%d\n", orderId);

            // Allow brief moment for replication broadcast
            Thread.sleep(300);

            // 3. Verify replication on all three nodes
            System.out.println("\n[3] Verifying in-memory state replication across all 3 nodes directly via RMI:");
            OrderNodeRemote node1Stub = (OrderNodeRemote) Naming.lookup(ClusterConfig.getNodeUrl("1", ClusterConfig.NODE_1_PORT));
            OrderNodeRemote node2Stub = (OrderNodeRemote) Naming.lookup(ClusterConfig.getNodeUrl("2", ClusterConfig.NODE_2_PORT));
            OrderNodeRemote node3Stub = (OrderNodeRemote) Naming.lookup(ClusterConfig.getNodeUrl("3", ClusterConfig.NODE_3_PORT));

            System.out.println("    Node 1 (PRIMARY): Order #1001 status = " + node1Stub.getOrderStatus(orderId).getStatus());
            System.out.println("    Node 2 (BACKUP) : Order #1001 status = " + node2Stub.getOrderStatus(orderId).getStatus());
            System.out.println("    Node 3 (BACKUP) : Order #1001 status = " + node3Stub.getOrderStatus(orderId).getStatus());

            // 4. Inspect persisted log files on disk
            System.out.println("\n[4] Inspecting persisted append-only event logs on disk:");
            printLogFile("data/node1/events.log", "Node 1 Log");
            printLogFile("data/node2/events.log", "Node 2 Log");
            printLogFile("data/node3/events.log", "Node 3 Log");

            // 5. Rider accepts and completes lifecycle
            System.out.println("\n[5] Executing Rider acceptance and assignment (replicated across nodes):");
            RiderClient rider = new RiderClient("Rider-R1", gateway);
            rider.synchronizeClock();
            rider.acceptOrder(orderId, 50);
            Thread.sleep(200);

            gateway.finalizeAssignment(orderId);
            Thread.sleep(200);

            System.out.println("    Node 1 Assigned Rider: " + node1Stub.getOrderStatus(orderId).getAssignedRider());
            System.out.println("    Node 2 Assigned Rider: " + node2Stub.getOrderStatus(orderId).getAssignedRider());
            System.out.println("    Node 3 Assigned Rider: " + node3Stub.getOrderStatus(orderId).getAssignedRider());

            // 6. Demonstrate Restart & Catch-Up Recovery
            System.out.println("\n[6] Demonstrating Restart & Catch-Up Recovery for Node 2:");
            System.out.println("    Current Node 2 version: " + node2Stub.getLatestLogVersion());

            // Place another order while simulating Node 2 catching up
            System.out.println("    Customer placing new Order #1002 on Node 1...");
            int order2 = gateway.placeOrder("Customer Bob", "Restaurant B", "Burger", 1, 10L);
            System.out.printf("    Order #%d created on Node 1.\n", order2);

            System.out.println("\n    Simulating Node 2 restart recovery (loading local log, rebuilding state, syncing from primary):");
            EventLog recoveryLog = new EventLog("2", null);
            OrderStore recoveryStore = new OrderStore();

            System.out.println("    [N2 Restart] loading EventLog from " + recoveryLog.getLogFile().getPath() + "...");
            List<ReplicationEvent> replayed = recoveryLog.replayAll();
            for (ReplicationEvent ev : replayed) {
                recoveryStore.apply(ev);
            }
            System.out.printf("    [N2 Restart] rebuilding state: %d event(s) applied, %d active order(s) reconstructed.\n",
                    replayed.size(), recoveryStore.size());

            System.out.println("    [N2 Restart] synchronizing missing events from PRIMARY...");
            ReplicationManager recoveryManager = new ReplicationManager("2");
            String primaryUrl = ClusterConfig.getNodeUrl(ClusterConfig.NODE_1_ID, ClusterConfig.NODE_1_PORT);
            int missingCount = recoveryManager.synchronizeFromPrimary(primaryUrl, recoveryLog, recoveryStore);

            System.out.println("    [N2 Restart] rejoining as BACKUP.");
            OrderSnapshot recoveredOrder2 = recoveryStore.getSnapshot(order2);
            System.out.printf("    Node 2 state after catch-up: Order #%d is present? %b (status: %s)\n",
                    order2,
                    recoveredOrder2 != null,
                    recoveredOrder2 != null ? recoveredOrder2.getStatus() : "N/A");

            System.out.println("\n===============================================================");
            System.out.println("  STEP 3 REPLICATION & RECOVERY DEMO COMPLETED SUCCESSFULLY!");
            System.out.println("===============================================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printLogFile(String path, String label) {
        System.out.printf("--- %s (%s) ---\n", label, path);
        File f = new File(path);
        if (!f.exists()) {
            System.out.println("    (file does not exist)");
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                System.out.println("    " + line);
                count++;
            }
            if (count == 0) System.out.println("    (empty file)");
        } catch (Exception e) {
            System.err.println("Error reading " + path + ": " + e.getMessage());
        }
    }
}
