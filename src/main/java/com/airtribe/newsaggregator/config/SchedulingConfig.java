package com.airtribe.newsaggregator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Configuration is handled through application.properties
    // spring.task.scheduling.pool.size=5
}
