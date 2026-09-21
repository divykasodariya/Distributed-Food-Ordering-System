package com.foodordering.election;

import com.foodordering.api.gateway.GatewayRemote;
import com.foodordering.api.node.OrderNodeRemote;
import com.foodordering.config.ClusterConfig;
import com.foodordering.model.NodeInfo;

import java.rmi.Naming;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implements the classic Bully Leader Election algorithm in distributed Java RMI.
 * 
 * Rules:
 * 1. A node initiates election by sending an ELECTION message to all peers with higher IDs.
 * 2. If any higher peer responds OK, the sender yields and waits for a COORDINATOR announcement.
 * 3. If no higher peer responds within the timeout, the node declares victory, assumes the PRIMARY role,
 *    broadcasts COORDINATOR to all lower-ID peers, and registers with GatewayServer.
 * 4. When a higher node receives an ELECTION message, it responds OK and initiates its own election.
 */
public class BullyElectionManager {

    public interface ElectionListener {
        void onBecameLeader();
        void onLeaderElected(String leaderId);
    }

    private final String localNodeId;
    private final int localNumericId;
    private final List<NodeInfo> peers;
    private final ElectionListener listener;
    private final AtomicBoolean electionInProgress = new AtomicBoolean(false);
    private final ExecutorService electionExecutor = Executors.newCachedThreadPool();

    public BullyElectionManager(String localNodeId, List<NodeInfo> peers, ElectionListener listener) {
        this.localNodeId = localNodeId;
        this.localNumericId = parseNumericId(localNodeId);
        this.peers = peers;
        this.listener = listener;
    }

    private int parseNumericId(String id) {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Initiates the distributed Bully election asynchronously.
     */
    public void startElection() {
        if (!electionInProgress.compareAndSet(false, true)) {
            System.out.printf("[BULLY ELECTION] Node %s already has an election in progress.\n", localNodeId);
            return;
        }

        electionExecutor.submit(this::runElectionProcess);
    }

    private void runElectionProcess() {
        System.out.printf("\n==================================================\n" +
                        "  [BULLY ELECTION] Node %s initiated Leader Election!\n" +
                        "==================================================\n", localNodeId);

        List<NodeInfo> higherPeers = new ArrayList<>();
        for (NodeInfo peer : peers) {
            int peerNum = parseNumericId(peer.getNodeId());
            if (peerNum > localNumericId) {
                higherPeers.add(peer);
            }
        }

        boolean higherNodeAnswered = false;

        if (!higherPeers.isEmpty()) {
            System.out.printf("[BULLY ELECTION] Node %s challenging %d higher node(s)...\n",
                    localNodeId, higherPeers.size());

            for (NodeInfo higher : higherPeers) {
                try {
                    System.out.printf("[BULLY ELECTION] [N%s -> N%s] Sending ELECTION message...\n",
                            localNodeId, higher.getNodeId());
                    OrderNodeRemote stub = (OrderNodeRemote) Naming.lookup(higher.getRmiUrl());
                    boolean ok = stub.receiveElection(localNodeId);
                    if (ok) {
                        System.out.printf("[BULLY ELECTION] [N%s <- N%s] Received OK answer from higher node.\n",
                                localNodeId, higher.getNodeId());
                        higherNodeAnswered = true;
                    }
                } catch (Exception e) {
                    System.out.printf("[BULLY ELECTION] Higher Node %s did not respond (%s). Presumed down.\n",
                            higher.getNodeId(), e.getMessage());
                }
            }
        }

        if (higherNodeAnswered) {
            System.out.printf("[BULLY ELECTION] Node %s yielding to higher node(s). Waiting for COORDINATOR announcement...\n", localNodeId);
            electionInProgress.set(false);
        } else {
            // No higher node responded or this is the highest node: WE WIN!
            declareVictory();
        }
    }

    private void declareVictory() {
        System.out.printf("\n==================================================\n" +
                        "  [BULLY ELECTION] Node %s WON the election!\n" +
                        "  Transitioning role to PRIMARY / COORDINATOR.\n" +
                        "==================================================\n", localNodeId);

        if (listener != null) {
            listener.onBecameLeader();
        }

        // 1. Announce COORDINATOR to all other peers
        for (NodeInfo peer : peers) {
            try {
                System.out.printf("[BULLY ELECTION] [N%s -> N%s] Announcing COORDINATOR...\n",
                        localNodeId, peer.getNodeId());
                OrderNodeRemote stub = (OrderNodeRemote) Naming.lookup(peer.getRmiUrl());
                stub.receiveCoordinator(localNodeId);
            } catch (Exception e) {
                System.out.printf("[BULLY ELECTION] Peer Node %s unreachable for COORDINATOR announcement (%s)\n",
                        peer.getNodeId(), e.getMessage());
            }
        }

        // 2. Register new PRIMARY with GatewayServer
        try {
            System.out.printf("[BULLY ELECTION] [N%s -> GATEWAY] Notifying Gateway of new PRIMARY...\n", localNodeId);
            GatewayRemote gateway = (GatewayRemote) Naming.lookup(ClusterConfig.getGatewayUrl());
            gateway.notifyLeaderChanged(localNodeId);
            System.out.println("[BULLY ELECTION] Gateway routing updated successfully.");
        } catch (Exception e) {
            System.err.printf("[BULLY ELECTION ERROR] Failed to notify GatewayServer: %s\n", e.getMessage());
        }

        electionInProgress.set(false);
    }

    /**
     * Called via RMI when a lower node challenges this node with an ELECTION message.
     */
    public boolean handleElectionMessage(String fromNodeId) {
        int senderId = parseNumericId(fromNodeId);
        System.out.printf("[BULLY ELECTION] Node %s received ELECTION from Node %s (My ID=%d, Sender ID=%d)\n",
                localNodeId, fromNodeId, localNumericId, senderId);

        if (localNumericId > senderId) {
            // Send OK back, and start our own election if not already running
            System.out.printf("[BULLY ELECTION] Node %s has higher ID than Node %s. Sending OK and taking over election.\n",
                    localNodeId, fromNodeId);
            startElection();
            return true;
        }
        return false;
    }

    /**
     * Called via RMI when the newly elected coordinator announces itself.
     */
    public void handleCoordinatorMessage(String coordinatorId) {
        System.out.printf("\n[BULLY ELECTION] Node %s acknowledged new COORDINATOR: Node %s\n",
                localNodeId, coordinatorId);
        electionInProgress.set(false);
        if (listener != null) {
            listener.onLeaderElected(coordinatorId);
        }
    }
}
