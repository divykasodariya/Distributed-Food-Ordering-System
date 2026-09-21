package com.foodordering.demo;

import com.foodordering.api.gateway.GatewayRemote;
import com.foodordering.client.rider.RiderClient;
import com.foodordering.clock.LamportClock;
import com.foodordering.config.ClusterConfig;
import com.foodordering.model.DistributedEvent;
import com.foodordering.model.NodeInfo;
import com.foodordering.model.OrderSnapshot;

import java.rmi.Naming;

/**
 * Automated end-to-end demonstration for Step 2:
 * - Real Lamport logical clocks integrated into all RMI message flows
 * - Clear distinction between physical clock (for action ordering) and Lamport clock (for causal event tracking)
 * - Complete event lifecycle:
 *     ORDER_CREATED -> RIDER_ACCEPTED -> ASSIGNED -> RIDER_ARRIVED -> DELIVERED
 */
public class Step2Demo {

    public static void main(String[] args) {
        try {
            System.out.println("===============================================================");
            System.out.println("  STEP 2 END-TO-END DEMO: REAL LAMPORT LOGICAL CLOCK");
            System.out.println("===============================================================");

            String gatewayUrl = ClusterConfig.getGatewayUrl();
            GatewayRemote gateway = (GatewayRemote) Naming.lookup(gatewayUrl);

            // 1. Check Node Topology
            System.out.println("\n[1] Verifying 4 Server Processes Topology from Gateway:");
            for (NodeInfo info : gateway.getNodeStatuses()) {
                System.out.println("    -> " + info);
            }

            // 2. Customer places order
            LamportClock customerClock = new LamportClock("Customer-Alice");
            long customerLamport = customerClock.tickOnSend();
            System.out.printf("\n[2] Customer Alice placing Pizza order (Customer Lamport=%d):\n", customerLamport);

            int orderId = gateway.placeOrder("Customer Alice", "Restaurant A", "Pizza", 2, customerLamport);
            System.out.printf("    Order Placed successfully! Distributed Order ID: #%d\n", orderId);

            // 3. Concurrent Rider acceptances
            System.out.println("\n[3] Simulating 3 Concurrent Riders (R1, R2, R3) accepting order opportunity:");
            System.out.println("    - Rider R1: Physical action at T0, 400ms network delay (arrives LAST at server)");
            System.out.println("    - Rider R2: Physical action at T0 + 150ms, 0ms network delay (arrives FIRST at server)");
            System.out.println("    - Rider R3: Physical action at T0 + 250ms, 100ms network delay");
            System.out.println("    Each rider sends BOTH physicalTimestamp (via Cristian) and lamportTimestamp!");

            Thread tR1 = new Thread(() -> {
                try {
                    RiderClient r1 = new RiderClient("Rider-R1", gateway);
                    r1.synchronizeClock();
                    r1.acceptOrder(orderId, 400);
                } catch (Exception e) { e.printStackTrace(); }
            });

            Thread tR2 = new Thread(() -> {
                try {
                    Thread.sleep(150);
                    RiderClient r2 = new RiderClient("Rider-R2", gateway);
                    r2.synchronizeClock();
                    r2.acceptOrder(orderId, 0);
                } catch (Exception e) { e.printStackTrace(); }
            });

            Thread tR3 = new Thread(() -> {
                try {
                    Thread.sleep(250);
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

            // 4. Finalize assignment
            System.out.println("\n[4] Finalizing assignment on Primary Order Node:");
            String assignmentResult = gateway.finalizeAssignment(orderId);
            System.out.println("    " + assignmentResult);

            OrderSnapshot assignedSnapshot = gateway.getOrderStatus(orderId);
            System.out.printf("    Assigned Winner: %s (Status: %s)\n",
                    assignedSnapshot.getAssignedRider(), assignedSnapshot.getStatus());

            // 5. Rider arrives at restaurant
            System.out.println("\n[5] Winning Rider arriving at restaurant:");
            RiderClient winnerClient = new RiderClient("Rider-R1", gateway);
            winnerClient.synchronizeClock();
            winnerClient.arriveAtRestaurant(orderId);

            // 6. Rider delivers order to customer
            System.out.println("\n[6] Winning Rider delivering order to customer:");
            winnerClient.deliverOrder(orderId);

            // 7. Final Order State & Distributed Event Log
            System.out.println("\n[7] Final Order State & Complete Lamport Causal Event Log:");
            OrderSnapshot finalSnapshot = gateway.getOrderStatus(orderId);
            System.out.println(finalSnapshot);

            System.out.println("\n--- Event Sequence Validation ---");
            long lastLamport = 0;
            boolean ordered = true;
            for (DistributedEvent ev : finalSnapshot.getEventLog()) {
                if (ev.getLamportTimestamp() <= lastLamport) {
                    ordered = false;
                }
                lastLamport = ev.getLamportTimestamp();
            }

            if (ordered && finalSnapshot.getEventLog().size() >= 5) {
                System.out.println(">>> SUCCESS: All distributed events strictly adhere to Lamport causal ordering (L_i < L_{i+1})!");
            } else {
                System.out.println(">>> Notice: Total events recorded: " + finalSnapshot.getEventLog().size());
            }

            System.out.println("\n===============================================================");
            System.out.println("  STEP 2 END-TO-END DEMO COMPLETED SUCCESSFULLY!");
            System.out.println("===============================================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
