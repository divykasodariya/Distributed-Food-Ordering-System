import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RiderService extends Remote {
    boolean acceptDelivery(int orderId, String riderId, long synchronizedTimestamp) throws RemoteException;
    String finalizeAssignment(int orderId) throws RemoteException;
    boolean arriveAtRestaurant(int orderId, String riderId, long synchronizedTimestamp) throws RemoteException;
    long getServerTime() throws RemoteException; 
}