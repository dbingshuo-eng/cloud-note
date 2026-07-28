package com.clouddisk.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@Component
@ConfigurationProperties(prefix = "cloud.share.verification-rate-limit")
public class ShareVerificationRateLimitProperties {

    private static final Duration MAX_WINDOW = Duration.ofHours(1);

    @Min(1)
    @Max(100)
    private int maxAttempts = 5;

    @Min(1)
    @Max(1_000_000)
    private int maxTrackedCodes = 10_000;

    @NotNull
    private Duration window = Duration.ofMinutes(1);

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getMaxTrackedCodes() {
        return maxTrackedCodes;
    }

    public void setMaxTrackedCodes(int maxTrackedCodes) {
        this.maxTrackedCodes = maxTrackedCodes;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }

    @AssertTrue(message = "window must be greater than zero and no more than one hour")
    public boolean isWindowWithinBounds() {
        return window != null
                && !window.isZero()
                && !window.isNegative()
                && window.compareTo(MAX_WINDOW) <= 0;
    }
}
