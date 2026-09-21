package com.foodordering.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Serializable replication event representing an application-state transition.
 * Uses eventId and monotonic version for idempotent delivery and replay.
 */
public class ReplicationEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss.SSS");

    private final String eventId;
    private final long version;
    private final int orderId;
    private final EventType eventType;
    private final String customerName;
    private final String restaurantName;
    private final String itemName;
    private final int quantity;
    private final String riderId;
    private final Long physicalTimestamp;
    private final long lamportTimestamp;
    private final String sourceNodeId;
    private final long timestamp; // Physical system time when event was recorded

    public ReplicationEvent(String eventId,
                            long version,
                            int orderId,
                            EventType eventType,
                            String customerName,
                            String restaurantName,
                            String itemName,
                            int quantity,
                            String riderId,
                            Long physicalTimestamp,
                            long lamportTimestamp,
                            String sourceNodeId,
                            long timestamp) {
        this.eventId = eventId;
        this.version = version;
        this.orderId = orderId;
        this.eventType = eventType;
        this.customerName = customerName;
        this.restaurantName = restaurantName;
        this.itemName = itemName;
        this.quantity = quantity;
        this.riderId = riderId;
        this.physicalTimestamp = physicalTimestamp;
        this.lamportTimestamp = lamportTimestamp;
        this.sourceNodeId = sourceNodeId;
        this.timestamp = timestamp;
    }

    public String getEventId() { return eventId; }
    public long getVersion() { return version; }
    public int getOrderId() { return orderId; }
    public EventType getEventType() { return eventType; }
    public String getCustomerName() { return customerName; }
    public String getRestaurantName() { return restaurantName; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public String getRiderId() { return riderId; }
    public Long getPhysicalTimestamp() { return physicalTimestamp; }
    public long getLamportTimestamp() { return lamportTimestamp; }
    public String getSourceNodeId() { return sourceNodeId; }
    public long getTimestamp() { return timestamp; }

    /**
     * Converts event to pipe-delimited string format for human-readable append-only disk storage.
     */
    public String toLogString() {
        return String.format("%d|%s|%d|%s|%s|%d|%s|%s|%s|%s|%d|%s|%d",
                version,
                eventId,
                orderId,
                eventType.name(),
                sourceNodeId,
                lamportTimestamp,
                physicalTimestamp != null ? physicalTimestamp.toString() : "NULL",
                sanitize(customerName),
                sanitize(restaurantName),
                sanitize(itemName),
                quantity,
                sanitize(riderId),
                timestamp);
    }

    /**
     * Parses an event from a persisted log line.
     */
    public static ReplicationEvent fromLogString(String line) {
        if (line == null || line.trim().isEmpty() || line.startsWith("#")) {
            return null;
        }
        String[] parts = line.split("\\|", -1);
        if (parts.length < 13) {
            throw new IllegalArgumentException("Corrupted log entry, expected 13 fields but got: " + parts.length);
        }

        long version = Long.parseLong(parts[0]);
        String eventId = parts[1];
        int orderId = Integer.parseInt(parts[2]);
        EventType eventType = EventType.valueOf(parts[3]);
        String sourceNodeId = parts[4];
        long lamportTimestamp = Long.parseLong(parts[5]);
        Long physicalTimestamp = "NULL".equals(parts[6]) ? null : Long.parseLong(parts[6]);
        String customerName = unsanitize(parts[7]);
        String restaurantName = unsanitize(parts[8]);
        String itemName = unsanitize(parts[9]);
        int quantity = Integer.parseInt(parts[10]);
        String riderId = unsanitize(parts[11]);
        long timestamp = Long.parseLong(parts[12]);

        return new ReplicationEvent(eventId, version, orderId, eventType, customerName,
                restaurantName, itemName, quantity, riderId, physicalTimestamp,
                lamportTimestamp, sourceNodeId, timestamp);
    }

    private static String sanitize(String s) {
        if (s == null) return "NULL";
        return s.replace("|", "/");
    }

    private static String unsanitize(String s) {
        if ("NULL".equals(s)) return null;
        return s;
    }

    @Override
    public String toString() {
        return String.format("ReplicationEvent[ver=%d, id=%s, order=%d, type=%s, src=%s, lamport=%d, phys=%s]",
                version, eventId, orderId, eventType, sourceNodeId, lamportTimestamp,
                physicalTimestamp != null ? String.valueOf(physicalTimestamp) : "N/A");
    }
}
