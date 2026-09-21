package com.foodordering.node;

import com.foodordering.config.ClusterConfig;
import com.foodordering.model.NodeRole;

/**
 * Runner for Order Node 2 (Initial BACKUP on port 1102).
 */
public class OrderNode2 {
    public static void main(String[] args) {
        OrderNodeServer.startServer(ClusterConfig.NODE_2_ID, NodeRole.BACKUP, ClusterConfig.NODE_2_PORT);
    }
}
