import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;

public class FoodOrderingServer extends UnicastRemoteObject implements CustomerService, RiderService {
    private Map<Integer, Order> orders = new HashMap<>();
    private int nextOrderId = 1001;

    public FoodOrderingServer() throws RemoteException { super(0); }

    @Override
    public int requestOrder(String customer, String restaurant) throws RemoteException {
        int orderId = nextOrderId++;
        orders.put(orderId, new Order(orderId, customer, restaurant));
        System.out.println("[NEW ORDER] ID: " + orderId + " from " + customer);
        return orderId;
    }

    @Override
    public long getServerTime() throws RemoteException {
        return System.currentTimeMillis();
    }

    @Override
    public synchronized boolean acceptDelivery(int orderId, String riderId, long synchronizedTimestamp) throws RemoteException {
        Order order = orders.get(orderId);
        if (order == null || !order.getStatus().equals("WAITING")) return false;
        
        order.addAcceptance(riderId, synchronizedTimestamp);
        System.out.println("[ACCEPTANCE] Rider " + riderId + " accepted Order " + orderId + " at " + synchronizedTimestamp);
        return true;
    }

    @Override
    public synchronized String finalizeAssignment(int orderId) throws RemoteException {
        Order order = orders.get(orderId);
        if (order == null || order.getRiderAcceptances().isEmpty()) return "No riders accepted this order yet.";

        String winner = null;
        long earliestTimestamp = Long.MAX_VALUE;

        for (Map.Entry<String, Long> entry : order.getRiderAcceptances().entrySet()) {
            if (entry.getValue() < earliestTimestamp) {
                earliestTimestamp = entry.getValue();
                winner = entry.getKey();
            }
        }

        order.setAssignedRider(winner);
        order.setAssignmentTime(System.currentTimeMillis());
        order.setDeadline(order.getAssignmentTime() + (10 * 60 * 1000)); // 10 minute deadline
        order.setStatus("ASSIGNED");
        
        return "Order " + orderId + " assigned to Rider " + winner;
    }

    @Override
    public synchronized boolean arriveAtRestaurant(int orderId, String riderId, long synchronizedTimestamp) throws RemoteException {
        Order order = orders.get(orderId);
        if (order == null || !riderId.equals(order.getAssignedRider())) return false;

        if (synchronizedTimestamp < order.getDeadline()) {
            order.setStatus("RIDER ARRIVED");
            System.out.println("[RESULT] Rider " + riderId + " arrived ON TIME.");
            return true;
        } else {
            order.setStatus("WAITING");
            order.setAssignedRider(null);
            System.out.println("[RESULT] Rider " + riderId + " was LATE. Reassigning order.");
            return false;
        }
    }

    @Override
    public synchronized String getOrderStatus(int orderId) throws RemoteException {
        Order order = orders.get(orderId);
        return order != null ? order.toString() : "Order not found";
    }

    public static void main(String[] args) {
        try {
            System.setProperty("java.rmi.server.hostname", "food-server");
            LocateRegistry.createRegistry(1234);
            FoodOrderingServer server = new FoodOrderingServer();
            
            // Bind the interfaces to separate endpoints
            Naming.rebind("rmi://localhost:1234/CustomerService", server);
            Naming.rebind("rmi://localhost:1234/RiderService", server);
            
            System.out.println("Dual-Interface Server running on port 1234...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}