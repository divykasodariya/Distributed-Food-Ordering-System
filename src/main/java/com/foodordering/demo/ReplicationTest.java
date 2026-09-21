package com.foodordering.demo;

import com.foodordering.model.EventType;
import com.foodordering.model.Order;
import com.foodordering.model.ReplicationEvent;
import com.foodordering.storage.EventLog;
import com.foodordering.storage.OrderStore;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Unit verification tests for storage and replication:
 * 1. Local append-only persistence
 * 2. Log replay and in-memory state reconstruction
 * 3. Duplicate replication event idempotency
 * 4. Missing events catch-up slicing
 */
public class ReplicationTest {

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("  STORAGE & APPLICATION-STATE REPLICATION UNIT TESTS");
        System.out.println("===============================================================");

        int passed = 0;
        int failed = 0;

        String testDir = "data/test_node";
        EventLog eventLog = new EventLog("test", testDir);
        eventLog.clear(); // clean slate
        OrderStore orderStore = new OrderStore();

        try {
            // Test 1: Local persistence & log format
            ReplicationEvent ev1 = new ReplicationEvent(
                    "EVT-N1-1", 1, 1001, EventType.ORDER_CREATED,
                    "Customer Alice", "Restaurant A", "Pizza", 2,
                    null, null, 1, "1", System.currentTimeMillis()
            );
            eventLog.append(ev1);

            if (eventLog.getLatestVersion() == 1 && eventLog.hasEvent("EVT-N1-1")) {
                System.out.println("[PASS] Test 1: Event persisted to append-only log with version=1");
                passed++;
            } else {
                System.err.println("[FAIL] Test 1: Failed persisting event");
                failed++;
            }

            // Test 2: OrderStore state application
            boolean applied1 = orderStore.apply(ev1);
            Order order = orderStore.get(1001);
            if (applied1 && order != null && "WAITING_FOR_RIDER".equals(order.getStatus())) {
                System.out.println("[PASS] Test 2: OrderStore correctly created Order #1001 in WAITING_FOR_RIDER status");
                passed++;
            } else {
                System.err.println("[FAIL] Test 2: OrderStore apply failed");
                failed++;
            }

            // Test 3: Idempotency (duplicate event application)
            boolean appliedDuplicate = orderStore.apply(ev1);
            if (!appliedDuplicate) {
                System.out.println("[PASS] Test 3: Duplicate event idempotently rejected by OrderStore");
                passed++;
            } else {
                System.err.println("[FAIL] Test 3: Duplicate event was applied twice!");
                failed++;
            }

            // Test 4: Append state transition events
            ReplicationEvent ev2 = new ReplicationEvent(
                    "EVT-N1-2", 2, 1001, EventType.RIDER_ACCEPTED,
                    "Customer Alice", "Restaurant A", "Pizza", 2,
                    "Rider-R1", 1789999000000L, 2, "1", System.currentTimeMillis()
            );
            ReplicationEvent ev3 = new ReplicationEvent(
                    "EVT-N1-3", 3, 1001, EventType.ASSIGNED,
                    "Customer Alice", "Restaurant A", "Pizza", 2,
                    "Rider-R1", null, 3, "1", System.currentTimeMillis()
            );

            eventLog.append(ev2);
            eventLog.append(ev3);
            orderStore.apply(ev2);
            orderStore.apply(ev3);

            if ("ASSIGNED".equals(orderStore.get(1001).getStatus()) && "Rider-R1".equals(orderStore.get(1001).getAssignedRider())) {
                System.out.println("[PASS] Test 4: Order state transitioned to ASSIGNED with Rider-R1");
                passed++;
            } else {
                System.err.println("[FAIL] Test 4: State transition failed");
                failed++;
            }

            // Test 5: Replay from disk into fresh OrderStore (simulating crash recovery)
            OrderStore recoveredStore = new OrderStore();
            List<ReplicationEvent> replayed = eventLog.replayAll();
            for (ReplicationEvent ev : replayed) {
                recoveredStore.apply(ev);
            }

            Order recoveredOrder = recoveredStore.get(1001);
            if (recoveredOrder != null && "ASSIGNED".equals(recoveredOrder.getStatus()) &&
                    "Rider-R1".equals(recoveredOrder.getAssignedRider()) && replayed.size() == 3) {
                System.out.println("[PASS] Test 5: Fresh OrderStore reconstructed from EventLog replay (3 events)");
                passed++;
            } else {
                System.err.println("[FAIL] Test 5: Recovery from log replay failed");
                failed++;
            }

            // Test 6: Slicing missing events for catch-up (getEventsAfter)
            List<ReplicationEvent> catchUpEvents = eventLog.getEventsAfter(1); // after version 1
            if (catchUpEvents.size() == 2 && catchUpEvents.get(0).getVersion() == 2 && catchUpEvents.get(1).getVersion() == 3) {
                System.out.println("[PASS] Test 6: getEventsAfter(1) correctly returned 2 missing events (versions 2 and 3)");
                passed++;
            } else {
                System.err.println("[FAIL] Test 6: Catch-up slicing failed: size=" + catchUpEvents.size());
                failed++;
            }

        } catch (IOException e) {
            e.printStackTrace();
            failed++;
        } finally {
            // Clean up test directory
            eventLog.clear();
            new File(testDir).delete();
        }

        System.out.println("\n===============================================================");
        System.out.printf("  REPLICATION TESTS SUMMARY: %d PASSED, %d FAILED\n", passed, failed);
        System.out.println("===============================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }
}
