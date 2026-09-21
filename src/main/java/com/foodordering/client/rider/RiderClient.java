package com.foodordering.client.rider;

import com.foodordering.api.gateway.GatewayRemote;
import com.foodordering.clock.LamportClock;
import com.foodordering.clock.PhysicalClockSynchronizer;
import com.foodordering.config.ClusterConfig;
import com.foodordering.model.OrderSnapshot;

import java.rmi.Naming;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

/**
 * Rider client process.
 * Demonstrates:
 * 1. Physical clock synchronization using Cristian's algorithm with RTT adjustment via PhysicalClockSynchronizer.
 * 2. Logical Lamport Clock for tracking causal event order across distributed nodes.
 * 3. Clear distinction between physical action timestamp ordering vs. logical causality.
 */
public class RiderClient {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss.SSS");

    private final String riderId;
    private final GatewayRemote gateway;
    private final PhysicalClockSynchronizer physicalSynchronizer;
    private final LamportClock lamportClock;

    public RiderClient(String riderId, GatewayRemote gateway) {
        this.riderId = riderId;
        this.gateway = gateway;
        this.physicalSynchronizer = new PhysicalClockSynchronizer(gateway);
        this.lamportClock = new LamportClock("Rider-" + riderId);
    }

    public void synchronizeClock() {
        try {
            physicalSynchronizer.synchronize();
        } catch (Exception e) {
            System.err.println("Physical clock synchronization failed: " + e.getMessage());
        }
    }

    public long getEstimatedPhysicalTime() {
        return physicalSynchronizer.getEstimatedPhysicalTime();
    }

    public LamportClock getLamportClock() {
        return lamportClock;
    }

    public void acceptOrder(int orderId, long networkDelayMs) {
        try {
            if (!physicalSynchronizer.isSynchronized()) {
                System.out.println("[WARN] Physical clock not yet synchronized. Performing sync first...");
                synchronizeClock();
            }

            // 1. Capture physical action timestamp
            long physicalActionTimestamp = getEstimatedPhysicalTime();

            // 2. Advance logical Lamport clock before send
            long sendLamport = lamportClock.tickOnSend();

            System.out.printf("\n[ACTION PERFORMED] Rider %s clicked ACCEPT for Order #%d\n" +
                            "  -> Physical Action Time: %s (%d ms)\n" +
                            "  -> Logical Lamport Time: %d\n",
                    riderId, orderId, SDF.format(new Date(physicalActionTimestamp)), physicalActionTimestamp, sendLamport);

            if (networkDelayMs > 0) {
                System.out.printf("[NETWORK DELAY] Simulating %d ms transmission delay for Rider %s...\n", networkDelayMs, riderId);
                Thread.sleep(networkDelayMs);
            }

            boolean accepted = gateway.acceptDelivery(orderId, riderId, physicalActionTimestamp, sendLamport);
            if (accepted) {
                System.out.printf("[RMI SENT] Order #%d acceptance transmitted to Gateway.\n", orderId);
            } else {
                System.out.printf("[RMI REJECTED] Order #%d did not accept bid (may not be waiting or already assigned).\n", orderId);
            }
        } catch (Exception e) {
            System.err.println("Failed to accept order: " + e.getMessage());
        }
    }

    public void finalizeAssignment(int orderId) {
        try {
            System.out.println("\n[FINALIZING ASSIGNMENT] Requesting Primary node to evaluate acceptances...");
            String result = gateway.finalizeAssignment(orderId);
            System.out.println("Result: " + result);
        } catch (Exception e) {
            System.err.println("Failed to finalize assignment: " + e.getMessage());
        }
    }

    public void arriveAtRestaurant(int orderId) {
        try {
            long arrivalPhysicalTime = getEstimatedPhysicalTime();
            long sendLamport = lamportClock.tickOnSend();

            System.out.printf("\n[RIDER ARRIVAL] Rider %s arriving at restaurant\n" +
                            "  -> Physical Arrival Time: %s (%d ms)\n" +
                            "  -> Logical Lamport Time : %d\n",
                    riderId, SDF.format(new Date(arrivalPhysicalTime)), arrivalPhysicalTime, sendLamport);

            boolean onTime = gateway.arriveAtRestaurant(orderId, riderId, arrivalPhysicalTime, sendLamport);
            if (onTime) {
                System.out.println(">>> [SUCCESS] Arrived ON TIME! Status updated to RIDER_ARRIVED.");
            } else {
                System.out.println(">>> [FAILED] Arrived LATE or not assigned to this rider. Order reset to WAITING_FOR_RIDER.");
            }
        } catch (Exception e) {
            System.err.println("Arrival report failed: " + e.getMessage());
        }
    }

