package com.foodordering.storage;

import com.foodordering.model.ReplicationEvent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Append-only persistent event log stored on the local filesystem.
 * Managed separately for each node under data/node<nodeId>/events.log.
 * Provides crash-resilient storage, startup replay, and catch-up event queries.
 */
public class EventLog {

    private final String nodeId;
    private final File logFile;
    private final Set<String> loggedEventIds = new HashSet<>();
    private long latestVersion = 0;

    public EventLog(String nodeId, String baseDir) {
        this.nodeId = nodeId;
        File dir = new File(baseDir != null ? baseDir : ("data/node" + nodeId));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.logFile = new File(dir, "events.log");
        // Initialize latestVersion and index existing events if file already exists
        replayAll();
    }

    public File getLogFile() {
        return logFile;
    }

    public synchronized long getLatestVersion() {
        return latestVersion;
    }

    public synchronized boolean hasEvent(String eventId) {
        return loggedEventIds.contains(eventId);
    }

    /**
     * Appends an event to the local append-only log file and flushes to disk.
     */
    public synchronized void append(ReplicationEvent event) throws IOException {
        if (event == null || loggedEventIds.contains(event.getEventId())) {
            return; // Idempotent: already written
        }

        try (FileOutputStream fos = new FileOutputStream(logFile, true);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(osw);
             PrintWriter pw = new PrintWriter(bw)) {

            pw.println(event.toLogString());
            pw.flush();
            fos.getFD().sync(); // Force disk sync for crash durability
        }

        loggedEventIds.add(event.getEventId());
        if (event.getVersion() > latestVersion) {
            latestVersion = event.getVersion();
        }
    }

    public synchronized List<ReplicationEvent> replayAll() {
        List<ReplicationEvent> events = new ArrayList<>();
        if (!logFile.exists()) {
            return events;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    ReplicationEvent ev = ReplicationEvent.fromLogString(line);
                    if (ev != null) {
                        events.add(ev);
                        loggedEventIds.add(ev.getEventId());
                        if (ev.getVersion() > latestVersion) {
                            latestVersion = ev.getVersion();
                        }
                    }
                } catch (Exception e) {
                    System.err.printf("[EVENT LOG WARNING] Skipping corrupted log entry in %s: %s\n", logFile.getPath(), e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.printf("[EVENT LOG ERROR] Failed reading %s: %s\n", logFile.getPath(), e.getMessage());
        }
        return events;
    }

    /**
     * Retrieves all events recorded after a specific version for catch-up synchronization.
     */
    public synchronized List<ReplicationEvent> getEventsAfter(long afterVersion) {
        List<ReplicationEvent> all = replayAll();
        List<ReplicationEvent> missing = new ArrayList<>();
        for (ReplicationEvent ev : all) {
            if (ev.getVersion() > afterVersion) {
                missing.add(ev);
            }
        }
        return missing;
    }

    /**
     * Clears log file content (primarily used in unit testing).
     */
    public synchronized void clear() {
        if (logFile.exists()) {
            logFile.delete();
        }
        loggedEventIds.clear();
        latestVersion = 0;
    }
}
