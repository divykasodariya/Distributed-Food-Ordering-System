package com.foodordering.model;

import java.io.Serializable;

/**
 * System-wide telemetry event broadcast to HTTP/WebSocket/SSE clients
 * capturing real distributed system events across Gateway, Nodes, Clocks, Elections, and Replications.
 */
public class SystemEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String eventId;
    private final String category;      // e.g., ORDER_CREATED, REPLICATION_SENT, HEARTBEAT_RECEIVED, ELECTION_STARTED, etc.
    private final String sourceNode;    // e.g., "Gateway", "1", "2", "3", "Customer", "Rider-R1"
    private final String targetNode;    // e.g., "1", "2", "3", "Gateway", "ALL"
    private final long lamportTime;
    private final long physicalTime;
    private final Integer orderId;
    private final String details;
    private final boolean success;

    public SystemEvent(String eventId, String category, String sourceNode, String targetNode,
                       long lamportTime, long physicalTime, Integer orderId, String details, boolean success) {
        this.eventId = eventId;
        this.category = category;
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.lamportTime = lamportTime;
        this.physicalTime = physicalTime;
        this.orderId = orderId;
        this.details = details;
        this.success = success;
    }

    public String getEventId() { return eventId; }
    public String getCategory() { return category; }
    public String getSourceNode() { return sourceNode; }
    public String getTargetNode() { return targetNode; }
    public long getLamportTime() { return lamportTime; }
    public long getPhysicalTime() { return physicalTime; }
    public Integer getOrderId() { return orderId; }
    public String getDetails() { return details; }
    public boolean isSuccess() { return success; }

    public String toJson() {
        return String.format(
            "{\"eventId\":\"%s\",\"category\":\"%s\",\"sourceNode\":\"%s\",\"targetNode\":\"%s\"," +
            "\"lamportTime\":%d,\"physicalTime\":%d,\"orderId\":%s,\"details\":\"%s\",\"success\":%b}",
            escapeJson(eventId),
            escapeJson(category),
            escapeJson(sourceNode != null ? sourceNode : ""),
            escapeJson(targetNode != null ? targetNode : ""),
            lamportTime,
            physicalTime,
            orderId != null ? orderId.toString() : "null",
            escapeJson(details != null ? details : ""),
            success
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
