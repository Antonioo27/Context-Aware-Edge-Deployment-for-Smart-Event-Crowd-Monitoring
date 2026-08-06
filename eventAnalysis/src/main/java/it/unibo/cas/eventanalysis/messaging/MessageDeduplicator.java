package it.unibo.cas.eventanalysis.messaging;

import java.util.LinkedHashMap;
import java.util.Map;

public class MessageDeduplicator {

    private final Map<Long, Boolean> seenIds;
    private final Object lock = new Object();

    public MessageDeduplicator(int windowSize) {
        this.seenIds = new LinkedHashMap<Long, Boolean>(windowSize, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                return size() > windowSize;
            }
        };
    }

    /**
     * Checks if the given batchId is a duplicate. If not, it is added to the window.
     * @param batchId The ID to check.
     * @return true if it was already in the window, false otherwise.
     */
    public boolean isDuplicateOrAdd(long batchId) {
        synchronized (lock) {
            if (seenIds.containsKey(batchId)) {
                return true;
            } else {
                seenIds.put(batchId, true);
                return false;
            }
        }
    }

    /**
     * Removes an ID from the window (useful if a message was rejected after being added).
     * @param batchId The ID to remove.
     */
    public void remove(long batchId) {
        synchronized (lock) {
            seenIds.remove(batchId);
        }
    }
}
