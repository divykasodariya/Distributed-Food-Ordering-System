package com.foodordering.node;

import com.foodordering.api.gateway.GatewayRemote;
import com.foodordering.api.node.OrderNodeRemote;
import com.foodordering.clock.LamportClock;
import com.foodordering.config.ClusterConfig;
import com.foodordering.election.BullyElectionManager;
import com.foodordering.model.EventType;
import com.foodordering.model.NodeInfo;
import com.foodordering.model.NodeRole;
import com.foodordering.model.Order;
import com.foodordering.model.OrderSnapshot;
import com.foodordering.model.ReplicationAck;
import com.foodordering.model.ReplicationEvent;
import com.foodordering.monitoring.HeartbeatMonitor;
import com.foodordering.storage.EventLog;
import com.foodordering.storage.OrderStore;
import com.foodordering.storage.ReplicationManager;
import com.foodordering.util.RmiUtils;

import java.io.IOException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Order Node server process implementation.
 * Orchestrates local OrderStore, EventLog persistence, ReplicationManager,
 * HeartbeatMonitor, and BullyElectionManager.
 */
public class OrderNodeServer extends UnicastRemoteObject implements OrderNodeRemote {
    private static final long serialVersionUID = 1L;

    private final NodeInfo nodeInfo;
    private final List<NodeInfo> peers = Collections.synchronizedList(new ArrayList<>());
    private final LamportClock lamportClock;

    // Storage and replication components
    private final OrderStore orderStore;
    private final EventLog eventLog;
    private final ReplicationManager replicationManager;

    // Monitoring and election components
    private final BullyElectionManager bullyElectionManager;
    private final HeartbeatMonitor heartbeatMonitor;

    // Thread pool for processing concurrent orders without uncontrolled thread creation
    private final ExecutorService orderExecutor = Executors.newFixedThreadPool(4);

    // Sequence counter for deterministic distributed order IDs: (nodeId * 1000) + sequence
    private int orderSequence = 1;
    private final AtomicLong versionSequence = new AtomicLong(0);

    private volatile long lastHeartbeatTime = System.currentTimeMillis();

    public OrderNodeServer(String nodeId, String host, int port, NodeRole role) throws RemoteException {
        super(port);
        this.nodeInfo = new NodeInfo(nodeId, host, port, role, "ALIVE");
        this.lamportClock = new LamportClock("OrderNode-" + nodeId);
        this.orderStore = new OrderStore();
        this.eventLog = new EventLog(nodeId, null);
        this.replicationManager = new ReplicationManager(nodeId);

        // Bully Election and Heartbeat Monitoring setup
        this.bullyElectionManager = new BullyElectionManager(nodeId, peers, new BullyElectionManager.ElectionListener() {
            @Override
            public void onBecameLeader() {
                try {
                    setRole(NodeRole.PRIMARY);
                    if (heartbeatMonitor != null) {
                        heartbeatMonitor.stop();
                    }
                } catch (RemoteException ignored) {}
            }

            @Override
            public void onLeaderElected(String leaderId) {
                try {
                    setRole(NodeRole.BACKUP);
                    for (NodeInfo peer : peers) {
                        if (peer.getNodeId().equals(leaderId)) {
                            if (heartbeatMonitor != null) {
                                heartbeatMonitor.updateTarget(peer);
                                if (!heartbeatMonitor.isRunning()) {
                                    heartbeatMonitor.start();
                                }
                            }
                            break;
                        }
                    }
                } catch (RemoteException ignored) {}
            }
        });

        this.heartbeatMonitor = new HeartbeatMonitor(nodeId, null, failedPrimary -> {
            System.out.printf("[N%s] Primary Node %s failure detected by HeartbeatMonitor. Initiating Bully Election...\n",
                    nodeId, failedPrimary.getNodeId());
            bullyElectionManager.startElection();
        });

        // Reconstruct version sequence from log
        this.versionSequence.set(eventLog.getLatestVersion());

        // Startup recovery: replay local EventLog into OrderStore
        rebuildStateFromLog();
    }

