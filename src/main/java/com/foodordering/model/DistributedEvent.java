package com.foodordering.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Immutable representation of a distributed domain event ordered by Lamport logical clock.
 *
 * Notice:
 * Lamport timestamps establish causal partial ordering (a -> b => L(a) < L(b)).
 * For concurrent events where neither causally preceded the other, Lamport timestamps
 * alone do NOT establish physical or causal precedence.
 * For deterministic total ordering, a tie-breaker is explicitly applied:
 *   (lamportTimestamp, sourceId, eventId)
 */
public class DistributedEvent implements Serializable, Comparable<DistributedEvent> {
    private static final long serialVersionUID = 1L;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss.SSS");

    private final String eventId;
    private final int orderId;
    private final EventType eventType;
    private final String sourceId;
    private final long lamportTimestamp;
    private final Long physicalTimestamp; // Present only when genuinely relevant (e.g. rider action)

    public DistributedEvent(String eventId, int orderId, EventType eventType, String sourceId,
                            long lamportTimestamp, Long physicalTimestamp) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.eventType = eventType;
        this.sourceId = sourceId;
        this.lamportTimestamp = lamportTimestamp;
        this.physicalTimestamp = physicalTimestamp;
    }

    public DistributedEvent(String eventId, int orderId, EventType eventType, String sourceId, long lamportTimestamp) {
        this(eventId, orderId, eventType, sourceId, lamportTimestamp, null);
    }

    public String getEventId() { return eventId; }
    public int getOrderId() { return orderId; }
    public EventType getEventType() { return eventType; }
    public String getSourceId() { return sourceId; }
    public long getLamportTimestamp() { return lamportTimestamp; }
    public Long getPhysicalTimestamp() { return physicalTimestamp; }

    /**
     * Implements deterministic total ordering using the standard tie-breaker:
     * (lamportTimestamp, sourceId, eventId).
     */
    @Override
    public int compareTo(DistributedEvent other) {
        int cmp = Long.compare(this.lamportTimestamp, other.lamportTimestamp);
        if (cmp != 0) return cmp;
        cmp = this.sourceId.compareTo(other.sourceId);
        if (cmp != 0) return cmp;
        return this.eventId.compareTo(other.eventId);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(eventType).append("]");
        sb.append(" Order=#").append(orderId);
        sb.append(" | Source=").append(sourceId);
        if (physicalTimestamp != null) {
            sb.append(" | PhysicalTime=").append(SDF.format(new Date(physicalTimestamp)))
              .append(" (").append(physicalTimestamp).append(")");
        }
        sb.append(" | Lamport=").append(lamportTimestamp);
        return sb.toString();
    }
}
