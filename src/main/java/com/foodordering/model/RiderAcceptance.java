package com.foodordering.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Captures a rider's delivery acceptance bid.
 * Contains both:
 * 1. physicalTimestamp: Estimated real-world action time (for bid tie-breaking via physical clock)
 * 2. lamportTimestamp: Logical clock value of the action (for distributed causal event tracking)
 * 3. serverReceiptTimestamp: Physical server time when RMI request arrived
 */
public class RiderAcceptance implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String riderId;
    private final long physicalTimestamp;      // Estimated synchronized time at client action
    private final long serverReceiptTimestamp; // Server local time when RMI request arrived
    private final long lamportTimestamp;       // Rider's logical clock when action was initiated

    public RiderAcceptance(String riderId, long physicalTimestamp, long serverReceiptTimestamp, long lamportTimestamp) {
        this.riderId = riderId;
        this.physicalTimestamp = physicalTimestamp;
        this.serverReceiptTimestamp = serverReceiptTimestamp;
        this.lamportTimestamp = lamportTimestamp;
    }

    public String getRiderId() {
        return riderId;
    }

    public long getPhysicalTimestamp() {
        return physicalTimestamp;
    }

    public long getServerReceiptTimestamp() {
        return serverReceiptTimestamp;
    }

    public long getLamportTimestamp() {
        return lamportTimestamp;
    }

    @Override
    public String toString() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        return String.format("Rider %s [Physical Action Time: %s (%d), Lamport: %d, Server Receipt Time: %s (%d)]",
                riderId,
                sdf.format(new Date(physicalTimestamp)),
                physicalTimestamp,
                lamportTimestamp,
                sdf.format(new Date(serverReceiptTimestamp)),
                serverReceiptTimestamp);
    }
}
