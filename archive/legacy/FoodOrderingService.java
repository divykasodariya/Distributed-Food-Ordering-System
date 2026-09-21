import java.rmi.Remote;
import java.rmi.RemoteException;

public interface FoodOrderingService extends Remote {
    String browseMenu() throws RemoteException;
    
    // Added synchronizedTimestamp parameter
    int placeOrder(String customerName, String itemName, int quantity, long synchronizedTimestamp) throws RemoteException;
    
    Order getOrderStatus(int orderId) throws RemoteException;
    
    // New method to fetch the server's physical time
    long getServerTime() throws RemoteException;
}