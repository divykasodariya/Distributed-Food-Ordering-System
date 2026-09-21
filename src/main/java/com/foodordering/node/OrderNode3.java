package com.foodordering.node;

import com.foodordering.config.ClusterConfig;
import com.foodordering.model.NodeRole;

/**
 * Runner for Order Node 3 (Initial BACKUP on port 1103).
 */
public class OrderNode3 {
    public static void main(String[] args) {
        OrderNodeServer.startServer(ClusterConfig.NODE_3_ID, NodeRole.BACKUP, ClusterConfig.NODE_3_PORT);
    }
}
