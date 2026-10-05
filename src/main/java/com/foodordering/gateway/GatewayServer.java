package com.foodordering.gateway;

import com.foodordering.api.gateway.GatewayRemote;
import com.foodordering.api.node.OrderNodeRemote;
import com.foodordering.clock.LamportClock;
import com.foodordering.config.ClusterConfig;
import com.foodordering.model.NodeInfo;
import com.foodordering.model.NodeRole;
import com.foodordering.model.OrderSnapshot;
import com.foodordering.model.Restaurant;
import com.foodordering.util.RmiUtils;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Gateway Server process.
 * Acts as the single entry point for Customer and Rider clients.
 * Routes client operations to the current PRIMARY OrderNode, propagating Lamport logical clocks.
 * Serves server physical timestamps for Cristian's clock synchronization.
 */
public class GatewayServer extends UnicastRemoteObject implements GatewayRemote {
    private static final long serialVersionUID = 1L;

    private final LamportClock lamportClock = new LamportClock("GatewayServer");
    private final List<NodeInfo> nodes = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Restaurant> restaurantCatalog = Restaurant.getDefaultRestaurants();

    public GatewayServer() throws RemoteException {
        super(ClusterConfig.GATEWAY_PORT);

        String host = ClusterConfig.getHost();
        nodes.add(new NodeInfo(ClusterConfig.NODE_1_ID, host, ClusterConfig.NODE_1_PORT, NodeRole.PRIMARY, "ALIVE"));
        nodes.add(new NodeInfo(ClusterConfig.NODE_2_ID, host, ClusterConfig.NODE_2_PORT, NodeRole.BACKUP, "ALIVE"));
        nodes.add(new NodeInfo(ClusterConfig.NODE_3_ID, host, ClusterConfig.NODE_3_PORT, NodeRole.BACKUP, "ALIVE"));
    }

    private final Map<String, Integer> currentWeights = Collections.synchronizedMap(new java.util.HashMap<>());

    public synchronized NodeInfo getPrimaryNodeInfo() throws RemoteException {
        for (NodeInfo info : nodes) {
            if (info.getRole() == NodeRole.PRIMARY) {
                return info;
            }
        }
        throw new RemoteException("[GATEWAY ERROR] No PRIMARY Order Node is currently designated in the cluster topology.");
    }

    /**
     * Selects an available node for READ operations using Smooth Weighted Round-Robin load balancing.
     * PRIMARY node receives weight = 1 (reserving CPU/disk capacity for writes and replication).
     * BACKUP nodes receive weight = 3 (utilizing idle backup capacity for read offloading).
     */
    private OrderNodeRemote getNextReadStub() throws RemoteException {
        synchronized (this) {
            List<NodeInfo> availableNodes = new ArrayList<>();
            for (NodeInfo info : nodes) {
                if (!"UNREACHABLE".equalsIgnoreCase(info.getHealthStatus())) {
                    availableNodes.add(info);
                }
            }

            if (availableNodes.isEmpty()) {
                return getPrimaryStub();
            }

            int totalWeight = 0;
            NodeInfo selectedNode = null;
            int maxCurrentWeight = Integer.MIN_VALUE;

            for (NodeInfo info : availableNodes) {
                int baseWeight = (info.getRole() == NodeRole.PRIMARY) ? 1 : 3;
                int currentWeight = currentWeights.getOrDefault(info.getNodeId(), 0) + baseWeight;
                currentWeights.put(info.getNodeId(), currentWeight);
                totalWeight += baseWeight;

                if (currentWeight > maxCurrentWeight) {
                    maxCurrentWeight = currentWeight;
                    selectedNode = info;
                }
            }

            if (selectedNode != null) {
                currentWeights.put(selectedNode.getNodeId(), maxCurrentWeight - totalWeight);

                try {
                    OrderNodeRemote stub = (OrderNodeRemote) Naming.lookup(selectedNode.getRmiUrl());
                    System.out.printf("[GATEWAY LOAD BALANCER] Routing read request getOrderStatus() -> Node %s (%s, WRR weight: %d)\n",
                            selectedNode.getNodeId(), selectedNode.getRole(),
                            (selectedNode.getRole() == NodeRole.PRIMARY) ? 1 : 3);
                    return stub;
                } catch (Exception e) {
                    System.out.printf("[GATEWAY LOAD BALANCER] Node %s is UNREACHABLE during read lookup. Failing over to Primary...\n",
                            selectedNode.getNodeId());
                    selectedNode.setHealthStatus("UNREACHABLE");
                }
            }

            return getPrimaryStub();
        }
    }