    private void rebuildStateFromLog() {
        System.out.printf("[N%s] loading EventLog from %s...\n", nodeInfo.getNodeId(), eventLog.getLogFile().getPath());
        List<ReplicationEvent> events = eventLog.replayAll();
        for (ReplicationEvent ev : events) {
            orderStore.apply(ev);
        }
        System.out.printf("[N%s] rebuilding state: %d event(s) applied, %d active order(s) reconstructed in OrderStore.\n",
                nodeInfo.getNodeId(), events.size(), orderStore.size());
    }

    public void addPeer(NodeInfo peer) {
        peers.add(peer);
    }

    public List<NodeInfo> getPeers() {
        return Collections.unmodifiableList(peers);
    }

    public LamportClock getLamportClock() {
        return lamportClock;
    }

    public OrderStore getOrderStore() {
        return orderStore;
    }

    public EventLog getEventLog() {
        return eventLog;
    }

    /**
     * Internal helper to persist an event locally and replicate to backups.
     */
    private void persistAndReplicate(ReplicationEvent event) {
        // 1. Update local OrderStore
        orderStore.apply(event);
        System.out.printf("[N%s] local state updated for Order #%d (%s)\n",
                nodeInfo.getNodeId(), event.getOrderId(), event.getEventType());

        // 2. Append to local EventLog
        try {
            eventLog.append(event);
            System.out.printf("[N%s] event persisted to %s\n",
                    nodeInfo.getNodeId(), eventLog.getLogFile().getName());
        } catch (IOException e) {
            System.err.printf("[N%s ERROR] Failed to persist event: %s\n", nodeInfo.getNodeId(), e.getMessage());
        }

        // 3. Replicate to BACKUP nodes via ReplicationManager
        replicationManager.replicateToBackups(event, peers);
    }

    @Override
    public int placeOrder(String customerName, String restaurantName, String itemName, int quantity, long clientLamport) throws RemoteException {
        if (nodeInfo.getRole() != NodeRole.PRIMARY) {
            throw new RemoteException("[REJECTED] Node " + nodeInfo.getNodeId() +
                    " is a BACKUP node and cannot process write requests directly.");
        }

        // Lamport Rule: on receive, L = max(L, clientLamport) + 1
        long eventLamport = lamportClock.updateOnReceive(clientLamport);

        final int orderId;
        synchronized (this) {
            int nodeNum = 1;
            try {
                nodeNum = Integer.parseInt(nodeInfo.getNodeId());
            } catch (NumberFormatException ignored) {}
            orderId = (nodeNum * 1000) + (orderSequence++);
        }

        long version = versionSequence.incrementAndGet();
        String eventId = "EVT-" + nodeInfo.getNodeId() + "-" + version;

        ReplicationEvent createEvent = new ReplicationEvent(
                eventId,
                version,
                orderId,
                EventType.ORDER_CREATED,
                customerName,
                restaurantName,
                itemName,
                quantity,
                null,
                null,
                eventLamport,
                nodeInfo.getNodeId(),
                System.currentTimeMillis()
        );

        // Update local state, persist, and replicate
        persistAndReplicate(createEvent);

        System.out.printf("\n[ORDER CREATED]\nOrder=#%d\nSource=%s\nLamport=%d\n",
                orderId, customerName, eventLamport);

        // Async background order preparation
        orderExecutor.submit(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
            System.out.printf("[%s] Order #%d prepared and WAITING_FOR_RIDER on Node %s\n",
                    threadName, orderId, nodeInfo.getNodeId());
        });

