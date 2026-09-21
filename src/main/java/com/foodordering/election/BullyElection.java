package com.foodordering.election;

import com.foodordering.model.NodeInfo;

import java.util.List;

/**
 * BullyElection (alias for BullyElectionManager).
 * Coordinates Bully Leader Election across cluster nodes.
 */
public class BullyElection extends BullyElectionManager {
    public BullyElection(String localNodeId, List<NodeInfo> peers, ElectionListener listener) {
        super(localNodeId, peers, listener);
    }
}
