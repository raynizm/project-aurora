// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.telemetry;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Telemetry service for logging events and metrics.
 * Mirrors the reference plugin's MainThreadTelemetryShape.
 */
public final class TelemetryService implements com.intellij.openapi.Disposable {
    private static final Logger LOG = Logger.getInstance(TelemetryService.class);

    private final Map<String, Integer> eventCounts;
    private volatile boolean isDisposed = false;

    public TelemetryService() {
        this.eventCounts = new HashMap<>();
        LOG.debug("Initialized TelemetryService");
    }

    /**
     * Log a public telemetry event.
     */
    public void publicLog(@NotNull String eventName, @Nullable Map<String, Object> data) {
        if (isDisposed) {
            return;
        }

        // Increment event count
        eventCounts.merge(eventName, 1, Integer::sum);

        // Log the event with timestamp
        String timestamp = Instant.now().toString();
        LOG.debug("[Telemetry] " + eventName + " @ " + timestamp + ": " + 
                  (data != null ? data.toString() : "null"));
    }

    /**
     * Log a public telemetry event with categorized data.
     */
    public void publicLog2(@NotNull String eventName, @Nullable Map<String, Object> data) {
        if (isDisposed) {
            return;
        }

        // Increment event count
        eventCounts.merge(eventName + "_categorized", 1, Integer::sum);

        // Log the categorized event
        String timestamp = Instant.now().toString();
        LOG.debug("[Telemetry2] " + eventName + " @ " + timestamp + ": " + 
                  (data != null ? data.toString() : "null"));
    }

    /**
     * Log a simple string event.
     */
    public void logEvent(@NotNull String eventName) {
        publicLog(eventName, null);
    }

    /**
     * Get the count of events for a specific name.
     */
    public int getEventCount(@NotNull String eventName) {
        return eventCounts.getOrDefault(eventName, 0);
    }

    /**
     * Get all event counts.
     */
    public @NotNull Map<String, Integer> getAllEventCounts() {
        return new HashMap<>(eventCounts);
    }

    /**
     * Reset all event counts.
     */
    public void resetCounts() {
        eventCounts.clear();
        LOG.debug("Reset all telemetry event counts");
    }

    @Override
    public void dispose() {
        isDisposed = true;
        LOG.debug("Disposing TelemetryService");
        eventCounts.clear();
    }
}
