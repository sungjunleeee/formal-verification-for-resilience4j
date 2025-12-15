package io.github.resilience4j.circuitbreaker.internal;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import org.cprover.CProver;

public class CircuitBreakerVerification {

    // NOTE (BMC): Keep this small in code; scale exploration with JBMC via --unwind STEPS.
    // Example: jbmc CircuitBreakerVerification --unwind 3 --trace
    static final int STEPS = 3; // temporal bound (drive via --unwind)
    static final int HALF_OPEN_LIMIT = 2;

    /*
     * ============================================================
     * Entry point
     * ============================================================
     */
    public static void main(String[] args) {
        verifyNoClosedToHalfOpen();
        verifyNoOpenToClosed();
        verifyOpenBlocksCalls();
        verifyHalfOpenLimit();
        verifyOpenToHalfOpen();
        verifyHalfOpenSuccess();
        verifyFailureThresholdLogic();
        verifyHalfOpenFailure();
    }

    /*
     * ============================================================
     * Harness setup
     * ============================================================
     */
    static CircuitBreakerStateMachine freshCB() {
        return freshCB(new MockClock(Instant.EPOCH, ZoneId.of("UTC")));
    }

    static CircuitBreakerStateMachine freshCB(Clock clock) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .permittedNumberOfCallsInHalfOpenState(HALF_OPEN_LIMIT)
                .waitDurationInOpenState(Duration.ofMillis(1))
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(3)
                .minimumNumberOfCalls(3)
                .clock(clock)
                .build();

