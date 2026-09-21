import java.rmi.Naming;
import java.util.Scanner;

public class FoodOrderingClient {
    public static void main(String[] args) {
        try {
            FoodOrderingService server = (FoodOrderingService) Naming.lookup("rmi://food-server:1234/FoodOrderingService");
            Scanner sc = new Scanner(System.in);
            long clockOffset = 0;
            
            while (true) {
                System.out.println("\n--- MENU ---");
                System.out.println("1. Request Menu");
                System.out.println("2. Synchronize Clock");
                System.out.println("3. Place Order");
                System.out.println("4. Check Order Status");
                System.out.println("5. Exit");
                System.out.print("Choice: ");
                
                int choice = sc.nextInt();
                sc.nextLine();
                
                switch (choice) {
                    case 1 -> System.out.println(server.browseMenu());
                    case 2 -> {
                        System.out.println("\n--- CLOCK SYNCHRONIZATION ---");
                        long localTime = System.currentTimeMillis();
                        long serverTime = server.getServerTime();
                        clockOffset = serverTime - localTime;
                        System.out.println("Client local time: " + localTime);
                        System.out.println("Server time: " + serverTime);
                        System.out.println("Clock offset: " + clockOffset + "ms");
                        System.out.println("Clock synchronized.");
                    }
                    case 3 -> {
                        System.out.print("Name: ");
                        String name = sc.nextLine();
                        System.out.print("Item: ");
                        String item = sc.nextLine();
                        System.out.print("Quantity: ");
                        int qty = sc.nextInt();
                        sc.nextLine();
                        
                        // Calculate synchronized time using the offset
                        long synchronizedTimestamp = System.currentTimeMillis() + clockOffset;
                        
                        int orderId = server.placeOrder(name, item, qty, synchronizedTimestamp);
                        if (orderId != -1) {
                            System.out.println("Order placed! Your Order ID is: " + orderId);
                        } else {
                            System.out.println("Invalid quantity.");
                        }
                    }
                    case 4 -> {
                        System.out.print("Enter Order ID: ");
                        int id = sc.nextInt();
                        sc.nextLine();
                        
                        Order order = server.getOrderStatus(id);
                        if (order != null) {
                            System.out.println("\n[CLIENT RECEIVED OBJECT]");
                            System.out.println("Details: " + order);
                        } else {
                            System.out.println("Order #" + id + " not found.");
                        }
                    }
                    case 5 -> {
                        System.out.println("Exiting client...");
                        sc.close();
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}