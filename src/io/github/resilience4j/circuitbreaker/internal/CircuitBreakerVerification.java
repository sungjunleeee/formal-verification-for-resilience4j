package io.github.resilience4j.circuitbreaker.internal;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.cprover.CProver;

import java.time.Duration;

public class CircuitBreakerVerification {

    // Mock Clock to control time
    static class MockClock extends java.time.Clock {
        long time = 0;

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneId.systemDefault();
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return java.time.Instant.ofEpochMilli(time);
        }

        public void advance(long millis) {
            time += millis;
        }
    }

    public static void main(String[] args) {
        MockClock clock = new MockClock();

        // 1. Configuration
        // Sliding Window Size: 4
        // Failure Rate Threshold: 50% (If 2 out of 4 fail -> OPEN)
        // Wait Duration: 1 second
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .failureRateThreshold(50)
                .permittedNumberOfCallsInHalfOpenState(2)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .clock(clock)
                .build();

        CircuitBreakerStateMachine circuitBreaker = new CircuitBreakerStateMachine("testVerifier", config);
        CircuitBreaker.State previousState = circuitBreaker.getState();

        // 2. Symbolic Execution Loop
        for (int i = 0; i < 10; i++) {
            CircuitBreaker.State currentState = circuitBreaker.getState();

            // A1. Property: No Illegal Transitions (Safety Property)
            if (previousState == CircuitBreaker.State.CLOSED && currentState == CircuitBreaker.State.HALF_OPEN) {
                assert false : "Safety Violated: Illegal transition from CLOSED to HALF_OPEN";
            }
            if (previousState == CircuitBreaker.State.OPEN && currentState == CircuitBreaker.State.CLOSED) {
                assert false : "Safety Violated: Illegal transition from OPEN to CLOSED";
            }

            checkFunctionalProperties(circuitBreaker, previousState);

            // Simulate Request
            boolean permission = circuitBreaker.tryAcquirePermission();

            // A2. Property: Open State Protection (Safety Property)
            if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                assert !permission : "Safety Violated: Acquired permission while in OPEN state";
            }

            if (permission) {
                boolean success = CProver.nondetBoolean();
                if (success) {
                    circuitBreaker.onSuccess(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                } else {
                    circuitBreaker.onError(100, java.util.concurrent.TimeUnit.MILLISECONDS,
                            new RuntimeException("Simulated Failure"));
                }
            } else {
                // If blocked, advance time to allow recovery
                clock.advance(1100);
            }

            // Property: Half-Open Failure (~Not implemented yet)
            // If we were HALF_OPEN and failed, we must transition to OPEN
            if (currentState == CircuitBreaker.State.HALF_OPEN && !permission) {
                // Note: This logic is tricky because permission might be denied for other
                // reasons. Better to check state after operation.
            }

            // Check Functional Properties after state updates
            checkFunctionalProperties(circuitBreaker, previousState);

            previousState = currentState;
        }
    }

    private static void checkFunctionalProperties(CircuitBreakerStateMachine circuitBreaker,
            CircuitBreaker.State previousState) {
        CircuitBreaker.State state = circuitBreaker.getState();
        float failureRate = circuitBreaker.getMetrics().getFailureRate();

        // Property: Failure Threshold (Functional Correctness Property)
        if (previousState == CircuitBreaker.State.CLOSED) {
            if (state == CircuitBreaker.State.CLOSED && failureRate >= 50.0f) {
                assert false : "Functional Logic Violated: Remained CLOSED despite failure rate >= 50%";
            }
            if (state != CircuitBreaker.State.CLOSED && failureRate < 50.0f) {
                assert false
                        : "Functional Logic Violated: Switched from CLOSED to " + state + " despite failure rate < 50%";
            }
        }

        // Property: Half-Open Limits (Implicitly checked by state transitions, but
        // could be explicit)
    }
}
