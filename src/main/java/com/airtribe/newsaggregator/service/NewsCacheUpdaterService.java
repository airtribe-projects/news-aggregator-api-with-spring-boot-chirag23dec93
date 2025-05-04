package com.airtribe.newsaggregator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCacheUpdaterService {

    private final NewsService newsService;
    
    // Track last access time for each user
    private final ConcurrentHashMap<String, Instant> userLastAccess = new ConcurrentHashMap<>();
    
    // Constants
    private static final Duration USER_INACTIVITY_THRESHOLD = Duration.ofHours(24);
    private static final int BATCH_SIZE = 10;

    /**
     * Records user activity to determine which users need fresh news
     */
    public void recordUserAccess(String username) {
        userLastAccess.put(username, Instant.now());
        log.debug("Recorded access for user: {}, total active users: {}", username, userLastAccess.size());
    }

    /**
     * Updates news cache for active users every 15 minutes
     */
    @Scheduled(fixedRateString = "${news.cache.update.interval:900000}")
    public void updateNewsCache() {
        log.info("Starting scheduled news cache update");
        Instant start = Instant.now();
        int updatedCount = 0;
        int errorCount = 0;

        try {
            Set<String> activeUsers = getActiveUsers();
            log.info("Found {} active users for cache update", activeUsers.size());

            // Process users in batches to avoid overwhelming the News API
            for (String username : activeUsers) {
                try {
                    // Add small delay between requests to respect rate limits
                    if (updatedCount > 0 && updatedCount % BATCH_SIZE == 0) {
                        Thread.sleep(1000);
                    }

                    log.debug("Updating news cache for user: {}", username);
                    newsService.getNews(username);
                    updatedCount++;
                } catch (Exception e) {
                    log.error("Error updating news cache for user: {}", username, e);
                    errorCount++;
                }
            }

            Duration duration = Duration.between(start, Instant.now());
            log.info("Completed news cache update. Updated: {}, Errors: {}, Duration: {}ms",
                    updatedCount, errorCount, duration.toMillis());

        } catch (Exception e) {
            log.error("Error during news cache update", e);
        }
    }

    /**
     * Cleans up tracking for inactive users every hour
     */
    @Scheduled(fixedRateString = "${news.cache.cleanup.interval:3600000}")
    public void cleanupInactiveUsers() {
        log.info("Starting inactive users cleanup");
        Instant threshold = Instant.now().minus(USER_INACTIVITY_THRESHOLD);
        
        int initialSize = userLastAccess.size();
        userLastAccess.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold));
        int removedCount = initialSize - userLastAccess.size();
        
        log.info("Completed inactive users cleanup. Removed {} inactive users", removedCount);
    }

    private Set<String> getActiveUsers() {
        Instant threshold = Instant.now().minus(USER_INACTIVITY_THRESHOLD);
        return userLastAccess.entrySet().stream()
                .filter(entry -> entry.getValue().isAfter(threshold))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
