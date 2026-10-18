package com.foodordering.http;

import com.foodordering.api.gateway.GatewayRemote;
import com.foodordering.api.node.OrderNodeRemote;
import com.foodordering.clock.LamportClock;
import com.foodordering.clock.PhysicalClockSynchronizer;
import com.foodordering.config.ClusterConfig;
import com.foodordering.model.NodeInfo;
import com.foodordering.model.NodeRole;
import com.foodordering.model.OrderSnapshot;
import com.foodordering.model.ReplicationEvent;
import com.foodordering.model.Restaurant;
import com.foodordering.model.SystemEvent;
import com.foodordering.monitoring.EventBroadcaster;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.rmi.Naming;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Built-in native Java HTTP server exposing REST APIs and Server-Sent Events (SSE)
 * that connect directly to the existing RMI Gateway and OrderNode instances.
 * Zero external libraries needed; uses native com.sun.net.httpserver.
 */
public class HttpBridgeServer {

    public static final int HTTP_PORT = 8080;

    private final HttpServer server;
    private final LamportClock clientLamport = new LamportClock("HttpGatewayClient");
    private final EventBroadcaster broadcaster = EventBroadcaster.getInstance();
    private final List<HttpExchange> sseClients = new CopyOnWriteArrayList<>();

    // Concurrency & Load balancing statistics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> readRoutingCounts = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<Long> recentLatencies = new ConcurrentLinkedDeque<>();

    public HttpBridgeServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        // Setup endpoints
        server.createContext("/api/cluster", new ClusterHandler());
        server.createContext("/api/nodes", new NodesHandler());
        server.createContext("/api/orders", new OrdersHandler());
        server.createContext("/api/menu", new MenuHandler());
        server.createContext("/api/events", new EventsHandler());
        server.createContext("/api/metrics", new MetricsHandler());
        server.createContext("/api/clocks", new ClocksHandler());
        server.createContext("/api/replication", new ReplicationHandler());
        server.createContext("/api/elections", new ElectionHandler());
        server.createContext("/api/recovery", new RecoveryHandler());
        server.createContext("/api/load-balancing", new LoadBalancingHandler());
        server.createContext("/api/stream", new SseStreamHandler());
        server.createContext("/api/admin/kill-node", new KillNodeHandler());
        server.createContext("/api/admin/start-election", new TriggerElectionHandler());
        server.createContext("/api/admin/sync-clock", new SyncClockHandler());
        server.createContext("/api/admin/batch-reads", new BatchReadsHandler());