    private OrderNodeRemote getPrimaryStub() throws RemoteException {
        NodeInfo primary = getPrimaryNodeInfo();
        try {
            return (OrderNodeRemote) Naming.lookup(primary.getRmiUrl());
        } catch (Exception e) {
            primary.setHealthStatus("UNREACHABLE");
            System.out.printf("[GATEWAY FAILOVER] Primary Node %s is UNREACHABLE (%s). Scanning for active leader...\n",
                    primary.getNodeId(), e.getMessage());
            // Scan other nodes to see if one has been elected PRIMARY
            for (NodeInfo info : nodes) {
                if (!info.getNodeId().equals(primary.getNodeId())) {
                    try {
                        OrderNodeRemote candidate = (OrderNodeRemote) Naming.lookup(info.getRmiUrl());
                        NodeInfo candidateInfo = candidate.getNodeInfo();
                        if (candidateInfo.getRole() == NodeRole.PRIMARY) {
                            System.out.printf("[GATEWAY FAILOVER] Discovered active PRIMARY Node %s! Updating routing table...\n",
                                    info.getNodeId());
                            notifyLeaderChanged(info.getNodeId());
                            return candidate;
                        }
                    } catch (Exception ignored) {}
                }
            }
            throw new RemoteException(String.format(
                    "[GATEWAY ERROR] Primary Node %s failed and no active PRIMARY could be reached in the cluster.",
                    primary.getNodeId()), e);
        }
    }

    @Override
    public synchronized void notifyLeaderChanged(String newLeaderNodeId) throws RemoteException {
        System.out.printf("\n==================================================\n" +
                        "  [GATEWAY ROUTING UPDATE] Primary Node updated -> Node %s\n" +
                        "==================================================\n", newLeaderNodeId);
        currentWeights.clear();
        for (NodeInfo info : nodes) {
            if (info.getNodeId().equals(newLeaderNodeId)) {
                info.setRole(NodeRole.PRIMARY);
                info.setHealthStatus("ALIVE");
            } else {
                info.setRole(NodeRole.BACKUP);
            }
        }
    }

    @Override
    public synchronized String getPrimaryNodeId() throws RemoteException {
        return getPrimaryNodeInfo().getNodeId();
    }

    @Override
    public long getServerTime() throws RemoteException {
        return System.currentTimeMillis();
    }

    @Override
    public String getMenu() throws RemoteException {
        StringBuilder sb = new StringBuilder();
        sb.append("\n================= RESTAURANT MENUS =================\n");
        for (Restaurant r : restaurantCatalog.values()) {
            sb.append(String.format("Restaurant: %s (ID: %s)\n", r.getName(), r.getId()));
            for (Map.Entry<String, Double> item : r.getMenu().entrySet()) {
                sb.append(String.format("   - %-20s : $%.2f\n", item.getKey(), item.getValue()));
            }
            sb.append("----------------------------------------------------\n");
        }
        sb.append("====================================================\n");
        return sb.toString();
    }

    @Override
    public int placeOrder(String customerName, String restaurantName, String itemName, int quantity, long clientLamport) throws RemoteException {
        lamportClock.updateOnReceive(clientLamport);
        long fwdLamport = lamportClock.tickOnSend();

        NodeInfo primary = getPrimaryNodeInfo();
        System.out.printf("[GATEWAY ROUTING] Routing placeOrder(customer='%s', item='%s', Lamport: in=%d, out=%d) -> PRIMARY Node %s\n",
                customerName, itemName, clientLamport, fwdLamport, primary.getNodeId());
        OrderNodeRemote stub = getPrimaryStub();
        return stub.placeOrder(customerName, restaurantName, itemName, quantity, fwdLamport);
    }

    @Override
    public OrderSnapshot getOrderStatus(int orderId) throws RemoteException {
        OrderNodeRemote stub = getNextReadStub();
        return stub.getOrderStatus(orderId);
    }

