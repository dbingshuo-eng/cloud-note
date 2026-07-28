package com.clouddisk.service;

import com.clouddisk.config.ShareVerificationRateLimitProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;

@Service
public class ShareVerificationRateLimiter {

    private static final int MAX_CONFIGURED_ATTEMPTS = 100;
    private static final int MAX_CONFIGURED_TRACKED_CODES = 1_000_000;
    private static final Duration MAX_CONFIGURED_WINDOW = Duration.ofHours(1);

    private final int maxAttempts;
    private final int maxTrackedCodes;
    private final Duration window;
    private final Clock clock;
    private final LinkedHashMap<String, AttemptWindow> attemptsByCode =
            new LinkedHashMap<>(16, 0.75f, true);

    public ShareVerificationRateLimiter(
            ShareVerificationRateLimitProperties properties,
            Clock clock
    ) {
        if (properties == null
                || properties.getMaxAttempts() < 1
                || properties.getMaxAttempts() > MAX_CONFIGURED_ATTEMPTS
                || properties.getMaxTrackedCodes() < 1
                || properties.getMaxTrackedCodes() > MAX_CONFIGURED_TRACKED_CODES
                || properties.getWindow() == null
                || properties.getWindow().isZero()
                || properties.getWindow().isNegative()
                || properties.getWindow().compareTo(MAX_CONFIGURED_WINDOW) > 0
                || clock == null) {
            throw new IllegalArgumentException("Invalid share verification rate limit configuration");
        }
        this.maxAttempts = properties.getMaxAttempts();
        this.maxTrackedCodes = properties.getMaxTrackedCodes();
        this.window = properties.getWindow();
        this.clock = clock;
    }

    public synchronized boolean tryAcquire(String code) {
        Instant now = clock.instant();
        AttemptWindow current = attemptsByCode.get(code);
        if (current == null || !now.isBefore(current.startedAt.plus(window))) {
            attemptsByCode.put(code, new AttemptWindow(now, 1));
            evictOverflow();
            return true;
        }
        if (current.attempts >= maxAttempts) {
            return false;
        }
        current.attempts++;
        return true;
    }

    public synchronized void clear(String code) {
        attemptsByCode.remove(code);
    }

    synchronized int trackedCodeCount() {
        return attemptsByCode.size();
    }

    private void evictOverflow() {
        while (attemptsByCode.size() > maxTrackedCodes) {
            var iterator = attemptsByCode.entrySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static final class AttemptWindow {

        private final Instant startedAt;
        private int attempts;

        private AttemptWindow(Instant startedAt, int attempts) {
            this.startedAt = startedAt;
            this.attempts = attempts;
        }
    }
}
