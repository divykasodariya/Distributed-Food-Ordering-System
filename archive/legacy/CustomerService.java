import java.rmi.Remote;
import java.rmi.RemoteException;

public interface CustomerService extends Remote {
    int requestOrder(String customer, String restaurant) throws RemoteException;
    String getOrderStatus(int orderId) throws RemoteException;
    long getServerTime() throws RemoteException; 
}