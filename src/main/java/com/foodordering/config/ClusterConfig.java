package com.foodordering.config;

import java.util.Arrays;
import java.util.List;

/**
 * Static cluster configuration parameters for the Distributed Food Ordering System.
 */
public final class ClusterConfig {

    private ClusterConfig() {}

    public static final String DEFAULT_HOST = "localhost";
    public static final int GATEWAY_PORT = 1099;
    public static final String GATEWAY_SERVICE_NAME = "GatewayService";

    public static final int NODE_1_PORT = 1101;
    public static final int NODE_2_PORT = 1102;
    public static final int NODE_3_PORT = 1103;

    public static final String NODE_1_ID = "1";
    public static final String NODE_2_ID = "2";
    public static final String NODE_3_ID = "3";

    public static String getHost() {
        return System.getProperty("java.rmi.server.hostname", DEFAULT_HOST);
    }

    public static String getGatewayUrl() {
        return "rmi://" + getHost() + ":" + GATEWAY_PORT + "/" + GATEWAY_SERVICE_NAME;
    }

    public static String getNodeUrl(String nodeId, int port) {
        return "rmi://" + getHost() + ":" + port + "/OrderNode" + nodeId;
    }

    public static List<Integer> getAllNodePorts() {
        return Arrays.asList(NODE_1_PORT, NODE_2_PORT, NODE_3_PORT);
    }
}
