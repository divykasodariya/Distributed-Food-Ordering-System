package com.foodordering.api.gateway;

import com.foodordering.model.NodeInfo;
import com.foodordering.model.OrderSnapshot;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Public client-facing RMI interface exposed by the GatewayServer.
 */
public interface GatewayRemote extends Remote {

    /**
     * Returns physical server time (ms since epoch) for Cristian-style physical clock synchronization.
     */
    long getServerTime() throws RemoteException;

    /**
     * Returns formatted menu and restaurant catalog.
     */
    String getMenu() throws RemoteException;

    /**
     * Places a customer order. Carries the customer's Lamport logical timestamp.
     * The Gateway routes this write request to the current PRIMARY node.
     */
    int placeOrder(String customerName, String restaurantName, String itemName, int quantity, long clientLamport) throws RemoteException;

    /**
     * Retrieves an immutable read-only snapshot of the specified order, including its event log.
     */
    OrderSnapshot getOrderStatus(int orderId) throws RemoteException;

    /**
     * Records a delivery acceptance bid from a rider.
     * Carries both:
     * - physicalTimestamp: physical action timestamp for Cristian's action ordering
     * - riderLamport: rider's logical timestamp for distributed causal event tracking
     */
    boolean acceptDelivery(int orderId, String riderId, long physicalTimestamp, long riderLamport) throws RemoteException;

    /**
     * Evaluates rider acceptances based on synchronized physical action timestamps and assigns the order.
     */
    String finalizeAssignment(int orderId) throws RemoteException;

    /**
     * Validates on-time rider arrival at the restaurant against order deadline.
     * Carries the rider's Lamport timestamp.
     */
    boolean arriveAtRestaurant(int orderId, String riderId, long physicalTimestamp, long riderLamport) throws RemoteException;

    /**
     * Marks the order as DELIVERED by the assigned rider.
     * Carries the rider's Lamport timestamp.
     */
    boolean deliverOrder(int orderId, String riderId, long riderLamport) throws RemoteException;

    /**
     * Returns current status and topology of all order nodes known to the gateway.
     */
    List<NodeInfo> getNodeStatuses() throws RemoteException;

    /**
     * Notifies the Gateway that a new PRIMARY node has been elected via Bully election.
     * Updates the Gateway routing table dynamically.
     */
    void notifyLeaderChanged(String newLeaderNodeId) throws RemoteException;

    /**
     * Returns the node ID of the current PRIMARY node.
     */
    String getPrimaryNodeId() throws RemoteException;
}
