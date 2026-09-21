package com.foodordering.storage;

import com.foodordering.api.node.OrderNodeRemote;
import com.foodordering.model.NodeInfo;
import com.foodordering.model.NodeRole;
import com.foodordering.model.ReplicationAck;
import com.foodordering.model.ReplicationEvent;

import java.rmi.Naming;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates application-state replication from PRIMARY to BACKUP nodes.
 * Handles synchronous replication broadcasts, explicit ACKs, and catch-up recovery.
 */
public class ReplicationManager {

    private final String localNodeId;

    public ReplicationManager(String localNodeId) {
        this.localNodeId = localNodeId;
    }

    /**
     * Broadcasts a replication event to all designated BACKUP peers.
     * Logs exact transmission and acknowledgment status.
     *
     * @param event the replication event to broadcast.
     * @param peers list of cluster peers.
     * @return list of collected acknowledgments.
     */
    public List<ReplicationAck> replicateToBackups(ReplicationEvent event, List<NodeInfo> peers) {
        List<ReplicationAck> acks = new ArrayList<>();

        for (NodeInfo peer : peers) {
            // Replicate only to BACKUP nodes
            if (peer.getRole() == NodeRole.BACKUP) {
                String targetNodeId = peer.getNodeId();
                System.out.printf("[N%s -> N%s] replication event #%d (type=%s, ver=%d, eventId=%s)\n",
                        localNodeId, targetNodeId, event.getOrderId(), event.getEventType(),
                        event.getVersion(), event.getEventId());

                try {
                    OrderNodeRemote stub = (OrderNodeRemote) Naming.lookup(peer.getRmiUrl());
                    ReplicationAck ack = stub.replicateEvent(event);
                    if (ack != null && ack.isSuccess()) {
                        System.out.printf("[N%s -> N%s] ACK (applied successfully at version %d)\n",
                                targetNodeId, localNodeId, ack.getCurrentVersion());
                        acks.add(ack);
                    } else {
                        System.out.printf("[N%s -> N%s] NACK: %s\n",
                                targetNodeId, localNodeId, ack != null ? ack.getStatusMessage() : "null response");
                    }
                } catch (Exception e) {
                    System.out.printf("[REPLICATION WARNING] N%s unavailable for replication: %s. " +
                            "(N%s will synchronize missing events upon restart)\n",
                            targetNodeId, e.getMessage(), targetNodeId);
                }
            }
        }
        return acks;
    }

    /**
     * Synchronizes missing historical events from the PRIMARY node when rejoining as BACKUP.
     * Reconstructs local state and updates the local append-only event log.
     *
     * @param primaryRmiUrl RMI URL of the primary node.
     * @param log local EventLog instance.
     * @param store local OrderStore instance.
     * @return number of missing events synchronized.
     */
    public int synchronizeFromPrimary(String primaryRmiUrl, EventLog log, OrderStore store) {
        long localVersion = log.getLatestVersion();
        System.out.printf("[CATCH-UP] Rejoining as BACKUP. Local version is %d. Contacting PRIMARY at %s...\n",
                localVersion, primaryRmiUrl);

        try {
            OrderNodeRemote primaryStub = (OrderNodeRemote) Naming.lookup(primaryRmiUrl);
            List<ReplicationEvent> missing = primaryStub.getMissingEvents(localVersion);
            System.out.printf("[CATCH-UP] Primary reported %d missing event(s) to apply.\n", missing.size());

            int appliedCount = 0;
            for (ReplicationEvent ev : missing) {
                boolean applied = store.apply(ev);
                if (applied) {
                    log.append(ev);
                    appliedCount++;
                }
            }
            System.out.printf("[CATCH-UP] Successfully synchronized and persisted %d missing event(s). Local version now: %d\n",
                    appliedCount, log.getLatestVersion());
            return appliedCount;
        } catch (Exception e) {
            System.err.printf("[CATCH-UP WARNING] Failed synchronizing from PRIMARY at %s: %s\n",
                    primaryRmiUrl, e.getMessage());
            return 0;
        }
    }
}
