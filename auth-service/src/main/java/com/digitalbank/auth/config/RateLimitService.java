package com.digitalbank.auth.config;

import com.digitalbank.auth.exception.RateLimitExceededException;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitService {

    private final com.github.benmanes.caffeine.cache.Cache<String, Bucket> loginBuckets =
            Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterAccess(Duration.ofMinutes(5))
                    .build();

    public void checkLoginRateLimit(String key) {
        Bucket bucket = loginBuckets.get(key, this::createNewBucket);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long waitTimeSeconds = probe.getNanosToWaitForRefill() / TimeUnit.SECONDS.toNanos(1);
            throw new RateLimitExceededException(
                    "Too many login attempts. Please try again after " + waitTimeSeconds + " seconds"
            );
        }
    }

    private Bucket createNewBucket(String key) {
        Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