        return new CircuitBreakerStateMachine("cb", config);
    }

    /*
     * ============================================================
     * A.1.1 No Illegal Transitions (CLOSED -> HALF_OPEN)
     * ============================================================
     */
    static void verifyNoClosedToHalfOpen() {
        CircuitBreakerStateMachine cb = freshCB();

        for (int i = 0; i < STEPS; i++) {
            CircuitBreaker.State prev = cb.getState();

            // one request lifecycle
            if (cb.tryAcquirePermission()) {
                boolean ok = CProver.nondetBoolean();
                if (ok) {
                    cb.onSuccess(0, TimeUnit.MILLISECONDS);
                } else {
                    cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException());
                }
            }

            CircuitBreaker.State curr = cb.getState();

            assert !(prev == CircuitBreaker.State.CLOSED &&
                    curr == CircuitBreaker.State.HALF_OPEN);
        }
    }

    /*
     * ============================================================
     * A.1.2 No Illegal Transitions (OPEN -> CLOSED)
     * ============================================================
     */
    static void verifyNoOpenToClosed() {
        CircuitBreakerStateMachine cb = freshCB();

        /* Step 1: Drive system to OPEN */
        for (int i = 0; i < STEPS; i++) {
            cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException());
        }
        assert cb.getState() == CircuitBreaker.State.OPEN;

        /* Step 2: Explore transitions */
        for (int i = 0; i < STEPS; i++) {

            CircuitBreaker.State prev = cb.getState();

            // one request lifecycle
            if (cb.tryAcquirePermission()) {
                boolean ok = CProver.nondetBoolean();
                if (ok)
                    cb.onSuccess(0, TimeUnit.MILLISECONDS);
                else
                    cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException());
            }

            CircuitBreaker.State curr = cb.getState();

            // illegal direct transition
            assert !(prev == CircuitBreaker.State.OPEN &&
                    curr == CircuitBreaker.State.CLOSED);
        }
    }

    /*
     * ============================================================
     * A.2. Open State Protection
     * ============================================================
     */
    static void verifyOpenBlocksCalls() {
        CircuitBreakerStateMachine cb = freshCB();

        for (int i = 0; i < STEPS; i++) {
            cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException());
        }

        assert cb.getState() == CircuitBreaker.State.OPEN;

        // FIX: The property is "OPEN blocks calls unless wait duration expired".
        // So we assert blocking only while the state is OPEN.
        for (int i = 0; i < STEPS; i++) {
            if (cb.getState() == CircuitBreaker.State.OPEN) {
                boolean permitted = cb.tryAcquirePermission();
                assert !permitted;
            } else {
                // once it leaves OPEN to HALF_OPEN after wait expiry, this property no longer applies
                break;
            }
        }
    }

    /*
     * ============================================================
     * A.3. Half-Open Limits
     * ============================================================
     */
    static void verifyHalfOpenLimit() {
        CircuitBreakerStateMachine cb = freshCB();

        for (int i = 0; i < STEPS; i++) {
            cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException());
        }

        assert cb.getState() == CircuitBreaker.State.OPEN;

        ((MockClock) cb.getCircuitBreakerConfig().getClock()).advance(Duration.ofMillis(2));

        cb.tryAcquirePermission();
        assert cb.getState() == CircuitBreaker.State.HALF_OPEN;

        int permits = 0;
        for (int i = 0; i < HALF_OPEN_LIMIT + 1; i++) {
            if (cb.tryAcquirePermission()) {
                permits++;
            }
        }

        assert permits <= HALF_OPEN_LIMIT;
    }

    /*
     * ============================================================
     * B.1. Recovery from Open
     * ============================================================
     */
    static void verifyOpenToHalfOpen() {
        MockClock clock = new MockClock(Instant.EPOCH, ZoneId.of("UTC"));
        CircuitBreakerStateMachine cb = freshCB(clock);

        // Drive to OPEN
        for (int i = 0; i < STEPS; i++) {
            cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException());
        }

        assert cb.getState() == CircuitBreaker.State.OPEN;

        // Expire wait duration
        clock.advance(Duration.ofMillis(2));

        // The very next call must cause the transition
        cb.tryAcquirePermission();

        assert cb.getState() == CircuitBreaker.State.HALF_OPEN;
    }

    /*
     * ============================================================
     * B.2. Recovery from Half-Open
     * ============================================================
     */
    static void verifyHalfOpenSuccess() {
        CircuitBreakerStateMachine cb = freshCB();

        for (int i = 0; i < STEPS; i++) {
            cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException());
        }

        ((MockClock) cb.getCircuitBreakerConfig().getClock()).advance(Duration.ofMillis(2));

        cb.tryAcquirePermission();
        assert cb.getState() == CircuitBreaker.State.HALF_OPEN;

        for (int i = 0; i < HALF_OPEN_LIMIT; i++) {
            cb.onSuccess(0, TimeUnit.MILLISECONDS);
        }

        assert cb.getState() == CircuitBreaker.State.CLOSED;
    }

    /*
     * ============================================================
     * C.1. Failure Threshold Logic
     * ============================================================
     */
    static void verifyFailureThresholdLogic() {
        // Case 1: failure rate exceeds threshold -> must OPEN
        {
            CircuitBreakerStateMachine cb = freshCB();

            assert cb.getState() == CircuitBreaker.State.CLOSED;

            // 3 calls in sliding window, minimumNumberOfCalls=3.
            // 2 failures + 1 success => 66% failures > 50% => must OPEN.
            cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException());
            cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException());
            cb.onSuccess(0, TimeUnit.MILLISECONDS);

            assert cb.getState() == CircuitBreaker.State.OPEN;
        }

        // Case 2: failure rate below threshold -> must remain CLOSED
        {
            CircuitBreakerStateMachine cb = freshCB();

            assert cb.getState() == CircuitBreaker.State.CLOSED;

            // 1 failure + 2 successes => 33% failures <= 50% => must stay CLOSED.
            cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException());
            cb.onSuccess(0, TimeUnit.MILLISECONDS);
            cb.onSuccess(0, TimeUnit.MILLISECONDS);

            assert cb.getState() == CircuitBreaker.State.CLOSED;
        }
    }

    /*
     * ============================================================
     * C.2. Half-Open Failure Logic
     * ============================================================
     */
    static void verifyHalfOpenFailure() {
        CircuitBreakerStateMachine cb = freshCB();

        for (int i = 0; i < STEPS; i++) {
            cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException());
        }

        ((MockClock) cb.getCircuitBreakerConfig().getClock()).advance(Duration.ofMillis(2));

        cb.tryAcquirePermission();
        assert cb.getState() == CircuitBreaker.State.HALF_OPEN;

        cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException());
        assert cb.getState() == CircuitBreaker.State.OPEN;
    }

    static class MockClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        public MockClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MockClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        public void advance(Duration duration) {
            instant = instant.plusMillis(duration.toMillis());
        }
    }
}