    @Override
    public boolean acceptDelivery(int orderId, String riderId, long physicalTimestamp, long riderLamport) throws RemoteException {
        lamportClock.updateOnReceive(riderLamport);
        long fwdLamport = lamportClock.tickOnSend();

        NodeInfo primary = getPrimaryNodeInfo();
        long receiptTimestamp = System.currentTimeMillis();
        System.out.printf("[GATEWAY ROUTING] Routing acceptDelivery(order=#%d, rider='%s', physicalTime=%d, Lamport: in=%d, out=%d) -> PRIMARY Node %s\n",
                orderId, riderId, physicalTimestamp, riderLamport, fwdLamport, primary.getNodeId());
        OrderNodeRemote stub = getPrimaryStub();
        return stub.acceptDelivery(orderId, riderId, physicalTimestamp, receiptTimestamp, fwdLamport);
    }

    @Override
    public String finalizeAssignment(int orderId) throws RemoteException {
        NodeInfo primary = getPrimaryNodeInfo();
        System.out.printf("[GATEWAY ROUTING] Routing finalizeAssignment(order=#%d) -> PRIMARY Node %s\n",
                orderId, primary.getNodeId());
        OrderNodeRemote stub = getPrimaryStub();
        return stub.finalizeAssignment(orderId);
    }

    @Override
    public boolean arriveAtRestaurant(int orderId, String riderId, long physicalTimestamp, long riderLamport) throws RemoteException {
        lamportClock.updateOnReceive(riderLamport);
        long fwdLamport = lamportClock.tickOnSend();

        NodeInfo primary = getPrimaryNodeInfo();
        System.out.printf("[GATEWAY ROUTING] Routing arriveAtRestaurant(order=#%d, rider='%s', Lamport: in=%d, out=%d) -> PRIMARY Node %s\n",
                orderId, riderId, riderLamport, fwdLamport, primary.getNodeId());
        OrderNodeRemote stub = getPrimaryStub();
        return stub.arriveAtRestaurant(orderId, riderId, physicalTimestamp, fwdLamport);
    }

    @Override
    public boolean deliverOrder(int orderId, String riderId, long riderLamport) throws RemoteException {
        lamportClock.updateOnReceive(riderLamport);
        long fwdLamport = lamportClock.tickOnSend();

        NodeInfo primary = getPrimaryNodeInfo();
        System.out.printf("[GATEWAY ROUTING] Routing deliverOrder(order=#%d, rider='%s', Lamport: in=%d, out=%d) -> PRIMARY Node %s\n",
                orderId, riderId, riderLamport, fwdLamport, primary.getNodeId());
        OrderNodeRemote stub = getPrimaryStub();
        return stub.deliverOrder(orderId, riderId, fwdLamport);
    }

    @Override
    public List<NodeInfo> getNodeStatuses() throws RemoteException {
        List<NodeInfo> statuses = new ArrayList<>();
        for (NodeInfo info : nodes) {
            try {
                OrderNodeRemote stub = (OrderNodeRemote) Naming.lookup(info.getRmiUrl());
                stub.ping();
                info.setHealthStatus("ALIVE");
            } catch (Exception e) {
                info.setHealthStatus("UNREACHABLE");
            }
            statuses.add(info);
        }
        return statuses;
    }

    public static void main(String[] args) {
        try {
            String host = ClusterConfig.getHost();
            System.setProperty("java.rmi.server.hostname", host);

            Registry registry = RmiUtils.getOrCreateRegistry(ClusterConfig.GATEWAY_PORT);

            GatewayServer server = new GatewayServer();
            registry.rebind(ClusterConfig.GATEWAY_SERVICE_NAME, server);

            System.out.println("=================================================");
            System.out.println("  Gateway Server Running on port " + ClusterConfig.GATEWAY_PORT);
            System.out.println("  RMI Endpoint: " + ClusterConfig.getGatewayUrl());
            System.out.println("  Initial PRIMARY Node: Node 1 (port " + ClusterConfig.NODE_1_PORT + ")");
            System.out.println("  Initial BACKUP Nodes : Node 2 (" + ClusterConfig.NODE_2_PORT + "), Node 3 (" + ClusterConfig.NODE_3_PORT + ")");
            System.out.println("=================================================");
        } catch (Exception e) {
            System.err.println("Failed to start GatewayServer: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
