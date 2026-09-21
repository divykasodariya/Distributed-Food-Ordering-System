package com.foodordering.demo;

import com.foodordering.clock.LamportClock;
import com.foodordering.model.DistributedEvent;
import com.foodordering.model.EventType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unit verification tests for LamportClock rules and distributed event ordering:
 * 1. Local tick rule (L = L + 1)
 * 2. Send timestamp rule (L = L + 1)
 * 3. Receive timestamp rule (L = max(L, receivedTimestamp) + 1)
 * 4. max + 1 rule boundary behaviors
 * 5. Concurrent events (identical timestamps on independent processes without causal link)
 * 6. Deterministic tie-breaking on (lamportTimestamp, sourceId)
 */
public class LamportClockTest {

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("  LAMPORT LOGICAL CLOCK UNIT TESTS & CAUSALITY VERIFICATION");
        System.out.println("===============================================================");

        int passed = 0;
        int failed = 0;

        // Test 1: Local tick rule
        LamportClock clockA = new LamportClock("Process-A", 0);
        long t1 = clockA.tick();
        long t2 = clockA.tick();
        if (t1 == 1 && t2 == 2 && clockA.getValue() == 2) {
            System.out.println("[PASS] Test 1: Local tick increments correctly (0 -> 1 -> 2)");
            passed++;
        } else {
            System.err.println("[FAIL] Test 1: Local tick failed. t1=" + t1 + ", t2=" + t2);
            failed++;
        }

        // Test 2: Send timestamp rule
        LamportClock clockB = new LamportClock("Process-B", 5);
        long sendTs = clockB.tickOnSend();
        if (sendTs == 6 && clockB.getValue() == 6) {
            System.out.println("[PASS] Test 2: Send timestamp advances clock before transmission (5 -> 6)");
            passed++;
        } else {
            System.err.println("[FAIL] Test 2: Send timestamp failed: " + sendTs);
            failed++;
        }

        // Test 3: Receive timestamp rule when received > local
        LamportClock clockC = new LamportClock("Process-C", 3);
        long recvHigher = clockC.updateOnReceive(10);
        // max(3, 10) + 1 = 11
        if (recvHigher == 11 && clockC.getValue() == 11) {
            System.out.println("[PASS] Test 3: Receive rule when received > local (max(3, 10) + 1 = 11)");
            passed++;
        } else {
            System.err.println("[FAIL] Test 3: Receive rule failed: " + recvHigher);
            failed++;
        }

        // Test 4: Receive timestamp rule when local > received
        LamportClock clockD = new LamportClock("Process-D", 15);
        long recvLower = clockD.updateOnReceive(8);
        // max(15, 8) + 1 = 16
        if (recvLower == 16 && clockD.getValue() == 16) {
            System.out.println("[PASS] Test 4: Receive rule when local > received (max(15, 8) + 1 = 16)");
            passed++;
        } else {
            System.err.println("[FAIL] Test 4: Receive rule failed: " + recvLower);
            failed++;
        }

        // Test 5: Receive timestamp rule when local == received
        LamportClock clockE = new LamportClock("Process-E", 20);
        long recvEqual = clockE.updateOnReceive(20);
        // max(20, 20) + 1 = 21
        if (recvEqual == 21 && clockE.getValue() == 21) {
            System.out.println("[PASS] Test 5: Receive rule when local == received (max(20, 20) + 1 = 21)");
            passed++;
        } else {
            System.err.println("[FAIL] Test 5: Receive rule failed: " + recvEqual);
            failed++;
        }

        // Test 6: Concurrent events demonstrate no causal order, resolved by tie-breaker
        System.out.println("\n--- Concurrent Events & Total Ordering Demonstration ---");
        LamportClock procX = new LamportClock("Rider-R1", 0);
        LamportClock procY = new LamportClock("Rider-R2", 0);

        // Both processes perform concurrent independent local actions
        long tsX = procX.tick(); // Rider-R1 Lamport = 1
        long tsY = procY.tick(); // Rider-R2 Lamport = 1

        System.out.printf("Rider-R1 Event Timestamp: %d\n", tsX);
        System.out.printf("Rider-R2 Event Timestamp: %d\n", tsY);
        System.out.println("Notice: Both have Lamport timestamp = 1, but neither caused the other (Concurrent events).");
        System.out.println("Lamport timestamps DO NOT prove one happened earlier in physical time!");

        DistributedEvent ev1 = new DistributedEvent("EVT-1", 1001, EventType.RIDER_ACCEPTED, "Rider-R2", tsY);
        DistributedEvent ev2 = new DistributedEvent("EVT-2", 1001, EventType.RIDER_ACCEPTED, "Rider-R1", tsX);

        List<DistributedEvent> eventList = new ArrayList<>();
        eventList.add(ev1);
        eventList.add(ev2);
        Collections.sort(eventList);

        System.out.println("Deterministic Total Order resolved via tie-breaker (lamportTimestamp, sourceId):");
        for (DistributedEvent e : eventList) {
            System.out.println("  -> " + e);
        }

        if (eventList.get(0).getSourceId().equals("Rider-R1") && eventList.get(1).getSourceId().equals("Rider-R2")) {
            System.out.println("[PASS] Test 6: Tie-breaker deterministically ordered Rider-R1 before Rider-R2");
            passed++;
        } else {
            System.err.println("[FAIL] Test 6: Tie-breaker failed");
            failed++;
        }

        System.out.println("\n===============================================================");
        System.out.printf("  LAMPORT CLOCK TESTS SUMMARY: %d PASSED, %d FAILED\n", passed, failed);
        System.out.println("===============================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }
}