        return orderId;
    }

    @Override
    public OrderSnapshot getOrderStatus(int orderId) throws RemoteException {
        return orderStore.getSnapshot(orderId);
    }

    @Override
    public boolean acceptDelivery(int orderId, String riderId, long physicalTimestamp, long receiptTimestamp, long riderLamport) throws RemoteException {
        if (nodeInfo.getRole() != NodeRole.PRIMARY) {
            throw new RemoteException("[REJECTED] Node " + nodeInfo.getNodeId() +
                    " is a BACKUP node and cannot accept delivery bids directly.");
        }

        long eventLamport = lamportClock.updateOnReceive(riderLamport);

        Order order = orderStore.get(orderId);
        if (order == null) {
            System.out.printf("[ACCEPTANCE FAILED] Order #%d not found on Node %s\n", orderId, nodeInfo.getNodeId());
            return false;
        }

        long version = versionSequence.incrementAndGet();
        String eventId = "EVT-" + nodeInfo.getNodeId() + "-" + version;

        ReplicationEvent acceptEvent = new ReplicationEvent(
                eventId,
                version,
                orderId,
                EventType.RIDER_ACCEPTED,
                order.getCustomerName(),
                order.getRestaurantName(),
                order.getItemName(),
                order.getQuantity(),
                riderId,
                physicalTimestamp,
                eventLamport,
                nodeInfo.getNodeId(),
                receiptTimestamp
        );

        persistAndReplicate(acceptEvent);

        System.out.printf("\n[RIDER ACCEPTED]\nOrder=#%d\nSource=%s\nPhysicalTime=%d\nLamport=%d\n",
                orderId, riderId, physicalTimestamp, eventLamport);
        return true;
    }

    @Override
    public String finalizeAssignment(int orderId) throws RemoteException {
        if (nodeInfo.getRole() != NodeRole.PRIMARY) {
            throw new RemoteException("[REJECTED] Node " + nodeInfo.getNodeId() +
                    " is a BACKUP node and cannot finalize assignments.");
        }

        Order order = orderStore.get(orderId);
        if (order == null) {
            return "Order #" + orderId + " not found.";
        }

        long assignLamport = lamportClock.tick();
        String result = order.finalizeAssignment();

        if (order.getAssignedRider() != null) {
            long version = versionSequence.incrementAndGet();
            String eventId = "EVT-" + nodeInfo.getNodeId() + "-" + version;

            ReplicationEvent assignEvent = new ReplicationEvent(
                    eventId,
                    version,
                    orderId,
                    EventType.ASSIGNED,
                    order.getCustomerName(),
                    order.getRestaurantName(),
                    order.getItemName(),
                    order.getQuantity(),
                    order.getAssignedRider(),
                    null,
                    assignLamport,
                    nodeInfo.getNodeId(),
                    System.currentTimeMillis()
            );

            persistAndReplicate(assignEvent);

            System.out.printf("\n[ASSIGNMENT]\nOrder=#%d\nSource=OrderNode-%s\nLamport=%d\nResult: %s\n",
                    orderId, nodeInfo.getNodeId(), assignLamport, result);
        }
        return result;
    }

    @Override
    public boolean arriveAtRestaurant(int orderId, String riderId, long physicalTimestamp, long riderLamport) throws RemoteException {
        if (nodeInfo.getRole() != NodeRole.PRIMARY) {
            throw new RemoteException("[REJECTED] Node " + nodeInfo.getNodeId() + " is a BACKUP node.");
        }

        long arriveLamport = lamportClock.updateOnReceive(riderLamport);
        Order order = orderStore.get(orderId);
        if (order == null) return false;

        boolean arrivedOnTime = order.arriveAtRestaurant(riderId, physicalTimestamp);
        if (arrivedOnTime) {
            long version = versionSequence.incrementAndGet();
            String eventId = "EVT-" + nodeInfo.getNodeId() + "-" + version;

            ReplicationEvent arriveEvent = new ReplicationEvent(
                    eventId,
                    version,
                    orderId,
                    EventType.RIDER_ARRIVED,
                    order.getCustomerName(),
                    order.getRestaurantName(),
                    order.getItemName(),
                    order.getQuantity(),
                    riderId,
                    physicalTimestamp,
                    arriveLamport,
                    nodeInfo.getNodeId(),
                    System.currentTimeMillis()
            );

            persistAndReplicate(arriveEvent);

            System.out.printf("\n[RIDER ARRIVED]\nOrder=#%d\nSource=%s\nLamport=%d\n",
                    orderId, riderId, arriveLamport);
        }
        return arrivedOnTime;
    }

    @Override
    public boolean deliverOrder(int orderId, String riderId, long riderLamport) throws RemoteException {
        if (nodeInfo.getRole() != NodeRole.PRIMARY) {
            throw new RemoteException("[REJECTED] Node " + nodeInfo.getNodeId() + " is a BACKUP node.");
        }

        long deliverLamport = lamportClock.updateOnReceive(riderLamport);
        Order order = orderStore.get(orderId);
        if (order == null) return false;

        boolean delivered = order.deliverOrder(riderId);
        if (delivered) {
            long version = versionSequence.incrementAndGet();
            String eventId = "EVT-" + nodeInfo.getNodeId() + "-" + version;

            ReplicationEvent deliverEvent = new ReplicationEvent(
                    eventId,
                    version,
                    orderId,
                    EventType.DELIVERED,
                    order.getCustomerName(),
                    order.getRestaurantName(),
                    order.getItemName(),
                    order.getQuantity(),
                    riderId,
                    null,
                    deliverLamport,
                    nodeInfo.getNodeId(),
                    System.currentTimeMillis()
            );

            persistAndReplicate(deliverEvent);

            System.out.printf("\n[DELIVERED]\nOrder=#%d\nSource=OrderNode-%s\nLamport=%d\n",
                    orderId, nodeInfo.getNodeId(), deliverLamport);
        }
        return delivered;
    }

    @Override
    public ReplicationAck replicateEvent(ReplicationEvent event) throws RemoteException {
        // Advance local Lamport clock upon receiving replicated message
        lamportClock.updateOnReceive(event.getLamportTimestamp());

        System.out.printf("[N%s] received replication event %s (Order=#%d, Type=%s, Ver=%d)\n",
                nodeInfo.getNodeId(), event.getEventId(), event.getOrderId(), event.getEventType(), event.getVersion());

        // Idempotent state application
        boolean applied = orderStore.apply(event);
        if (applied) {
            System.out.printf("[N%s] applied event %s to OrderStore\n",
                    nodeInfo.getNodeId(), event.getEventId());
            try {
                eventLog.append(event);
                System.out.printf("[N%s] persisted %s to data/node%s/events.log\n",
                        nodeInfo.getNodeId(), event.getEventId(), nodeInfo.getNodeId());
                if (event.getVersion() > versionSequence.get()) {
                    versionSequence.set(event.getVersion());
                }
            } catch (IOException e) {
                System.err.printf("[N%s ERROR] Disk write failure for %s: %s\n",
                        nodeInfo.getNodeId(), event.getEventId(), e.getMessage());
                return new ReplicationAck(nodeInfo.getNodeId(), event.getEventId(), false,
                        "Disk write failure: " + e.getMessage(), eventLog.getLatestVersion());
            }
        } else {
            System.out.printf("[N%s] duplicate event ignored (Event=%s, Ver=%d)\n",
                    nodeInfo.getNodeId(), event.getEventId(), event.getVersion());
        }

        System.out.printf("[N%s] ACK sent to Node %s for %s\n",
                nodeInfo.getNodeId(), event.getSourceNodeId(), event.getEventId());

        return new ReplicationAck(nodeInfo.getNodeId(), event.getEventId(), true,
                applied ? "Applied" : "Duplicate ignored", eventLog.getLatestVersion());
    }

    @Override
    public List<ReplicationEvent> getMissingEvents(long afterVersion) throws RemoteException {
        return eventLog.getEventsAfter(afterVersion);
    }

    @Override
    public long getLatestLogVersion() throws RemoteException {
        return eventLog.getLatestVersion();
    }

    @Override
    public NodeInfo getNodeInfo() throws RemoteException {
        return nodeInfo;
    }

    @Override
    public void setRole(NodeRole newRole) throws RemoteException {
        NodeRole oldRole = nodeInfo.getRole();
        nodeInfo.setRole(newRole);
        System.out.printf("[ROLE CHANGE] Node %s changed role from %s to %s\n",
                nodeInfo.getNodeId(), oldRole, newRole);
        if (newRole == NodeRole.PRIMARY) {
            if (heartbeatMonitor != null) {
                heartbeatMonitor.stop();
            }
            long latest = eventLog.getLatestVersion();
            if (latest > versionSequence.get()) {
                versionSequence.set(latest);
            }
        }
    }

    @Override
    public long ping() throws RemoteException {
        this.lastHeartbeatTime = System.currentTimeMillis();
        return this.lastHeartbeatTime;
    }

    @Override
    public boolean receiveElection(String fromNodeId) throws RemoteException {
        return bullyElectionManager.handleElectionMessage(fromNodeId);
    }

    @Override
    public void receiveCoordinator(String coordinatorNodeId) throws RemoteException {
        bullyElectionManager.handleCoordinatorMessage(coordinatorNodeId);
    }

    @Override
    public boolean heartbeat(String fromNodeId) throws RemoteException {
        this.lastHeartbeatTime = System.currentTimeMillis();
        return true;
    }

    public BullyElectionManager getBullyElectionManager() {
        return bullyElectionManager;
    }

    public HeartbeatMonitor getHeartbeatMonitor() {
        return heartbeatMonitor;
    }

    public void stopServer() {
        if (heartbeatMonitor != null) {
            heartbeatMonitor.stop();
        }
        orderExecutor.shutdownNow();
    }

    public static OrderNodeServer startServer(String nodeId, NodeRole role, int port) {
        try {
            String host = ClusterConfig.getHost();
            System.setProperty("java.rmi.server.hostname", host);

            Registry registry = RmiUtils.getOrCreateRegistry(port);
            OrderNodeServer server = new OrderNodeServer(nodeId, host, port, role);

            int[] allNodePorts = {ClusterConfig.NODE_1_PORT, ClusterConfig.NODE_2_PORT, ClusterConfig.NODE_3_PORT};
            for (int i = 1; i <= 3; i++) {
                if (i != Integer.parseInt(nodeId)) {
                    server.addPeer(new NodeInfo(String.valueOf(i), host, allNodePorts[i - 1],
                            (i == 1 ? NodeRole.PRIMARY : NodeRole.BACKUP), "UNKNOWN"));
                }
            }

            // If starting as BACKUP, contact PRIMARY to synchronize missing events and start HeartbeatMonitor
            if (role == NodeRole.BACKUP) {
                NodeInfo primaryPeer = null;
                try {
                    GatewayRemote gateway = (GatewayRemote) Naming.lookup(ClusterConfig.getGatewayUrl());
                    String primaryId = gateway.getPrimaryNodeId();
                    for (NodeInfo peer : server.getPeers()) {
                        if (peer.getNodeId().equals(primaryId)) {
                            primaryPeer = peer;
                            break;
                        }
                    }
                } catch (Exception ignored) {}

                if (primaryPeer == null) {
                    for (NodeInfo peer : server.getPeers()) {
                        if (peer.getRole() == NodeRole.PRIMARY) {
                            primaryPeer = peer;
                            break;
                        }
                    }
                }
                if (primaryPeer == null && !server.getPeers().isEmpty()) {
                    primaryPeer = server.getPeers().get(0);
                }

                if (primaryPeer != null) {
                    System.out.printf("synchronizing missing events from %s (Node %s)...\n",
                            primaryPeer.getRmiUrl(), primaryPeer.getNodeId());
                    server.replicationManager.synchronizeFromPrimary(primaryPeer.getRmiUrl(), server.getEventLog(), server.getOrderStore());
                    server.versionSequence.set(server.getEventLog().getLatestVersion());
                    System.out.printf("rejoining as %s.\n", role);

                    server.heartbeatMonitor.updateTarget(primaryPeer);
                    server.heartbeatMonitor.start();
                }
            }

            String bindName = "OrderNode" + nodeId;
            registry.rebind(bindName, server);

            System.out.println("=================================================");
            System.out.printf("  Order Node %s Started Successfully\n", nodeId);
            System.out.printf("  Role: %s\n", role);
            System.out.printf("  RMI Port: %d\n", port);
            System.out.printf("  RMI URL: rmi://%s:%d/%s\n", host, port, bindName);
            System.out.printf("  Storage: data/node%s/events.log (Latest version: %d)\n", nodeId, server.getEventLog().getLatestVersion());
            System.out.println("=================================================");
            return server;

        } catch (Exception e) {
            System.err.println("Failed to start OrderNodeServer: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        String nodeId = args.length > 0 ? args[0] : ClusterConfig.NODE_1_ID;
        NodeRole role = (args.length > 1 && args[1].equalsIgnoreCase("BACKUP")) ? NodeRole.BACKUP : NodeRole.PRIMARY;
        int port = args.length > 2 ? Integer.parseInt(args[2]) : (1100 + Integer.parseInt(nodeId));
        startServer(nodeId, role, port);
    }
}
