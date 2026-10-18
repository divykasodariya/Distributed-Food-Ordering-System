package com.foodordering.monitoring;

import com.foodordering.model.SystemEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Global in-memory broadcast bus for real-time distributed system telemetry.
 * Buffers recent events and notifies SSE/HTTP consumers.
 */
public class EventBroadcaster {

    private static final EventBroadcaster INSTANCE = new EventBroadcaster();
    private static final int MAX_BUFFER_SIZE = 500;

    private final List<SystemEvent> eventHistory = Collections.synchronizedList(new ArrayList<>());
    private final List<Consumer<SystemEvent>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong eventSequence = new AtomicLong(1);

    private EventBroadcaster() {}

    public static EventBroadcaster getInstance() {
        return INSTANCE;
    }

    public SystemEvent broadcast(String category, String sourceNode, String targetNode,
                                 long lamportTime, Integer orderId, String details, boolean success) {
        String eventId = "SYS-EVT-" + eventSequence.getAndIncrement();
        long physicalTime = System.currentTimeMillis();

        SystemEvent event = new SystemEvent(
            eventId, category, sourceNode, targetNode,
            lamportTime, physicalTime, orderId, details, success
        );

        synchronized (eventHistory) {
            eventHistory.add(event);
            if (eventHistory.size() > MAX_BUFFER_SIZE) {
                eventHistory.remove(0);
            }
        }

        for (Consumer<SystemEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception ignored) {}
        }

        return event;
    }

    public List<SystemEvent> getRecentEvents(int limit) {
        synchronized (eventHistory) {
            int size = eventHistory.size();
            int start = Math.max(0, size - limit);
            return new ArrayList<>(eventHistory.subList(start, size));
        }
    }

    public void addListener(Consumer<SystemEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<SystemEvent> listener) {
        listeners.remove(listener);
    }
}
