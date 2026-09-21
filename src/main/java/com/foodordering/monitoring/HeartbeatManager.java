package com.foodordering.monitoring;

import com.foodordering.model.NodeInfo;

/**
 * HeartbeatManager (alias for HeartbeatMonitor).
 * Monitors PRIMARY availability and detects failures in the cluster.
 */
public class HeartbeatManager extends HeartbeatMonitor {
    public HeartbeatManager(String localNodeId, NodeInfo targetPrimary, FailureCallback failureCallback) {
        super(localNodeId, targetPrimary, failureCallback);
    }

    public HeartbeatManager(String localNodeId, NodeInfo targetPrimary, FailureCallback failureCallback,
                            long intervalMs, int maxFailures) {
        super(localNodeId, targetPrimary, failureCallback, intervalMs, maxFailures);
    }
}
