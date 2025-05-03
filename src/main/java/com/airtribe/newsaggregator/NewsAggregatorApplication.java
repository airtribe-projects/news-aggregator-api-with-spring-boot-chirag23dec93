package com.airtribe.newsaggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * News Aggregator Application
 * 
 * Features:
 * - User authentication and authorization
 * - News preferences management
 * - News search and filtering
 * - Rate limiting
 * - Background cache updates for active users
 * 
 * Cache Management:
 * - News articles are cached per user
 * - Cache is automatically updated every 15 minutes for active users
 * - Users are considered inactive after 24 hours of no activity
 * - Inactive users are removed from cache tracking hourly
 * - Cache entries expire after 15 minutes (configurable)
 * 
 * Configuration:
 * - news.cache.update.interval: Interval for updating cached news (default: 900000ms/15min)
 * - news.cache.cleanup.interval: Interval for cleaning up inactive users (default: 3600000ms/1hour)
 * - news.cache.user-inactivity-threshold: Time after which a user is considered inactive (default: 86400000ms/24hours)
 * - spring.task.scheduling.pool.size: Size of the thread pool for background tasks (default: 5)
 */
@SpringBootApplication
@EnableCaching
public class NewsAggregatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewsAggregatorApplication.class, args);
    }
}
