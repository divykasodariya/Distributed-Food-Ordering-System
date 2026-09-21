package com.foodordering.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Immutable read-only snapshot DTO of an order, transferred across RMI.
 * Contains order details, rider acceptance bids, and the distributed event log
 * ordered by Lamport logical timestamps.
 */
public class OrderSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int orderId;
    private final String customerName;
    private final String restaurantName;
    private final String itemName;
    private final int quantity;
    private final String status;
    private final String assignedRider;
    private final long creationTime;
    private final long assignmentTime;
    private final long deadline;
    private final List<RiderAcceptance> acceptances;
    private final List<DistributedEvent> eventLog;

    public OrderSnapshot(int orderId,
                         String customerName,
                         String restaurantName,
                         String itemName,
                         int quantity,
                         String status,
                         String assignedRider,
                         long creationTime,
                         long assignmentTime,
                         long deadline,
                         List<RiderAcceptance> acceptances,
                         List<DistributedEvent> eventLog) {
        this.orderId = orderId;
        this.customerName = customerName;
        this.restaurantName = restaurantName;
        this.itemName = itemName;
        this.quantity = quantity;
        this.status = status;
        this.assignedRider = assignedRider;
        this.creationTime = creationTime;
        this.assignmentTime = assignmentTime;
        this.deadline = deadline;
        this.acceptances = acceptances != null
                ? Collections.unmodifiableList(new ArrayList<>(acceptances))
                : Collections.emptyList();
        this.eventLog = eventLog != null
                ? Collections.unmodifiableList(new ArrayList<>(eventLog))
                : Collections.emptyList();
    }

    public int getOrderId() { return orderId; }
    public String getCustomerName() { return customerName; }
    public String getRestaurantName() { return restaurantName; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public String getAssignedRider() { return assignedRider; }
    public long getCreationTime() { return creationTime; }
    public long getAssignmentTime() { return assignmentTime; }
    public long getDeadline() { return deadline; }
    public List<RiderAcceptance> getAcceptances() { return acceptances; }
    public List<DistributedEvent> getEventLog() { return eventLog; }

    @Override
    public String toString() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("Order ID       : #").append(orderId).append("\n");
        sb.append("Customer       : ").append(customerName).append("\n");
        sb.append("Restaurant     : ").append(restaurantName).append("\n");
        sb.append("Item Ordered   : ").append(itemName).append(" (Qty: ").append(quantity).append(")\n");
        sb.append("Status         : ").append(status).append("\n");
        sb.append("Created At     : ").append(creationTime > 0 ? sdf.format(new Date(creationTime)) : "N/A").append("\n");
        sb.append("Assigned Rider : ").append(assignedRider == null ? "None yet" : assignedRider).append("\n");
        if (assignmentTime > 0) {
            sb.append("Assigned At    : ").append(sdf.format(new Date(assignmentTime))).append("\n");
            sb.append("Deadline       : ").append(sdf.format(new Date(deadline))).append("\n");
        }
        sb.append("Rider Bids/Acceptances (").append(acceptances.size()).append(" total):\n");
        if (acceptances.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (RiderAcceptance acc : acceptances) {
                sb.append("  - ").append(acc.toString()).append("\n");
            }
        }
        sb.append("Distributed Event Log (Lamport Causal Sequence):\n");
        if (eventLog.isEmpty()) {
            sb.append("  (no events recorded yet)\n");
        } else {
            for (DistributedEvent event : eventLog) {
                sb.append("  * ").append(event.toString()).append("\n");
            }
        }
        sb.append("========================================");
        return sb.toString();
    }
}
