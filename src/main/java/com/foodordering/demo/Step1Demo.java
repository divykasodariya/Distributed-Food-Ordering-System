package com.foodordering.demo;

import com.foodordering.api.gateway.GatewayRemote;
import com.foodordering.client.rider.RiderClient;
import com.foodordering.config.ClusterConfig;
import com.foodordering.model.NodeInfo;
import com.foodordering.model.OrderSnapshot;

import java.rmi.Naming;

/**
 * Automated end-to-end demonstration for Step 1:
 * - Verifies 4 Server JVMs (Gateway + Node 1 PRIMARY + Node 2 BACKUP + Node 3 BACKUP)
 * - Customer places pizza order via Gateway -> Primary (Node 1)
 * - Concurrent Riders (R1, R2, R3) synchronize physical clocks via Cristian's algorithm
 * - Demonstrates physical action timestamp ordering vs. network arrival ordering
 * - Demonstrates thread-safe state transitions and assignment on Primary Node
 */
public class Step1Demo {

    public static void main(String[] args) {
        try {
            System.out.println("===============================================================");
            System.out.println("  STEP 1 END-TO-END VERIFICATION & DEMO");
            System.out.println("===============================================================");

            String gatewayUrl = ClusterConfig.getGatewayUrl();
            GatewayRemote gateway = (GatewayRemote) Naming.lookup(gatewayUrl);

            // 1. Check Node Topologies
            System.out.println("\n[1] Verifying 4 Server Processes Topology from Gateway:");
            for (NodeInfo info : gateway.getNodeStatuses()) {
                System.out.println("    -> " + info);
            }

            // 2. Customer places order
            System.out.println("\n[2] Customer placing Pizza order via Gateway -> Primary Order Node:");
            int orderId = gateway.placeOrder("Customer A", "Restaurant A", "Pizza", 1, 1L);
            System.out.printf("    Order Placed! Received Distributed Order ID: #%d\n", orderId);

            OrderSnapshot initialSnapshot = gateway.getOrderStatus(orderId);
            System.out.println("    Initial Status: " + initialSnapshot.getStatus());

            // 3. Concurrent Rider Actions demonstrating Physical Action Time vs Network Arrival Time
            System.out.println("\n[3] Simulating 3 Concurrent Riders (R1, R2, R3) accepting order opportunity:");
            System.out.println("    - Rider R1: performs action at T0, but has 400ms network delay");
            System.out.println("    - Rider R2: performs action at T0 + 150ms, with 0ms network delay (arrives at server FIRST)");
            System.out.println("    - Rider R3: performs action at T0 + 250ms, with 100ms network delay");

            Thread tR1 = new Thread(() -> {
                try {
                    RiderClient r1 = new RiderClient("Rider-R1", gateway);
                    r1.synchronizeClock();
                    r1.acceptOrder(orderId, 400); // 400ms delay -> arrives last at server
                } catch (Exception e) { e.printStackTrace(); }
            });

            Thread tR2 = new Thread(() -> {
                try {
                    Thread.sleep(150); // Action performed 150ms later
                    RiderClient r2 = new RiderClient("Rider-R2", gateway);
                    r2.synchronizeClock();
                    r2.acceptOrder(orderId, 0); // 0ms delay -> arrives first at server
                } catch (Exception e) { e.printStackTrace(); }
            });

            Thread tR3 = new Thread(() -> {
                try {
                    Thread.sleep(250); // Action performed 250ms later
                    RiderClient r3 = new RiderClient("Rider-R3", gateway);
                    r3.synchronizeClock();
                    r3.acceptOrder(orderId, 100);
                } catch (Exception e) { e.printStackTrace(); }
            });

            tR1.start();
            tR2.start();
            tR3.start();

            tR1.join();
            tR2.join();
            tR3.join();

            // 4. Inspect Order Snapshot with all bids
            System.out.println("\n[4] Inspecting Order Snapshot after concurrent acceptances:");
            OrderSnapshot bidsSnapshot = gateway.getOrderStatus(orderId);
            System.out.println(bidsSnapshot);

            // 5. Finalize assignment
            System.out.println("\n[5] Finalizing assignment on Primary Order Node:");
            String assignmentResult = gateway.finalizeAssignment(orderId);
            System.out.println("    " + assignmentResult);

            OrderSnapshot assignedSnapshot = gateway.getOrderStatus(orderId);
            System.out.println("    Assigned Winner: " + assignedSnapshot.getAssignedRider());
            System.out.println("    Current Status : " + assignedSnapshot.getStatus());

            if ("Rider-R1".equals(assignedSnapshot.getAssignedRider())) {
                System.out.println("    >>> SUCCESS: Rider-R1 won based on earliest physical action time,");
                System.out.println("        even though Rider-R2 arrived earlier over the network!");
            } else {
                System.err.println("    >>> UNEXPECTED WINNER: " + assignedSnapshot.getAssignedRider());
            }

            // 6. Arrive at restaurant
            System.out.println("\n[6] Rider-R1 arriving at restaurant before deadline:");
            RiderClient winnerClient = new RiderClient("Rider-R1", gateway);
            winnerClient.synchronizeClock();
            winnerClient.arriveAtRestaurant(orderId);

            OrderSnapshot finalSnapshot = gateway.getOrderStatus(orderId);
            System.out.println("\n[7] Final Order State:");
            System.out.println(finalSnapshot);

            System.out.println("\n===============================================================");
            System.out.println("  STEP 1 END-TO-END DEMO COMPLETED SUCCESSFULLY!");
            System.out.println("===============================================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
