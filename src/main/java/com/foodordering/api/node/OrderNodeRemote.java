package com.foodordering.api.node;

import com.foodordering.model.NodeInfo;
import com.foodordering.model.NodeRole;
import com.foodordering.model.OrderSnapshot;
import com.foodordering.model.ReplicationAck;
import com.foodordering.model.ReplicationEvent;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Internal RMI interface implemented by each OrderNode server process.
 * Used by GatewayServer for client routing and by peer nodes for application-state replication.
 */
public interface OrderNodeRemote extends Remote {

    /**
     * Places a new order on this node. Carries client's logical Lamport timestamp.
     */
    int placeOrder(String customerName, String restaurantName, String itemName, int quantity, long clientLamport) throws RemoteException;

    /**
     * Retrieves an immutable read-only snapshot of the specified order.
     */
    OrderSnapshot getOrderStatus(int orderId) throws RemoteException;

    /**
     * Records a delivery acceptance bid from a rider with both physical action time and logical Lamport time.
     */
    boolean acceptDelivery(int orderId, String riderId, long physicalTimestamp, long receiptTimestamp, long riderLamport) throws RemoteException;

    /**
     * Finalizes the rider assignment using earliest synchronized physical timestamp.
     */
    String finalizeAssignment(int orderId) throws RemoteException;

    /**
     * Validates that the assigned rider arrived at the restaurant before the deadline.
     */
    boolean arriveAtRestaurant(int orderId, String riderId, long physicalTimestamp, long riderLamport) throws RemoteException;

    /**
     * Marks the order as DELIVERED by the assigned rider.
     */
    boolean deliverOrder(int orderId, String riderId, long riderLamport) throws RemoteException;

    /**
     * Receives and applies a replicated application-state event from the PRIMARY node.
     * Returns an explicit acknowledgment (ACK/NACK).
     */
    ReplicationAck replicateEvent(ReplicationEvent event) throws RemoteException;

    /**
     * Returns historical events recorded after the specified version for catch-up synchronization.
     */
    List<ReplicationEvent> getMissingEvents(long afterVersion) throws RemoteException;

    /**
     * Returns the latest persisted log version on this node.
     */
    long getLatestLogVersion() throws RemoteException;

    /**
     * Returns metadata about this node (nodeId, host, port, role, health).
     */
    NodeInfo getNodeInfo() throws RemoteException;

    /**
     * Updates the current role of this node (PRIMARY or BACKUP).
     */
    void setRole(NodeRole newRole) throws RemoteException;

    /**
     * Simple connectivity / time probe.
     */
    long ping() throws RemoteException;

    /**
     * Bully Election: receives election challenge from a lower-ID node.
     * Returns true if this node has a higher ID and takes over.
     */
    boolean receiveElection(String fromNodeId) throws RemoteException;

    /**
     * Bully Election: receives coordinator announcement from the newly elected leader.
     */
    void receiveCoordinator(String coordinatorNodeId) throws RemoteException;

    /**
     * Heartbeat probe from peer / monitoring service.
     */
    boolean heartbeat(String fromNodeId) throws RemoteException;
}
