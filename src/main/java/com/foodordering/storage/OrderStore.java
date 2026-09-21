package com.foodordering.storage;

import com.foodordering.model.DistributedEvent;
import com.foodordering.model.Order;
import com.foodordering.model.OrderSnapshot;
import com.foodordering.model.ReplicationEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the in-memory order state for an Order Node using ConcurrentHashMap.
 * Ensures deterministic and idempotent state mutations when applying replicated events.
 */
public class OrderStore {

    private final ConcurrentHashMap<Integer, Order> orders = new ConcurrentHashMap<>();
    private final Set<String> appliedEvents = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public Order get(int orderId) {
        return orders.get(orderId);
    }

    public OrderSnapshot getSnapshot(int orderId) {
        Order order = orders.get(orderId);
        return order != null ? order.toSnapshot() : null;
    }

    public boolean containsOrder(int orderId) {
        return orders.containsKey(orderId);
    }

    public boolean isEventApplied(String eventId) {
        return appliedEvents.contains(eventId);
    }

    public Map<Integer, Order> getOrdersMap() {
        return orders;
    }

    public int size() {
        return orders.size();
    }

    public void clear() {
        orders.clear();
        appliedEvents.clear();
    }

    /**
     * Applies a replication event to the in-memory store.
     * Enforces idempotency: if eventId was already applied, ignores application and returns false.
     *
     * @param event the event to apply.
     * @return true if applied, false if already applied (duplicate).
     */
    public synchronized boolean apply(ReplicationEvent event) {
        if (event == null) return false;

        if (appliedEvents.contains(event.getEventId())) {
            return false; // Idempotent: duplicate event ignored
        }

        int orderId = event.getOrderId();
        switch (event.getEventType()) {
            case ORDER_CREATED -> {
                Order order = new Order(
                        orderId,
                        event.getCustomerName(),
                        event.getRestaurantName(),
                        event.getItemName(),
                        event.getQuantity()
                );
                DistributedEvent de = new DistributedEvent(
                        event.getEventId(),
                        orderId,
                        event.getEventType(),
                        event.getCustomerName(),
                        event.getLamportTimestamp()
                );
                order.addEvent(de);
                orders.put(orderId, order);
            }
            case RIDER_ACCEPTED -> {
                Order order = orders.get(orderId);
                if (order != null) {
                    order.addAcceptance(
                            event.getRiderId(),
                            event.getPhysicalTimestamp() != null ? event.getPhysicalTimestamp() : event.getTimestamp(),
                            event.getTimestamp(),
                            event.getLamportTimestamp()
                    );
                    DistributedEvent de = new DistributedEvent(
                            event.getEventId(),
                            orderId,
                            event.getEventType(),
                            event.getRiderId(),
                            event.getLamportTimestamp(),
                            event.getPhysicalTimestamp()
                    );
                    order.addEvent(de);
                }
            }
            case ASSIGNED -> {
                Order order = orders.get(orderId);
                if (order != null) {
                    order.finalizeAssignment();
                    DistributedEvent de = new DistributedEvent(
                            event.getEventId(),
                            orderId,
                            event.getEventType(),
                            event.getSourceNodeId(),
                            event.getLamportTimestamp()
                    );
                    order.addEvent(de);
                }
            }
            case RIDER_ARRIVED -> {
                Order order = orders.get(orderId);
                if (order != null) {
                    long physTime = event.getPhysicalTimestamp() != null ? event.getPhysicalTimestamp() : event.getTimestamp();
                    order.arriveAtRestaurant(event.getRiderId(), physTime);
                    DistributedEvent de = new DistributedEvent(
                            event.getEventId(),
                            orderId,
                            event.getEventType(),
                            event.getRiderId(),
                            event.getLamportTimestamp()
                    );
                    order.addEvent(de);
                }
            }
            case DELIVERED -> {
                Order order = orders.get(orderId);
                if (order != null) {
                    order.deliverOrder(event.getRiderId());
                    DistributedEvent de = new DistributedEvent(
                            event.getEventId(),
                            orderId,
                            event.getEventType(),
                            event.getSourceNodeId(),
                            event.getLamportTimestamp()
                    );
                    order.addEvent(de);
                }
            }
            default -> {
                // Future event types
            }
        }

        appliedEvents.add(event.getEventId());
        return true;
    }
}