    public void deliverOrder(int orderId) {
        try {
            long sendLamport = lamportClock.tickOnSend();
            System.out.printf("\n[DELIVERY COMPLETION] Rider %s marking order delivered\n" +
                            "  -> Logical Lamport Time: %d\n",
                    riderId, sendLamport);

            boolean delivered = gateway.deliverOrder(orderId, riderId, sendLamport);
            if (delivered) {
                System.out.println(">>> [SUCCESS] Order successfully DELIVERED to customer!");
            } else {
                System.out.println(">>> [FAILED] Could not mark delivered (must be in RIDER_ARRIVED status).");
            }
        } catch (Exception e) {
            System.err.println("Delivery report failed: " + e.getMessage());
        }
    }

    public void checkStatus(int orderId) {
        try {
            OrderSnapshot snapshot = gateway.getOrderStatus(orderId);
            if (snapshot != null) {
                System.out.println(snapshot);
            } else {
                System.out.println("Order #" + orderId + " not found.");
            }
        } catch (Exception e) {
            System.err.println("Status check failed: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            String riderId = (args.length > 0 && !args[0].startsWith("-")) ? args[0] : "R1";
            String gatewayUrl = ClusterConfig.getGatewayUrl();
            GatewayRemote gateway = (GatewayRemote) Naming.lookup(gatewayUrl);

            RiderClient client = new RiderClient(riderId, gateway);
            System.out.printf("Rider Client initialized for: %s (Connected to Gateway at %s)\n", riderId, gatewayUrl);

            // Scripting / CLI arguments: [riderId] [action] [orderId] [delayMs]
            if (args.length >= 2) {
                String action = args[1];
                if (action.equalsIgnoreCase("sync")) {
                    client.synchronizeClock();
                    return;
                } else if (action.equalsIgnoreCase("accept") && args.length >= 3) {
                    int orderId = Integer.parseInt(args[2]);
                    long delay = args.length >= 4 ? Long.parseLong(args[3]) : 0;
                    client.synchronizeClock();
                    client.acceptOrder(orderId, delay);
                    return;
                } else if (action.equalsIgnoreCase("finalize") && args.length >= 3) {
                    client.finalizeAssignment(Integer.parseInt(args[2]));
                    return;
                } else if (action.equalsIgnoreCase("arrive") && args.length >= 3) {
                    client.synchronizeClock();
                    client.arriveAtRestaurant(Integer.parseInt(args[2]));
                    return;
                } else if (action.equalsIgnoreCase("deliver") && args.length >= 3) {
                    client.deliverOrder(Integer.parseInt(args[2]));
                    return;
                } else if (action.equalsIgnoreCase("status") && args.length >= 3) {
                    client.checkStatus(Integer.parseInt(args[2]));
                    return;
                }
            }

            // Interactive console mode
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.println("\n========== RIDER APP (" + riderId + ") ==========");
                System.out.printf("Current Logical Lamport Time: %d\n", client.getLamportClock().getValue());
                System.out.println("1. Synchronize Physical Clock (Cristian's Algorithm with RTT)");
                System.out.println("2. Accept Delivery Opportunity");
                System.out.println("3. Finalize Assignment (Server Action)");
                System.out.println("4. Arrive at Restaurant");
                System.out.println("5. Mark Order DELIVERED");
                System.out.println("6. Check Order Status & Event Log");
                System.out.println("7. Local Event Tick (Advance Lamport Clock)");
                System.out.println("8. Exit");
                System.out.print("Enter choice: ");

                if (!scanner.hasNextInt()) break;
                int choice = scanner.nextInt();
                scanner.nextLine();

                switch (choice) {
                    case 1 -> client.synchronizeClock();
                    case 2 -> {
                        System.out.print("Enter Order ID (e.g. 1001): ");
                        int orderId = scanner.nextInt();
                        System.out.print("Simulate network transmission delay in ms (0 for instant): ");
                        long delay = scanner.hasNextLong() ? scanner.nextLong() : 0;
                        scanner.nextLine();
                        client.acceptOrder(orderId, delay);
                    }
                    case 3 -> {
                        System.out.print("Enter Order ID to finalize: ");
                        int orderId = scanner.nextInt();
                        scanner.nextLine();
                        client.finalizeAssignment(orderId);
                    }
                    case 4 -> {
                        System.out.print("Enter Order ID: ");
                        int orderId = scanner.nextInt();
                        scanner.nextLine();
                        client.arriveAtRestaurant(orderId);
                    }
                    case 5 -> {
                        System.out.print("Enter Order ID to deliver: ");
                        int orderId = scanner.nextInt();
                        scanner.nextLine();
                        client.deliverOrder(orderId);
                    }
                    case 6 -> {
                        System.out.print("Enter Order ID: ");
                        int orderId = scanner.nextInt();
                        scanner.nextLine();
                        client.checkStatus(orderId);
                    }
                    case 7 -> {
                        long tick = client.getLamportClock().tick();
                        System.out.println("Local event executed. Clock advanced to: " + tick);
                    }
                    case 8 -> {
                        System.out.println("Exiting Rider App. Goodbye!");
                        return;
                    }
                    default -> System.out.println("Invalid choice. Please try again.");
                }
            }
        } catch (Exception e) {
            System.err.println("RiderClient Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
