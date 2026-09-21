package com.foodordering.model;

import java.io.Serializable;

public class NodeInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String nodeId;
    private final String host;
    private final int port;
    private NodeRole role;
    private String healthStatus; // e.g. "ALIVE", "SUSPECTED", "DEAD"

    public NodeInfo(String nodeId, String host, int port, NodeRole role, String healthStatus) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.role = role;
        this.healthStatus = healthStatus;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public NodeRole getRole() {
        return role;
    }

    public synchronized void setRole(NodeRole role) {
        this.role = role;
    }

    public String getHealthStatus() {
        return healthStatus;
    }

    public synchronized void setHealthStatus(String healthStatus) {
        this.healthStatus = healthStatus;
    }

    public String getRmiUrl() {
        return "rmi://" + host + ":" + port + "/OrderNode" + nodeId;
    }

    @Override
    public String toString() {
        return String.format("NodeInfo[id=%s, role=%s, host=%s, port=%d, health=%s]",
                nodeId, role, host, port, healthStatus);
    }
}