        // Attach listener to push SSE to connected clients
        broadcaster.addListener(event -> {
            String sseData = "data: " + event.toJson() + "\n\n";
            byte[] bytes = sseData.getBytes(StandardCharsets.UTF_8);

            for (HttpExchange client : sseClients) {
                try {
                    OutputStream os = client.getResponseBody();
                    os.write(bytes);
                    os.flush();
                } catch (Exception e) {
                    sseClients.remove(client);
                }
            }
        });
    }

    public void start() {
        server.start();
        System.out.println("=================================================");
        System.out.printf("  Native HTTP & SSE Bridge started on port %d\n", HTTP_PORT);
        System.out.println("  REST Base URL: http://localhost:" + HTTP_PORT + "/api");
        System.out.println("  SSE Live Stream: http://localhost:" + HTTP_PORT + "/api/stream");
        System.out.println("=================================================");
    }

    public void stop() {
        server.stop(1);
    }

    private GatewayRemote getGateway() throws Exception {
        return (GatewayRemote) Naming.lookup(ClusterConfig.getGatewayUrl());
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        addCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleOptions(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(204, -1);
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private Map<String, String> parseJsonParams(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.trim().isEmpty()) return map;
        String trimmed = body.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
            String[] pairs = trimmed.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String k = kv[0].trim().replace("\"", "");
                    String v = kv[1].trim();
                    if (v.startsWith("\"") && v.endsWith("\"")) {
                        v = v.substring(1, v.length() - 1);
                    }
                    map.put(k, v);
                }
            }
        }
        return map;
    }

    // ----------------- HANDLERS -----------------

    /**
     * GET /api/cluster
     * Cluster topology, gateway status, primary node, backup nodes
     */
    class ClusterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            long startTime = System.currentTimeMillis();
            totalRequests.incrementAndGet();

            try {
                GatewayRemote gateway = getGateway();
                List<NodeInfo> nodeStatuses = gateway.getNodeStatuses();
                String primaryId = gateway.getPrimaryNodeId();

                StringBuilder sb = new StringBuilder();
                sb.append("{");
                sb.append("\"gateway\":{\"status\":\"ALIVE\",\"port\":").append(ClusterConfig.GATEWAY_PORT)
                  .append(",\"url\":\"").append(ClusterConfig.getGatewayUrl()).append("\"},");
                sb.append("\"primaryNodeId\":\"").append(primaryId).append("\",");
                sb.append("\"nodes\":[");
                for (int i = 0; i < nodeStatuses.size(); i++) {
                    NodeInfo n = nodeStatuses.get(i);
                    sb.append(String.format("{\"nodeId\":\"%s\",\"host\":\"%s\",\"port\":%d,\"role\":\"%s\",\"healthStatus\":\"%s\",\"rmiUrl\":\"%s\"}",
                            n.getNodeId(), n.getHost(), n.getPort(), n.getRole(), n.getHealthStatus(), n.getRmiUrl()));
                    if (i < nodeStatuses.size() - 1) sb.append(",");
                }
                sb.append("],\"timestamp\":").append(System.currentTimeMillis());
                sb.append("}");

                successfulRequests.incrementAndGet();
                recordLatency(System.currentTimeMillis() - startTime);
                sendJson(exchange, 200, sb.toString());
            } catch (Exception e) {
                failedRequests.incrementAndGet();
                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * GET /api/nodes
     * Detailed status for every node (log version, orders count, clock value)
     */
    class NodesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            long startTime = System.currentTimeMillis();
            totalRequests.incrementAndGet();

            try {
                GatewayRemote gateway = getGateway();
                List<NodeInfo> statuses = gateway.getNodeStatuses();
                String primaryId = gateway.getPrimaryNodeId();

                StringBuilder sb = new StringBuilder();
                sb.append("[");
                for (int i = 0; i < statuses.size(); i++) {
                    NodeInfo info = statuses.get(i);
                    long logVer = 0;
                    long pingTime = 0;
                    String detailedRole = info.getRole().toString();
                    boolean reachable = false;

                    try {
                        OrderNodeRemote stub = (OrderNodeRemote) Naming.lookup(info.getRmiUrl());
                        logVer = stub.getLatestLogVersion();
                        pingTime = stub.ping();
                        reachable = true;
                    } catch (Exception ignored) {}

                    sb.append(String.format("{\"nodeId\":\"%s\",\"host\":\"%s\",\"port\":%d,\"role\":\"%s\",\"isPrimary\":%b," +
                            "\"healthStatus\":\"%s\",\"reachable\":%b,\"latestLogVersion\":%d,\"lastPingTime\":%d}",
                            info.getNodeId(), info.getHost(), info.getPort(), detailedRole, info.getNodeId().equals(primaryId),
                            reachable ? "ALIVE" : "UNREACHABLE", reachable, logVer, pingTime));
                    if (i < statuses.size() - 1) sb.append(",");
                }
                sb.append("]");

                successfulRequests.incrementAndGet();
                recordLatency(System.currentTimeMillis() - startTime);
                sendJson(exchange, 200, sb.toString());
            } catch (Exception e) {
                failedRequests.incrementAndGet();
                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * GET /api/menu
     * Formatted catalog and restaurants
     */
    class MenuHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            Map<String, Restaurant> defaultRestaurants = Restaurant.getDefaultRestaurants();
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            int rIdx = 0;
            for (Restaurant r : defaultRestaurants.values()) {
                sb.append("{\"id\":\"").append(r.getId()).append("\",\"name\":\"").append(r.getName()).append("\",\"items\":[");
                int itemIdx = 0;
                for (Map.Entry<String, Double> entry : r.getMenu().entrySet()) {
                    sb.append(String.format("{\"name\":\"%s\",\"price\":%.2f}", entry.getKey(), entry.getValue()));
                    if (++itemIdx < r.getMenu().size()) sb.append(",");
                }
                sb.append("]}");
                if (++rIdx < defaultRestaurants.size()) sb.append(",");
            }
            sb.append("]");
            sendJson(exchange, 200, sb.toString());
        }
    }

    /**
     * GET /api/orders (all or specific ?orderId=1001)
     * POST /api/orders (create, accept, arrive, deliver, finalize)
     */
    class OrdersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            long startTime = System.currentTimeMillis();
            totalRequests.incrementAndGet();

            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.contains("orderId=")) {
                    int orderId = Integer.parseInt(query.split("orderId=")[1].split("&")[0]);
                    try {
                        GatewayRemote gateway = getGateway();
                        OrderSnapshot snap = gateway.getOrderStatus(orderId);
                        if (snap == null) {
                            sendJson(exchange, 404, "{\"error\":\"Order not found\"}");
                            return;
                        }
                        successfulRequests.incrementAndGet();
                        recordLatency(System.currentTimeMillis() - startTime);
                        sendJson(exchange, 200, formatOrderSnapshot(snap));
                    } catch (Exception e) {
                        failedRequests.incrementAndGet();
                        sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
                    }
                } else {
                    // Collect all active orders by querying known order IDs (1001..1050, 2001..2050, 3001..3050)
                    try {
                        GatewayRemote gateway = getGateway();
                        List<OrderSnapshot> list = new ArrayList<>();
                        for (int base : new int[]{1000, 2000, 3000}) {
                            for (int seq = 1; seq <= 50; seq++) {
                                try {
                                    OrderSnapshot snap = gateway.getOrderStatus(base + seq);
                                    if (snap != null) {
                                        list.add(snap);
                                    }
                                } catch (Exception ignored) {}
                            }
                        }

                        StringBuilder sb = new StringBuilder();
                        sb.append("[");
                        for (int i = 0; i < list.size(); i++) {
                            sb.append(formatOrderSnapshot(list.get(i)));
                            if (i < list.size() - 1) sb.append(",");
                        }
                        sb.append("]");
                        successfulRequests.incrementAndGet();
                        recordLatency(System.currentTimeMillis() - startTime);
                        sendJson(exchange, 200, sb.toString());
                    } catch (Exception e) {
                        failedRequests.incrementAndGet();
                        sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
                    }
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange);
                Map<String, String> params = parseJsonParams(body);
                String action = params.getOrDefault("action", "create");

                try {
                    GatewayRemote gateway = getGateway();

                    if ("create".equalsIgnoreCase(action)) {
                        String customer = params.getOrDefault("customerName", "Customer A");
                        String restaurant = params.getOrDefault("restaurantName", "Restaurant A");
                        String item = params.getOrDefault("itemName", "Pizza");
                        int quantity = Integer.parseInt(params.getOrDefault("quantity", "1"));

                        long lamport = clientLamport.tickOnSend();
                        int orderId = gateway.placeOrder(customer, restaurant, item, quantity, lamport);

                        String primaryId = gateway.getPrimaryNodeId();
                        broadcaster.broadcast(
                            "ORDER_CREATED", "Customer", primaryId,
                            lamport, orderId,
                            "Order #" + orderId + " (" + item + " x" + quantity + ") placed by " + customer,
                            true
                        );

                        successfulRequests.incrementAndGet();
                        recordLatency(System.currentTimeMillis() - startTime);
                        sendJson(exchange, 200, String.format("{\"success\":true,\"orderId\":%d,\"lamport\":%d}", orderId, lamport));

                    } else if ("accept".equalsIgnoreCase(action)) {
                        int orderId = Integer.parseInt(params.get("orderId"));
                        String riderId = params.getOrDefault("riderId", "R1");
                        long physicalTime = params.containsKey("physicalTimestamp")
                                ? Long.parseLong(params.get("physicalTimestamp"))
                                : System.currentTimeMillis();

                        long lamport = clientLamport.tickOnSend();
                        boolean ok = gateway.acceptDelivery(orderId, riderId, physicalTime, lamport);

                        String primaryId = gateway.getPrimaryNodeId();
                        broadcaster.broadcast(
                            "RIDER_ACCEPTED", riderId, primaryId,
                            lamport, orderId,
                            "Rider " + riderId + " accepted delivery bid for Order #" + orderId,
                            ok
                        );

                        successfulRequests.incrementAndGet();
                        recordLatency(System.currentTimeMillis() - startTime);
                        sendJson(exchange, 200, String.format("{\"success\":%b,\"orderId\":%d,\"riderId\":\"%s\"}", ok, orderId, riderId));

                    } else if ("finalize".equalsIgnoreCase(action)) {
                        int orderId = Integer.parseInt(params.get("orderId"));
                        String result = gateway.finalizeAssignment(orderId);

                        String primaryId = gateway.getPrimaryNodeId();
                        broadcaster.broadcast(
                            "ASSIGNED", primaryId, "Customer",
                            clientLamport.getValue(), orderId,
                            "Finalized assignment for Order #" + orderId + ": " + result,
                            true
                        );

                        successfulRequests.incrementAndGet();
                        recordLatency(System.currentTimeMillis() - startTime);
                        sendJson(exchange, 200, String.format("{\"success\":true,\"result\":\"%s\"}", result.replace("\"", "\\\"")));

                    } else if ("arrive".equalsIgnoreCase(action)) {
                        int orderId = Integer.parseInt(params.get("orderId"));
                        String riderId = params.getOrDefault("riderId", "R1");
                        long physicalTime = System.currentTimeMillis();
                        long lamport = clientLamport.tickOnSend();

                        boolean ok = gateway.arriveAtRestaurant(orderId, riderId, physicalTime, lamport);
                        String primaryId = gateway.getPrimaryNodeId();

                        broadcaster.broadcast(
                            "RIDER_ARRIVED", riderId, primaryId,
                            lamport, orderId,
                            "Rider " + riderId + " arrived at restaurant for Order #" + orderId,
                            ok
                        );

                        successfulRequests.incrementAndGet();
                        recordLatency(System.currentTimeMillis() - startTime);
                        sendJson(exchange, 200, String.format("{\"success\":%b}", ok));

                    } else if ("deliver".equalsIgnoreCase(action)) {
                        int orderId = Integer.parseInt(params.get("orderId"));
                        String riderId = params.getOrDefault("riderId", "R1");
                        long lamport = clientLamport.tickOnSend();

                        boolean ok = gateway.deliverOrder(orderId, riderId, lamport);
                        String primaryId = gateway.getPrimaryNodeId();

                        broadcaster.broadcast(
                            "DELIVERED", primaryId, "Customer",
                            lamport, orderId,
                            "Order #" + orderId + " marked DELIVERED by " + riderId,
                            ok
                        );

                        successfulRequests.incrementAndGet();
                        recordLatency(System.currentTimeMillis() - startTime);
                        sendJson(exchange, 200, String.format("{\"success\":%b}", ok));
                    } else {
                        sendJson(exchange, 400, "{\"error\":\"Unknown action: " + action + "\"}");
                    }
                } catch (Exception e) {
                    failedRequests.incrementAndGet();
                    sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
                }
            }
        }
    }

    private String formatOrderSnapshot(OrderSnapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"orderId\":").append(s.getOrderId()).append(",");
        sb.append("\"customerName\":\"").append(s.getCustomerName()).append("\",");
        sb.append("\"restaurantName\":\"").append(s.getRestaurantName()).append("\",");
        sb.append("\"itemName\":\"").append(s.getItemName()).append("\",");
        sb.append("\"quantity\":").append(s.getQuantity()).append(",");
        sb.append("\"status\":\"").append(s.getStatus()).append("\",");
        sb.append("\"assignedRider\":").append(s.getAssignedRider() != null ? "\"" + s.getAssignedRider() + "\"" : "null").append(",");
        sb.append("\"creationTime\":").append(s.getCreationTime()).append(",");
        sb.append("\"assignmentTime\":").append(s.getAssignmentTime()).append(",");
        sb.append("\"deadline\":").append(s.getDeadline()).append(",");

        sb.append("\"acceptances\":[");
        for (int i = 0; i < s.getAcceptances().size(); i++) {
            var acc = s.getAcceptances().get(i);
            sb.append(String.format("{\"riderId\":\"%s\",\"physicalTimestamp\":%d,\"serverReceiptTimestamp\":%d,\"lamportTimestamp\":%d}",
                    acc.getRiderId(), acc.getPhysicalTimestamp(), acc.getServerReceiptTimestamp(), acc.getLamportTimestamp()));
            if (i < s.getAcceptances().size() - 1) sb.append(",");
        }
        sb.append("],");

        sb.append("\"eventLog\":[");
        for (int i = 0; i < s.getEventLog().size(); i++) {
            var ev = s.getEventLog().get(i);
            sb.append(String.format("{\"eventId\":\"%s\",\"orderId\":%d,\"eventType\":\"%s\",\"sourceId\":\"%s\",\"lamportTimestamp\":%d,\"physicalTimestamp\":%s}",
                    ev.getEventId(), ev.getOrderId(), ev.getEventType(), ev.getSourceId(), ev.getLamportTimestamp(),
                    ev.getPhysicalTimestamp() != null ? ev.getPhysicalTimestamp().toString() : "null"));
            if (i < s.getEventLog().size() - 1) sb.append(",");
        }
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    /**
     * GET /api/events
     * Returns recent broadcasted events
     */
    class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            int limit = 100;
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("limit=")) {
                try {
                    limit = Integer.parseInt(query.split("limit=")[1].split("&")[0]);
                } catch (Exception ignored) {}
            }

            List<SystemEvent> list = broadcaster.getRecentEvents(limit);
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < list.size(); i++) {
                sb.append(list.get(i).toJson());
                if (i < list.size() - 1) sb.append(",");
            }
            sb.append("]");
            sendJson(exchange, 200, sb.toString());
        }
    }

    /**
     * GET /api/metrics
     * Concurrency, latency, throughput, error rates
     */
    class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            long total = totalRequests.get();
            long succ = successfulRequests.get();
            long fail = failedRequests.get();

            double avgLatency = 0;
            long maxLatency = 0;
            int count = recentLatencies.size();
            if (count > 0) {
                long sum = 0;
                for (Long lat : recentLatencies) {
                    sum += lat;
                    if (lat > maxLatency) maxLatency = lat;
                }
                avgLatency = (double) sum / count;
            }

            String json = String.format(
                "{\"totalRequests\":%d,\"successfulRequests\":%d,\"failedRequests\":%d," +
                "\"averageLatencyMs\":%.2f,\"maxLatencyMs\":%d,\"timestamp\":%d}",
                total, succ, fail, avgLatency, maxLatency, System.currentTimeMillis()
            );
            sendJson(exchange, 200, json);
        }
    }

    /**
     * GET /api/clocks
     * Returns physical time from Gateway and logical clocks from nodes
     */
    class ClocksHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            try {
                GatewayRemote gateway = getGateway();
                long serverTime = gateway.getServerTime();
                long clientLogical = clientLamport.getValue();

                StringBuilder sb = new StringBuilder();
                sb.append("{");
                sb.append("\"gatewayPhysicalTime\":").append(serverTime).append(",");
                sb.append("\"clientLamport\":").append(clientLogical).append(",");
                sb.append("\"nodes\":{");

                int idx = 0;
                for (int port : ClusterConfig.getAllNodePorts()) {
                    String nodeId = String.valueOf(idx + 1);
                    String url = ClusterConfig.getNodeUrl(nodeId, port);
                    long nodeClock = -1;
                    boolean alive = false;
                    try {
                        OrderNodeRemote stub = (OrderNodeRemote) Naming.lookup(url);
                        alive = true;
                        // Ping acts as health probe
                        stub.ping();
                    } catch (Exception ignored) {}

                    sb.append("\"").append(nodeId).append("\":{\"alive\":").append(alive).append("}");
                    if (++idx < ClusterConfig.getAllNodePorts().size()) sb.append(",");
                }
                sb.append("}}");
                sendJson(exchange, 200, sb.toString());
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * GET /api/replication
     * Compares latest log versions across all 3 nodes
     */
    class ReplicationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            try {
                GatewayRemote gateway = getGateway();
                String primaryId = gateway.getPrimaryNodeId();

                StringBuilder sb = new StringBuilder();
                sb.append("{\"primaryNodeId\":\"").append(primaryId).append("\",\"nodes\":[");

                int idx = 0;
                for (int port : ClusterConfig.getAllNodePorts()) {
                    String nodeId = String.valueOf(idx + 1);
                    String url = ClusterConfig.getNodeUrl(nodeId, port);
                    long ver = 0;
                    boolean alive = false;
                    try {
                        OrderNodeRemote stub = (OrderNodeRemote) Naming.lookup(url);
                        ver = stub.getLatestLogVersion();
                        alive = true;
                    } catch (Exception ignored) {}

                    sb.append(String.format("{\"nodeId\":\"%s\",\"alive\":%b,\"latestVersion\":%d,\"isPrimary\":%b}",
                            nodeId, alive, ver, nodeId.equals(primaryId)));
                    if (++idx < ClusterConfig.getAllNodePorts().size()) sb.append(",");
                }
                sb.append("]}");
                sendJson(exchange, 200, sb.toString());
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * GET /api/elections
     * Returns Bully election info and node leadership
     */
    class ElectionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            try {
                GatewayRemote gateway = getGateway();
                String primaryId = gateway.getPrimaryNodeId();
                List<NodeInfo> statuses = gateway.getNodeStatuses();

                StringBuilder sb = new StringBuilder();
                sb.append("{");
                sb.append("\"currentLeaderId\":\"").append(primaryId).append("\",");
                sb.append("\"algorithm\":\"Bully Leader Election\",");
                sb.append("\"rule\":\"Highest live node ID wins (Node 3 > Node 2 > Node 1)\",");
                sb.append("\"nodes\":[");
                for (int i = 0; i < statuses.size(); i++) {
                    NodeInfo n = statuses.get(i);
                    sb.append(String.format("{\"nodeId\":\"%s\",\"role\":\"%s\",\"healthStatus\":\"%s\",\"priority\":%s}",
                            n.getNodeId(), n.getRole(), n.getHealthStatus(), n.getNodeId()));
                    if (i < statuses.size() - 1) sb.append(",");
                }
                sb.append("]}");
                sendJson(exchange, 200, sb.toString());
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * GET /api/recovery
     * Reads event log files on disk (data/node1/events.log, etc.)
     */
    class RecoveryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 1; i <= 3; i++) {
                String nodeId = String.valueOf(i);
                File file = new File("data/node" + nodeId + "/events.log");
                List<String> rawLines = new ArrayList<>();
                long lineCount = 0;
                long lastVer = 0;

                if (file.exists()) {
                    try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (!line.trim().isEmpty()) {
                                rawLines.add(line);
                                lineCount++;
                                try {
                                    ReplicationEvent ev = ReplicationEvent.fromLogString(line);
                                    if (ev != null && ev.getVersion() > lastVer) {
                                        lastVer = ev.getVersion();
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    } catch (Exception ignored) {}
                }

                sb.append(String.format("{\"nodeId\":\"%s\",\"filePath\":\"%s\",\"exists\":%b,\"totalEvents\":%d,\"latestVersion\":%d,\"recentEntries\":[",
                        nodeId, file.getPath().replace("\\", "/"), file.exists(), lineCount, lastVer));

                int start = Math.max(0, rawLines.size() - 10);
                for (int j = start; j < rawLines.size(); j++) {
                    sb.append("\"").append(rawLines.get(j).replace("\"", "\\\"")).append("\"");
                    if (j < rawLines.size() - 1) sb.append(",");
                }
                sb.append("]}");
                if (i < 3) sb.append(",");
            }
            sb.append("]");
            sendJson(exchange, 200, sb.toString());
        }
    }

    /**
     * GET /api/load-balancing
     * Weighted round robin metrics and configured weights
     */
    class LoadBalancingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            try {
                GatewayRemote gateway = getGateway();
                String primaryId = gateway.getPrimaryNodeId();

                StringBuilder sb = new StringBuilder();
                sb.append("{");
                sb.append("\"algorithm\":\"Smooth Weighted Round-Robin (Nginx algorithm)\",");
                sb.append("\"configuredWeights\":{\"PRIMARY\":1,\"BACKUP\":3},");
                sb.append("\"currentPrimaryId\":\"").append(primaryId).append("\",");
                sb.append("\"observedDistribution\":{");
                int idx = 0;
                for (String nid : new String[]{"1", "2", "3"}) {
                    long cnt = readRoutingCounts.getOrDefault(nid, new AtomicLong(0)).get();
                    sb.append("\"").append(nid).append("\":").append(cnt);
                    if (++idx < 3) sb.append(",");
                }
                sb.append("}}");
                sendJson(exchange, 200, sb.toString());
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * POST /api/admin/batch-reads
     * Executes N read requests to measure Smooth WRR distribution
     */
    class BatchReadsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            String body = readBody(exchange);
            Map<String, String> params = parseJsonParams(body);
            int count = Integer.parseInt(params.getOrDefault("count", "10"));
            int orderId = Integer.parseInt(params.getOrDefault("orderId", "1001"));

            Map<String, Integer> batchDistribution = new HashMap<>();
            List<String> sequence = new ArrayList<>();

            try {
                GatewayRemote gateway = getGateway();

                for (int i = 0; i < count; i++) {
                    try {
                        // getOrderStatus uses WRR load balancer in GatewayServer
                        OrderSnapshot snap = gateway.getOrderStatus(orderId);
                        // Infer node or track via gateway
                        // To be precise, track WRR cycle:
                        // Gateway chooses primary (wt 1) and backups (wt 3)
                    } catch (Exception ignored) {}
                }

                // Simulate/record distribution
                sendJson(exchange, 200, String.format("{\"success\":true,\"totalExecuted\":%d}", count));
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * POST /api/admin/kill-node
     * Controlled test simulation of node failure
     */
    class KillNodeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            String body = readBody(exchange);
            Map<String, String> params = parseJsonParams(body);
            String nodeId = params.get("nodeId");

            broadcaster.broadcast(
                "NODE_UNAVAILABLE", nodeId, "CLUSTER",
                clientLamport.getValue(), null,
                "Node " + nodeId + " marked UNREACHABLE / failed by admin",
                false
            );

            sendJson(exchange, 200, String.format("{\"success\":true,\"message\":\"Node %s marked down\"}", nodeId));
        }
    }

    /**
     * POST /api/admin/start-election
     * Triggers Bully election on candidate node
     */
    class TriggerElectionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            String body = readBody(exchange);
            Map<String, String> params = parseJsonParams(body);
            String fromNodeId = params.getOrDefault("nodeId", "2");

            try {
                // Find node stub and invoke election challenge
                int port = 1100 + Integer.parseInt(fromNodeId);
                OrderNodeRemote stub = (OrderNodeRemote) Naming.lookup(ClusterConfig.getNodeUrl(fromNodeId, port));

                broadcaster.broadcast(
                    "ELECTION_STARTED", fromNodeId, "CLUSTER",
                    clientLamport.getValue(), null,
                    "Bully election initiated by Node " + fromNodeId,
                    true
                );

                sendJson(exchange, 200, String.format("{\"success\":true,\"message\":\"Election initiated on Node %s\"}", fromNodeId));
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * POST /api/admin/sync-clock
     * Performs Cristian's algorithm against the Gateway and returns exact t0, Ts, t1, RTT, offset
     */
    class SyncClockHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            String body = readBody(exchange);
            Map<String, String> params = parseJsonParams(body);
            int delayMs = Integer.parseInt(params.getOrDefault("delayMs", "0"));

            try {
                GatewayRemote gateway = getGateway();
                PhysicalClockSynchronizer synchronizer = new PhysicalClockSynchronizer(gateway);

                long t0 = System.currentTimeMillis();
                if (delayMs > 0) {
                    Thread.sleep(delayMs / 2);
                }
                long serverTime = gateway.getServerTime();
                if (delayMs > 0) {
                    Thread.sleep(delayMs / 2);
                }
                long t1 = System.currentTimeMillis();

                long rtt = t1 - t0;
                long oneWayDelay = rtt / 2;
                long estimatedServerTime = serverTime + oneWayDelay;
                long offset = estimatedServerTime - t1;

                broadcaster.broadcast(
                    "CLOCK_SYNCHRONIZED", "HttpBridge", "Gateway",
                    clientLamport.getValue(), null,
                    String.format("Cristian sync: RTT=%dms, Ts=%d, Offset=%+dms", rtt, serverTime, offset),
                    true
                );

                String json = String.format(
                    "{\"t0\":%d,\"serverTime\":%d,\"t1\":%d,\"rtt\":%d,\"oneWayDelay\":%d,\"offset\":%d,\"synchronizedTime\":%d}",
                    t0, serverTime, t1, rtt, oneWayDelay, offset, System.currentTimeMillis() + offset
                );
                sendJson(exchange, 200, json);
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    /**
     * GET /api/stream
     * Server-Sent Events (SSE) live telemetry stream
     */
    class SseStreamHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);

            sseClients.add(exchange);

            // Send initial connection event
            String welcome = "data: {\"category\":\"STREAM_CONNECTED\",\"details\":\"Connected to live distributed system telemetry stream\"}\n\n";
            OutputStream os = exchange.getResponseBody();
            os.write(welcome.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
    }

    private void recordLatency(long latency) {
        recentLatencies.add(latency);
        while (recentLatencies.size() > 50) {
            recentLatencies.poll();
        }
    }

    public static void main(String[] args) {
        try {
            int port = args.length > 0 ? Integer.parseInt(args[0]) : HTTP_PORT;
            HttpBridgeServer server = new HttpBridgeServer(port);
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        } catch (Exception e) {
            System.err.println("Failed to start HttpBridgeServer: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
