package com.foodordering.monitoring;

import com.foodordering.api.node.OrderNodeRemote;
import com.foodordering.model.NodeInfo;

import java.rmi.Naming;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitors the availability of the PRIMARY OrderNode from BACKUP nodes.
 * Periodically issues RMI heartbeat/ping probes.
 * When the primary fails to respond within the configured timeout threshold,
 * marks the node UNREACHABLE and triggers leader election via callback.
 */
public class HeartbeatMonitor {

    public interface FailureCallback {
        void onPrimaryFailure(NodeInfo failedPrimary);
    }

    private final String localNodeId;
    private volatile NodeInfo targetPrimary;
    private final FailureCallback failureCallback;
    private final long intervalMs;
    private final int maxFailures;

    private ScheduledExecutorService scheduler;
    private int consecutiveFailures = 0;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public HeartbeatMonitor(String localNodeId, NodeInfo targetPrimary, FailureCallback failureCallback) {
        this(localNodeId, targetPrimary, failureCallback, 500, 2);
    }

    public HeartbeatMonitor(String localNodeId, NodeInfo targetPrimary, FailureCallback failureCallback,
                            long intervalMs, int maxFailures) {
        this.localNodeId = localNodeId;
        this.targetPrimary = targetPrimary;
        this.failureCallback = failureCallback;
        this.intervalMs = intervalMs;
        this.maxFailures = maxFailures;
    }

    public synchronized void start() {
        if (running.get()) return;
        running.set(true);
        consecutiveFailures = 0;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HeartbeatMonitor-" + localNodeId);
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(this::checkHeartbeat, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        System.out.printf("[HEARTBEAT] Node %s started heartbeat monitor targeting PRIMARY Node %s (interval=%dms)\n",
                localNodeId, targetPrimary != null ? targetPrimary.getNodeId() : "NONE", intervalMs);
    }

    public synchronized void stop() {
        if (!running.get()) return;
        running.set(false);
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        System.out.printf("[HEARTBEAT] Node %s stopped heartbeat monitor.\n", localNodeId);
    }

    public synchronized void updateTarget(NodeInfo newPrimary) {
        this.targetPrimary = newPrimary;
        this.consecutiveFailures = 0;
        System.out.printf("[HEARTBEAT] Node %s updated monitor target to PRIMARY Node %s\n",
                localNodeId, newPrimary != null ? newPrimary.getNodeId() : "NONE");
    }

    public boolean isRunning() {
        return running.get();
    }

    private void checkHeartbeat() {
        if (!running.get() || targetPrimary == null) return;

        try {
            OrderNodeRemote stub = (OrderNodeRemote) Naming.lookup(targetPrimary.getRmiUrl());
            stub.heartbeat(localNodeId);
            consecutiveFailures = 0;
            targetPrimary.setHealthStatus("ALIVE");
        } catch (Exception e) {
            consecutiveFailures++;
            System.out.printf("[HEARTBEAT WARN] Node %s missed heartbeat from PRIMARY Node %s (%d/%d): %s\n",
                    localNodeId, targetPrimary.getNodeId(), consecutiveFailures, maxFailures, e.getMessage());

            if (consecutiveFailures >= maxFailures) {
                targetPrimary.setHealthStatus("DEAD");
                System.out.printf("[HEARTBEAT TIMEOUT] Node %s confirmed PRIMARY Node %s is DEAD!\n",
                        localNodeId, targetPrimary.getNodeId());
                stop();
                if (failureCallback != null) {
                    failureCallback.onPrimaryFailure(targetPrimary);
                }
            }
        }
    }
}
