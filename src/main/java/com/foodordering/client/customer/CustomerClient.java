package com.foodordering.client.customer;

import com.foodordering.api.gateway.GatewayRemote;
import com.foodordering.clock.LamportClock;
import com.foodordering.config.ClusterConfig;
import com.foodordering.model.NodeInfo;
import com.foodordering.model.OrderSnapshot;

import java.rmi.Naming;
import java.util.Scanner;

/**
 * Customer client process.
 * Connects to the GatewayServer via Java RMI to place orders and query order statuses.
 * Maintains its own Lamport logical clock for causal event ordering.
 */
public class CustomerClient {

    public static void main(String[] args) {
        try {
            String gatewayUrl = ClusterConfig.getGatewayUrl();
            GatewayRemote gateway = (GatewayRemote) Naming.lookup(gatewayUrl);
            System.out.println("Connected to Gateway Server at " + gatewayUrl);

            LamportClock customerClock = new LamportClock("CustomerClient");
            System.out.println("Initialized Local " + customerClock);

            // Support non-interactive command line mode for automated demos / verification
            if (args.length >= 4 && args[0].equalsIgnoreCase("place")) {
                String customer = args[1];
                String restaurant = args[2];
                String item = args[3];
                int qty = args.length >= 5 ? Integer.parseInt(args[4]) : 1;

                long sendLamport = customerClock.tickOnSend();
                int orderId = gateway.placeOrder(customer, restaurant, item, qty, sendLamport);
                System.out.printf("[AUTOMATED] Order placed successfully! ID: #%d (Lamport sent: %d, local: %d)\n",
                        orderId, sendLamport, customerClock.getValue());
                return;
            }

            if (args.length >= 2 && args[0].equalsIgnoreCase("status")) {
                int orderId = Integer.parseInt(args[1]);
                OrderSnapshot snapshot = gateway.getOrderStatus(orderId);
                if (snapshot != null) {
                    System.out.println(snapshot);
                } else {
                    System.out.println("Order #" + orderId + " not found.");
                }
                return;
            }

            // Interactive console mode
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.println("\n========== CUSTOMER FOOD ORDERING APP ==========");
                System.out.printf("Current Local Lamport Time: %d\n", customerClock.getValue());
                System.out.println("1. Browse Restaurant Menus");
                System.out.println("2. Place Pizza Order (Default: Customer A, Restaurant A, Pizza)");
                System.out.println("3. Place Custom Order");
                System.out.println("4. Check Order Status & Event Log");
                System.out.println("5. Check Order Node Topology / Statuses");
                System.out.println("6. Local Event Tick (Advance Lamport Clock)");
                System.out.println("7. Exit");
                System.out.print("Enter choice: ");

                if (!scanner.hasNextInt()) break;
                int choice = scanner.nextInt();
                scanner.nextLine(); // consume newline

                switch (choice) {
                    case 1 -> {
                        System.out.println(gateway.getMenu());
                    }
                    case 2 -> {
                        long sendLamport = customerClock.tickOnSend();
                        int orderId = gateway.placeOrder("Customer A", "Restaurant A", "Pizza", 1, sendLamport);
                        System.out.println("\n>>> [SUCCESS] Pizza order placed!");
                        System.out.printf(">>> Distributed Order ID: #%d | Lamport Sent: %d\n", orderId, sendLamport);
                        System.out.println(">>> Status: WAITING_FOR_RIDER");
                    }
                    case 3 -> {
                        System.out.print("Customer Name: ");
                        String customer = scanner.nextLine().trim();
                        System.out.print("Restaurant Name (e.g. Restaurant A / Restaurant B): ");
                        String restaurant = scanner.nextLine().trim();
                        System.out.print("Item Name (e.g. Pizza / Burger): ");
                        String item = scanner.nextLine().trim();
                        System.out.print("Quantity: ");
                        int qty = scanner.nextInt();
                        scanner.nextLine();

                        long sendLamport = customerClock.tickOnSend();
                        int orderId = gateway.placeOrder(customer, restaurant, item, qty, sendLamport);
                        System.out.println("\n>>> [SUCCESS] Order placed!");
                        System.out.printf(">>> Distributed Order ID: #%d | Lamport Sent: %d\n", orderId, sendLamport);
                    }
                    case 4 -> {
                        System.out.print("Enter Order ID (e.g. 1001): ");
                        int orderId = scanner.nextInt();
                        scanner.nextLine();
                        OrderSnapshot snapshot = gateway.getOrderStatus(orderId);
                        if (snapshot != null) {
                            System.out.println("\n[ORDER SNAPSHOT RECEIVED FROM GATEWAY]");
                            System.out.println(snapshot);
                        } else {
                            System.out.println("Order #" + orderId + " not found.");
                        }
                    }
                    case 5 -> {
                        System.out.println("\n--- Current Node Topology ---");
                        for (NodeInfo info : gateway.getNodeStatuses()) {
                            System.out.println(" - " + info);
                        }
                    }
                    case 6 -> {
                        long newTick = customerClock.tick();
                        System.out.printf("Local event executed. Clock advanced to: %d\n", newTick);
                    }
                    case 7 -> {
                        System.out.println("Exiting Customer App. Goodbye!");
                        return;
                    }
                    default -> System.out.println("Invalid choice. Please try again.");
                }
            }
        } catch (Exception e) {
            System.err.println("CustomerClient Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
