package com.foodordering.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side mutable entity representing an order.
 * All state mutations, event logging, and status transitions are synchronized to ensure thread safety
 * when accessed concurrently by worker threads and RMI requests.
 */
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String STATUS_WAITING_FOR_RIDER = "WAITING_FOR_RIDER";
    public static final String STATUS_ASSIGNED = "ASSIGNED";
    public static final String STATUS_RIDER_ARRIVED = "RIDER_ARRIVED";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final int orderId;
    private final String customerName;
    private final String restaurantName;
    private final String itemName;
    private final int quantity;
    private final long creationTime;

    private String status;
    private String assignedRider;
    private long assignmentTime;
    private long deadline;

    // Thread-safe map of rider acceptances (riderId -> RiderAcceptance)
    private final Map<String, RiderAcceptance> riderAcceptances = new ConcurrentHashMap<>();

    // Causal event history log ordered by Lamport logical clock
    private final List<DistributedEvent> eventLog = Collections.synchronizedList(new ArrayList<>());

    public Order(int orderId, String customerName, String restaurantName, String itemName, int quantity) {
        this.orderId = orderId;
        this.customerName = customerName;
        this.restaurantName = restaurantName;
        this.itemName = itemName;
        this.quantity = quantity;
        this.creationTime = System.currentTimeMillis();
        this.status = STATUS_WAITING_FOR_RIDER;
    }

    public int getOrderId() { return orderId; }
    public String getCustomerName() { return customerName; }
    public String getRestaurantName() { return restaurantName; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public long getCreationTime() { return creationTime; }

    public synchronized String getStatus() { return status; }
    public synchronized String getAssignedRider() { return assignedRider; }
    public synchronized long getAssignmentTime() { return assignmentTime; }
    public synchronized long getDeadline() { return deadline; }

    public synchronized void addEvent(DistributedEvent event) {
        eventLog.add(event);
    }

    public List<DistributedEvent> getEventLog() {
        synchronized (eventLog) {
            return new ArrayList<>(eventLog);
        }
    }

    /**
     * Thread-safe acceptance of an order by a rider.
     * Records physical timestamp, server receipt timestamp, and rider's logical Lamport timestamp.
     */
    public synchronized boolean addAcceptance(String riderId, long physicalTimestamp, long receiptTimestamp, long lamportTimestamp) {
        if (!STATUS_WAITING_FOR_RIDER.equals(status)) {
            return false;
        }
        riderAcceptances.put(riderId, new RiderAcceptance(riderId, physicalTimestamp, receiptTimestamp, lamportTimestamp));
        return true;
    }

    /**
     * Thread-safe assignment of the order to the rider who performed the action earliest
     * according to the synchronized physical clock.
     * Note: This demonstrates ordering by physical action timestamp, NOT network arrival ordering.
     */
    public synchronized String finalizeAssignment() {
        if (!STATUS_WAITING_FOR_RIDER.equals(status)) {
            return "Order #" + orderId + " is already in status: " + status;
        }
        if (riderAcceptances.isEmpty()) {
            return "No riders have accepted Order #" + orderId + " yet.";
        }

        // Compare by physicalTimestamp (earliest action wins); break ties by receiptTimestamp
        List<RiderAcceptance> sorted = new ArrayList<>(riderAcceptances.values());
        sorted.sort(Comparator.comparingLong(RiderAcceptance::getPhysicalTimestamp)
                .thenComparingLong(RiderAcceptance::getServerReceiptTimestamp));

        RiderAcceptance winner = sorted.get(0);
        this.assignedRider = winner.getRiderId();
        this.assignmentTime = System.currentTimeMillis();
        this.deadline = this.assignmentTime + (10 * 60 * 1000); // 10-minute delivery window
        this.status = STATUS_ASSIGNED;

        return String.format("Order #%d successfully ASSIGNED to Rider %s (Physical action time: %d, Server received: %d)",
                orderId, winner.getRiderId(), winner.getPhysicalTimestamp(), winner.getServerReceiptTimestamp());
    }

    /**
     * Thread-safe check when rider arrives at restaurant.
     * Validates against the 10-minute deadline.
     */
    public synchronized boolean arriveAtRestaurant(String riderId, long physicalTimestamp) {
        if (!STATUS_ASSIGNED.equals(status) || assignedRider == null || !assignedRider.equals(riderId)) {
            return false;
        }

        if (physicalTimestamp <= deadline) {
            this.status = STATUS_RIDER_ARRIVED;
            return true;
        } else {
            // Late arrival: reset to waiting for another rider
            this.status = STATUS_WAITING_FOR_RIDER;
            this.assignedRider = null;
            return false;
        }
    }

    /**
     * Thread-safe check when rider completes delivery.
     */
    public synchronized boolean deliverOrder(String riderId) {
        if (!STATUS_RIDER_ARRIVED.equals(status) || assignedRider == null || !assignedRider.equals(riderId)) {
            return false;
        }
        this.status = STATUS_DELIVERED;
        return true;
    }

    /**
     * Creates an immutable read-only snapshot DTO for safe transmission over RMI.
     */
    public synchronized OrderSnapshot toSnapshot() {
        return new OrderSnapshot(
                orderId,
                customerName,
                restaurantName,
                itemName,
                quantity,
                status,
                assignedRider,
                creationTime,
                assignmentTime,
                deadline,
                new ArrayList<>(riderAcceptances.values()),
                getEventLog()
        );
    }

    @Override
    public synchronized String toString() {
        return toSnapshot().toString();
    }
}
