package com.foodordering.model;

/**
 * Domain event types representing distributed lifecycle stages of an order.
 */
public enum EventType {
    ORDER_CREATED,
    RIDER_ACCEPTED,
    ASSIGNED,
    REPLICATED,
    RIDER_ARRIVED,
    DELIVERED
}
