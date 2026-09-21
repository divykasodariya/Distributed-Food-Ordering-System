package com.foodordering.clock;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe implementation of a Lamport Logical Clock.
 *
 * Fundamental Lamport Clock Rules:
 * 1. Local Event: L = L + 1
 * 2. Send Event: L = L + 1 before sending; attach L to outgoing message.
 * 3. Receive Event: L = max(L, receivedTimestamp) + 1 upon receiving message.
 *
 * Implemented using lock-free AtomicLong CAS updates to ensure thread safety
 * in high-concurrency multi-threaded distributed nodes.
 */
public class LamportClock {

    private final String processId;
    private final AtomicLong clock;

    public LamportClock(String processId) {
        this(processId, 0L);
    }

    public LamportClock(String processId, long initialValue) {
        this.processId = processId;
        this.clock = new AtomicLong(initialValue);
    }

    public String getProcessId() {
        return processId;
    }

    public long getValue() {
        return clock.get();
    }

    /**
     * Executes a local event tick (L = L + 1).
     * @return the updated Lamport logical timestamp.
     */
    public long tick() {
        return clock.incrementAndGet();
    }

    /**
     * Executes a send event tick (L = L + 1) to attach to an outgoing distributed message.
     * @return the logical timestamp to attach to the message.
     */
    public long tickOnSend() {
        return clock.incrementAndGet();
    }

    /**
     * Executes a receive event update (L = max(L, receivedTimestamp) + 1).
     * Uses atomic CAS to guarantee thread safety across concurrent incoming requests.
     * @param receivedTimestamp the logical timestamp carried by the incoming message.
     * @return the updated Lamport logical timestamp.
     */
    public long updateOnReceive(long receivedTimestamp) {
        return clock.updateAndGet(current -> Math.max(current, receivedTimestamp) + 1);
    }

    @Override
    public String toString() {
        return String.format("LamportClock[process=%s, value=%d]", processId, clock.get());
    }
}
