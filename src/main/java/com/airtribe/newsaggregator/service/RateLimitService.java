package com.airtribe.newsaggregator.service;

import com.airtribe.newsaggregator.exception.RateLimitExceededException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RateLimitService {

    private final Cache<String, Bucket> cache;

    public RateLimitService() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(100_000)
                .build();
    }

    public void checkRateLimit(String username) {
        Bucket bucket = cache.get(username, key -> createNewBucket());
        if (!bucket.tryConsume(1)) {
            long waitTimeSeconds = bucket.getAvailableTokens() == 0 ? 
                    Duration.ofMinutes(1).getSeconds() : 0;
            log.warn("Rate limit exceeded for user: {}. Need to wait {} seconds", username, waitTimeSeconds);
            throw new RateLimitExceededException(
                    String.format("Rate limit exceeded. Please try again in %d seconds", waitTimeSeconds),
                    waitTimeSeconds
            );
        }
        log.debug("Rate limit check passed for user: {}. Remaining tokens: {}", 
                username, bucket.getAvailableTokens());
    }

    public long getRemainingTokens(String username) {
        Bucket bucket = cache.get(username, key -> createNewBucket());
        return bucket.getAvailableTokens();
    }

    private Bucket createNewBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(10)
                        .refillIntervally(10, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
