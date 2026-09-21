package com.foodordering.clock;

import com.foodordering.api.gateway.GatewayRemote;

import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Implements physical clock synchronization using Cristian's algorithm with RTT adjustment.
 *
 * Concepts:
 *   t0 = local time before request
 *   serverTime = server physical timestamp received during request
 *   t1 = local time after response
 *   RTT = t1 - t0
 *   estimated server time at t1 ≈ serverTime + RTT / 2
 *   clockOffset = estimatedServerTime - t1 = serverTime - (t0 + t1) / 2
 *
 * Then any local action time can be adjusted to estimated physical time via:
 *   getEstimatedPhysicalTime() = System.currentTimeMillis() + clockOffset
 *
 * Notes:
 * - Assumes symmetric network latency; accuracy is bounded within +/- (RTT / 2).
 * - Does NOT guarantee exact physical time.
 * - Does NOT prove which RMI network packet arrived at the server first.
 */
public class PhysicalClockSynchronizer {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss.SSS");

    private final GatewayRemote gateway;
    private long clockOffset = 0;
    private long lastRtt = 0;
    private boolean synchronizedOnce = false;

    public PhysicalClockSynchronizer(GatewayRemote gateway) {
        this.gateway = gateway;
    }

    /**
     * Executes Cristian's clock synchronization request and updates the local clock offset.
     */
    public synchronized void synchronize() throws RemoteException {
        System.out.println("\n--- [CRISTIAN PHYSICAL CLOCK SYNCHRONIZATION] ---");
        long t0 = System.currentTimeMillis();
        long serverTime = gateway.getServerTime();
        long t1 = System.currentTimeMillis();

        this.lastRtt = t1 - t0;
        long oneWayDelay = lastRtt / 2;
        long estimatedServerTimeAtT1 = serverTime + oneWayDelay;

        // clockOffset = estimatedServerTimeAtT1 - t1
        this.clockOffset = estimatedServerTimeAtT1 - t1;
        this.synchronizedOnce = true;

        System.out.printf("Client Request Sent (t0)     : %s (%d ms)\n", SDF.format(new Date(t0)), t0);
        System.out.printf("Server Physical Timestamp (Ts): %s (%d ms)\n", SDF.format(new Date(serverTime)), serverTime);
        System.out.printf("Client Response Recv (t1)    : %s (%d ms)\n", SDF.format(new Date(t1)), t1);
        System.out.printf("Round Trip Time (RTT)        : %d ms\n", lastRtt);
        System.out.printf("Estimated One-Way Delay      : %d ms\n", oneWayDelay);
        System.out.printf("Estimated Server Time at t1  : %s (%d ms)\n", SDF.format(new Date(estimatedServerTimeAtT1)), estimatedServerTimeAtT1);
        System.out.printf("Calculated Clock Offset      : %+d ms\n", clockOffset);
        System.out.printf("Current Synchronized Time    : %s\n", SDF.format(new Date(getEstimatedPhysicalTime())));
        System.out.printf("(Accuracy bounded within +/- %.1f ms, assuming symmetric latency)\n", lastRtt / 2.0);
        System.out.println("-------------------------------------------------");
    }

    /**
     * Returns the estimated synchronized physical timestamp at the current moment.
     */
    public long getEstimatedPhysicalTime() {
        return System.currentTimeMillis() + clockOffset;
    }

    public long getClockOffset() {
        return clockOffset;
    }

    public long getLastRtt() {
        return lastRtt;
    }

    public boolean isSynchronized() {
        return synchronizedOnce;
    }
}
