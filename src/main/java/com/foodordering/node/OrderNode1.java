package com.foodordering.node;

import com.foodordering.config.ClusterConfig;
import com.foodordering.model.NodeRole;

/**
 * Runner for Order Node 1 (Initial PRIMARY on port 1101).
 */
public class OrderNode1 {
    public static void main(String[] args) {
        OrderNodeServer.startServer(ClusterConfig.NODE_1_ID, NodeRole.PRIMARY, ClusterConfig.NODE_1_PORT);
    }
}
