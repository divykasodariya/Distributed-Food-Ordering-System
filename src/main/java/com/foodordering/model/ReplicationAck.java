package com.foodordering.model;

import java.io.Serializable;

/**
 * Acknowledgment DTO sent from a backup node back to the primary node
 * confirming whether an application-state replication event was applied successfully.
 */
public class ReplicationAck implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String nodeId;
    private final String eventId;
    private final boolean success;
    private final String statusMessage;
    private final long currentVersion;

    public ReplicationAck(String nodeId, String eventId, boolean success, String statusMessage, long currentVersion) {
        this.nodeId = nodeId;
        this.eventId = eventId;
        this.success = success;
        this.statusMessage = statusMessage;
        this.currentVersion = currentVersion;
    }

    public String getNodeId() { return nodeId; }
    public String getEventId() { return eventId; }
    public boolean isSuccess() { return success; }
    public String getStatusMessage() { return statusMessage; }
    public long getCurrentVersion() { return currentVersion; }

    @Override
    public String toString() {
        return String.format("ReplicationAck[Node=%s, Event=%s, Success=%b, Msg='%s', Version=%d]",
                nodeId, eventId, success, statusMessage, currentVersion);
    }
}
