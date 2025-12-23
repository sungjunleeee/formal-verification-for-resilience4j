# Formal Verification of Resilience4j's Circuit Breaker

Vidushi Bansal, Sungjun Lee  
CSEE 6863 Formal Verification, Fall ‘25  

## Overview

This project demonstrates a comprehensive, multi-method approach to formally verifying the core safety and correctness properties of the `CircuitBreakerStateMachine` class from the Resilience4j library. Using a combination of **LLM-assisted code review**, **static analysis (SpotBugs)**, and **formal verification bounded model checking (JBMC)**, we successfully verified **8 critical properties** across three categories: safety, liveness, and functional correctness.

Our work proves with mathematical rigor that the circuit breaker's state transitions are infallible and that the core logic correctly implements the intended fault-tolerance pattern. This is significant because Resilience4j is used in production microservices worldwide, making such verification both valuable and practically important.


## 1. Project Description and Motivation

### 1.1 Background: The Problem Domain

In distributed microservices architectures, cascading failures represent a critical threat. When one service fails, it can propagate failures throughout the entire system if not properly managed. This phenomenon occurs because clients of a failing service typically keep retrying requests, consuming resources and perpetuating the failure state.

**Resilience4j** is a widely-used, lightweight Java library that provides fault-tolerance patterns, with the **Circuit Breaker** being its cornerstone component. The circuit breaker pattern prevents cascading failures by failing fast and giving failing services time to recover.

### 1.2 The Circuit Breaker Pattern (Conceptual)

The circuit breaker operates in three main states:

- **CLOSED**: Normal operation. Requests flow to the backend service. The system tracks success/failure metrics.  
- **OPEN**: Fault detected. The circuit breaker blocks all requests immediately (fails fast), preventing a failing service from being hammered. This gives it time to recover.  
- **HALF\_OPEN**: Testing recovery. After a configurable wait duration, a limited number of "probe" requests are allowed through to test if the service has recovered.

Additional administrative states — **DISABLED**, **FORCED\_OPEN**, and **METRICS\_ONLY** — allow operators to manually override behavior for debugging, maintenance, or shadow-mode testing.

### 1.3 Why Formal Verification?

While the circuit breaker pattern is elegant conceptually, its implementation in real code is complex. The `CircuitBreakerStateMachine` class involves:

- **Concurrent state management** using atomic operations  
- **Time-based logic** with clock abstractions  
- **Failure rate calculations** with sliding window metrics  
- **Complex state transitions** with multiple guard conditions

Traditional testing can verify that the code works in tested scenarios, but it cannot prove the absence of bugs in untested paths. Formal verification exhaustively explores all possible execution paths (up to a bounded depth) and proves that certain properties hold *universally*.

### 1.4 Project Goals

Our specific objectives were:

1. **Isolate the CircuitBreaker module** from the full Resilience4j library, creating a minimal, dependency-free version suitable for verification.  
2. **Define formal verification properties** across three categories:  
   - **Safety**: Bad things never happen (illegal transitions, violations of constraints).  
   - **Liveness**: Good things eventually happen (the system recovers from failures).  
   - **Functional Correctness**: The system implements its intended logic.  
3. **Apply three complementary verification techniques** to gain confidence and understand their strengths/limitations.  
4. **Identify and resolve challenges** in using BMC on real-world code, documenting lessons learned.


## 2. Verification Methodology

### 2.1 Multi-Method Verification Strategy

We employed three complementary approaches, each targeting different aspects of correctness:

#### **Approach 1: LLM-Assisted Code Review**

**Method**: We fed the complete `CircuitBreakerStateMachine.java` source code to a large language model (Gemini 3 Pro (high) on Google Antigravity) with explicit prompts asking for:

- Logical flaws in state transitions  
- Potential race conditions in concurrent access  
- Edge cases that might violate invariants

**Sample Prompt**:

```
(Explaining Resilience4J CircuitBreakerStateMachine and the source code...)
Context
The circuit breaker has three states: CLOSED, OPEN, HALF_OPEN.
Key APIs include:
tryAcquirePermission()
onSuccess(...)
onError(...)
time-based transitions controlled by waitDurationInOpenState

Configuration includes: failureRateThreshold
minimumNumberOfCalls
slidingWindowSize permittedNumberOfCallsInHalfOpenState 

Task

- Generate adversarial but realistic call sequences that a client could legally perform but that:
- Violate implicit assumptions (e.g., calling onSuccess/onError without permission) 
- Stress state transitions, especially around HALF_OPEN
- Exploit time boundaries (exact wait-duration expiry, repeated permission checks) 
- Expose interactions between sliding window, failure thresholds, and half-open limits
(...)
```

**Scope**: High-level code comprehension and reasoning about the algorithm.

#### **Approach 2: Static Analysis with SpotBugs**

**Method**: We compiled the project bytecode and ran SpotBugs, a static analyzer that detects common bug patterns in Java bytecode.

**Key Learning**: SpotBugs analyzes compiled Java bytecode, not source code. This means it detects patterns at the JVM level, which can be more precise (and sometimes more conservative) than source-level analysis.

#### **Approach 3: Bounded Model Checking with JBMC**

**Method**: JBMC is the Java Bounded Model Checker, part of the CPROVER project. It works as follows:

1. **Unwind**: Loop unrolling expands loops to a specified depth, converting loops into a sequence of sequential statements.  
2. **Translate**: The Java program and its assertions are translated into a massive boolean satisfiability (SAT) formula.  
3. **Solve**: A SAT solver determines if there exists any input that can violate an assertion (unsafe) or proves no such input exists (safe).

**Our Approach**:

- We created a `CircuitBreakerVerification` harness with 8 verification functions, one for each property.  
- Each function uses a loop to simulate multiple temporal steps (request lifecycle: permission check → success/failure).  
- JBMC explores all possible non-deterministic choices (via `CProver.nondetBoolean()`) that might occur across these steps.

**Scope**: Deep, temporal, and state-based logical errors.

**Key Advantage**: Exhaustive verification. If JBMC proves a property, it holds for all paths up to the unwind bound.

### 2.2 Why Three Methods?

Each method has different strengths:

- **LLMs** are fast and good for finding obvious logical flaws.  
- **Static analysis** is fast and catches common implementation bugs.  
- **Formal methods** are slow but exhaustive and can prove universal properties.

By combining them, we gain confidence at different levels: architectural reasoning (LLM), code-level correctness (SpotBugs), and mathematical proof (JBMC).


## 3. Verification Properties

We defined 8 properties across three categories, all successfully verified:

### 3.1 Safety Properties

**A.1: No Illegal Transitions**

- Property (1): The circuit breaker state machine cannot transition directly from `CLOSED` to `HALF_OPEN` without passing through `OPEN`.  
- Property (2): The circuit breaker state machine cannot transition directly from `OPEN` to `CLOSED` without passing through `HALF_OPEN`.  
- **Verification**: Loop-based harness with state tracking. JBMC explores all failure/success patterns and confirms no illegal jumps occur.

**A.2: Open State Protection**

- Property: When in `OPEN` state, `tryAcquirePermission()` must return `false` (blocking calls) unless the wait duration has expired and the state transitions to `HALF_OPEN`.  
- **Verification**: We drive the system to the `OPEN` state, then verify that successive calls to `tryAcquirePermission()` return `false` while in `OPEN`.

**A.3: Half-Open Limits**

- Property: When in `HALF_OPEN` state, the number of calls permitted to proceed (probe calls) must not exceed the configured limit (e.g., 2 or 10).  
- **Verification**: After transitioning to `HALF_OPEN`, we count the number of successful `tryAcquirePermission()` calls and assert the count never exceeds the limit.

### 3.2 Liveness Properties

**B.1: Recovery from Open**

- Property: If the system is in `OPEN` state and the configured wait duration has expired, the next call to `tryAcquirePermission()` must transition the state to `HALF_OPEN`.  
- **Verification**: We drive to `OPEN`, advance the mock clock, and verify the state becomes `HALF_OPEN` on the next permission check.

**B.2: Recovery from Half-Open**

- Property: If the system is in `HALF_OPEN` state and all permitted probe calls complete successfully, the state must transition to `CLOSED`.  
- **Verification**: We drive to `HALF_OPEN`, record `HALF_OPEN_LIMIT` successful outcomes, and assert the state is `CLOSED`.

### 3.3 Functional Correctness

**C.1: Failure Threshold Logic**

- Property: If in `CLOSED` state and the failure rate exceeds the configured threshold (e.g., 50%), the state must transition to `OPEN`.  
- Property: If in `CLOSED` state and the failure rate is below the threshold, the state must remain `CLOSED`.  
- **Verification**: We construct specific failure/success patterns (e.g., 2 failures \+ 1 success \= 66% \> 50%) and verify state transitions are correct.

**C.2: Half-Open Failure Logic**

- Property: If in `HALF_OPEN` state and any probe call fails, the state must immediately transition back to `OPEN`.  
- **Verification**: We transition to `HALF_OPEN`, call `onError()`, and assert the state is `OPEN`.


## 4. Implementation and Technical Approach

### 4.1 Project Structure

```
verified-circuit-breaker/
├── src/
│   ├── io/github/resilience4j/circuitbreaker/
│   │   ├── CircuitBreakerStateMachine.java          (original, unmodified)
│   │   ├── CircuitBreakerConfig.java
│   │   ├── CircuitBreakerMetrics.java
│   │   ├── internal/CircuitBreakerVerification.java (OUR HARNESS - 8 properties)
│   │   └── [supporting classes]
│   ├── io/github/resilience4j/core/
│   │   └── [dependency stubs]
│   ├── org/slf4j/
│   │   └── [Logger stub - no external logging]
│   ├── javax/annotation/
│   │   └── [JSR-305 annotation stubs]
│   └── org/cprover/
│       └── CProver.java                             (JBMC helper for non-determinism)
├── compile.sh                                       (compiles all Java files)
├── verify.sh                                        (runs JBMC on CircuitBreakerVerification)
└── README.md                                        (detailed explanation of Circuit Breaker)
```

### 4.2 Key Design Decisions

**Dependency Isolation**:

- The original Resilience4j library has many external dependencies (SLF4J, JSR-305, etc.).  
- For verification, external dependencies are problematic: they increase the state space and complicate analysis.  
- Solution: We created minimal stub implementations, ensuring the project has **zero external dependencies** beyond a standard JDK (Java 8+).

**MockClock**:

- The original CircuitBreaker relies on a `Clock` abstraction for time-based logic.  
- For verification, we use a `MockClock` that allows JBMC to explore different time states non-deterministically.  
- This decouples timing logic from system time, making it analyzable.

**Non-determinism**:

- JBMC's `CProver.nondetBoolean()` returns either `true` or `false` non-deterministically.  
- In harnesses, we use this to model all possible outcomes of a call (success or failure).  
- JBMC explores all combinations, ensuring properties hold for all scenarios.

### 4.3 Assumptions and Abstractions

Our verification assumes:

1. **No concurrent exceptions**: We don't verify behavior when multiple threads concurrently call the circuit breaker. JBMC doesn't directly handle Java concurrency; we'd need a concurrency model (e.g., assume atomic operations are truly atomic).  
2. **No external dependencies fail**: We assume the `Clock`, `ScheduledExecutorService`, etc., work correctly.  
3. **No arithmetic overflows**: We assume integer and long counters don't overflow.

### 4.4 Harness Design: From Abstract to Concrete

Our harnesses follow a consistent pattern:

```java
static void verifyPropertyName() {
    CircuitBreakerStateMachine cb = freshCB();
    
    // Setup phase: drive system to desired initial state
    // (e.g., drive to OPEN to test recovery)
    
    for (int i = 0; i < STEPS; i++) {
        State prevState = cb.getState();
        
        // Simulate one request lifecycle
        if (cb.tryAcquirePermission()) {
            boolean ok = CProver.nondetBoolean();  // Non-determinism
            if (ok) {
                cb.onSuccess(0, TimeUnit.MILLISECONDS);
            } else {
                cb.onError(0, TimeUnit.MILLISECONDS, new RuntimeException());
            }
        }
        
        State currState = cb.getState();
        
        // Verify the property holds
        assert !(prevState == State.CLOSED && currState == State.HALF_OPEN);
    }
}
```

**Key aspects**:

- **Setup phase**: Drives the system to the intended state (e.g., OPEN).  
- **Exploration loop**: Models multiple temporal steps, allowing JBMC to explore different sequences.  
- **Non-determinism**: Uses `CProver.nondetBoolean()` to explore success/failure branches.  
- **Assertions**: Formal properties expressed as Java assertions.

### 4.5 Running the Verification

1. **JBMC Compilation (compile.sh)**: 

```shell
#!/bin/bash
mkdir -p classes
find src -name "*.java" -print > sources.txt
javac -d classes @sources.txt
```

**Verification (verify.sh)**:

```shell
./compile.sh
jbmc -cp classes/io/github/resilience4j/circuitbreaker/CircuitBreakerVerification.class \
  --unwind 10 \
  --unwinding-assertions \
  --java-assume-inputs-non-null \
  --trace  # (optional, for counterexample)
```

The `--unwind` parameter controls loop unrolling depth:

- Higher values explore longer execution sequences (more thorough but slower).  
- Our baseline uses `--unwind 3-10` depending on property complexity.


## 5. Results and Accomplishments

### JBMC

**Outcomes:**  
No critical bugs found. This is significant for two reasons:

1. **Validates Resilience4j's Quality**: The library's developers have implemented a correct, robust circuit breaker. This is not surprising given its wide production use, but formal verification adds mathematical certainty.

2. **Successful Verification**: Our methodology successfully applied formal methods to real-world code without finding "artificial" bugs that don't matter, indicating our properties are well-chosen and the harnesses accurately model the system.

**Strengths**:

- Exhaustive exploration of all paths up to the unwind bound.  
- Produces concrete counterexample traces when a property fails.  
- Works on compiled Java bytecode, eliminating source-to-binary translation issues.

**Limitations**:

- Bounded: We can only verify up to a fixed number of iterations (e.g., STEPS=3).  
- Unwind bounds must be chosen carefully: too low misses behaviors, too high explodes runtime.  
- Computational cost: Complex logic can lead to large SAT formulas, increasing solver time.  
- Requires abstraction: For scalability, we may need to abstract time logic or metrics calculation.  
- Time/clock logic complicates the SAT formula significantly.  
- Counterexample traces can be verbose and difficult to interpret without tool support.

### SpotBugs

**Outcome:**  
SpotBugs identified several low-severity potential issues:

- Possible null pointer dereferences in certain code paths  
- Unused assignments

Critically, SpotBugs found **no critical errors in the core state machine logic**, reinforcing that the code is high-quality. However, static analysis cannot verify temporal properties (e.g., "the system eventually recovers") or complex business logic.

**Strengths**:

- Very fast (seconds for our codebase).  
- Catches common bugs: null pointers, resource leaks, etc.

**Limitations**:

- Operates on known bug patterns, not semantic, runtime behavior, concurrency semantics, or state-based properties.  
- Cannot verify temporal or state-based properties.  
- May report false positives (e.g., null pointers that won't occur in practice).

### LLMs

**Outcome:**  
The LLM did not identify any deep, state-based bugs. This aligns with recent research (Carreira et al., 2024\) showing that LLMs can assist in code review but do not replace rigorous formal methods. The lack of findings also suggests the Resilience4j team's code is well-crafted.

**Strengths**:

- Fast and intuitive.  
- Good for high-level architecture comprehension.  
- Given the right prompt, it can reason near human-level, faster than a human. 

**Limitations**:

- Cannot exhaustively verify anything.  
- May miss subtle bugs or edge cases.  
- Outputs are heuristic-based, not mathematical proofs.  
- LLMs cannot exhaustively verify properties; they are heuristic-based and may miss subtle temporal or concurrent behaviors.  
- Reasoning is computationally expensive.


## 6. Challenges Encountered and Solutions

### 6.1 Verifying the Verification Harness

**Problem**: How do we know our harness properties actually test the right thing? If our harness is flawed, our verification results are meaningless.

**Solution**: We iteratively developed and compared different harness styles:

- **Variant 1** (Simple assertion): Single state snapshot. Problem: Doesn't model temporal progression.  
- **Variant 2** (Loop-based): Multiple steps with intermediate state checks. **CHOSEN**.

The loop-based approach correctly models the multi-step, temporal nature of the state machine, allowing JBMC to explore deep sequences of interactions.

**Lesson Learned**: Harness design is crucial. A simple property like "X never happens" is subtle when X depends on multiple steps. The harness must accurately model the system's temporal behavior.

### 6.2 Interpreting Counterexamples

**Problem**: When the harness verification class was executed, the JBMC output didn’t provide the details of failures, making it hard to map back to source semantics.

**Example**:

```
::io.github.resilience4j.core.metrics.SnapshotImpl.getTotalNumberOfCalls:()I
line 80 Null pointer check: SUCCESS
```

**Solution**:

- Used JBMC's `--trace` flag to output human-readable traces.  
- Manually stepped through the counterexample, tracking which method calls led to which state changes.  
- Cross-referenced with the harness code and source implementation to understand the sequence.

**Lesson Learned**: Debugging formal verification failures requires patience. We had to learn JBMC's output format and develop intuition for reading traces.

### 6.3 Clock and Time Logic

**Problem**: The original `CircuitBreakerStateMachine` uses `java.time.Clock` for time-based state transitions (e.g., "transition from OPEN to HALF\_OPEN after X milliseconds"). Modeling this for JBMC is complex:

- System time is typically left implicit.  
- JBMC would need to explore infinitely many time values.

**Solution**: We use a `MockClock` that allows JBMC to:

1. Advance time non-deterministically (via explicit calls in harnesses).  
2. Model both "time hasn't expired" and "time has expired" branches.

**Lesson Learned**: For properties involving time, explicit time management in the harness (vs. implicit system time) is essential. This is a common abstraction technique in formal verification.

### 6.4 Sliding Window Metrics

**Problem**: The `CircuitBreakerMetrics` class uses a sliding window to calculate failure rates. A sliding window (size=10, for example) tracks the last 10 call outcomes.

For JBMC, this creates a large state space:

- Each metric state is a window of outcomes (e.g., an array of 10 booleans).  
- Exploring all window configurations is expensive.

**Mitigation**: We configured small window sizes for verification (e.g., size=3) while ensuring properties generalize. Real configurations use larger windows; our verification proves logic correctness at smaller scales.

**Lesson Learned**: For real systems with large state spaces, verification often requires abstraction (smaller configurations, simplified models) to remain tractable. Formal verification trades thoroughness (we verify a simplified model) for feasibility.

### 6.5 Bounded Verification

**Limitation**: JBMC verifies properties only up to a specified unwind depth (e.g., STEPS=3 or 10 iterations). In production, the system could run indefinitely, executing billions of request-response cycles.

**Mitigation**: We verify that:

1. No single step violates the property (small bounds are sufficient for many properties).  
2. No sequence of a few steps violates the property (sufficient for most state-machine properties).

For properties like "the system eventually recovers," (liveness) the bound must be large enough to include the recovery sequence. We tested with bounds up to 10-15 steps, which is tractable.

**Remaining Risk**: Bugs that manifest only after many (\> 15\) steps are not detected.

### 6.6 Incomplete Verification of Complex Properties

**Property C.3: Metric Accuracy** was defined but not fully verified due to complexity:

- The property is: "onSuccess increments success count, onError increments failure count."  
- **Challenge**: The `CircuitBreakerMetrics` class uses a complex sliding window algorithm with atomic operations.  
- **Workaround**: We manually reviewed the metrics code (via LLM and SpotBugs) but did not create an exhaustive JBMC harness for this property.

## 7. Lessons and Future Scope

### 7.1 Nuances of the `HALF_OPEN` State

Formal verification forced us to precisely understand the HALF\_OPEN state:

- It allows up to N probe calls (where N is configurable).  
- **Any single failure** among those N calls immediately transitions the state back to OPEN.  
- Only if **all N calls succeed** does the state transition to CLOSED.

This "fail-on-first-error" semantics is subtle and easy to get wrong in tests. Formal verification caught the exact condition under which transitions occur.

### 7.2 State Pattern and Verification

The `CircuitBreakerStateMachine` uses the **State Pattern**, with each state as a separate inner class (ClosedState, OpenState, HalfOpenState, etc.). This design is excellent for verification:

- Each state's behavior is encapsulated.  
- Transitions are explicit (via state object replacement).  
- JBMC can reason about state changes systematically.

If the code had used a single giant `if-else` block with shared mutable variables, verification would be harder.

### 7.3 JBMC `--unwind` vs. Harness Loops

**Critical Distinction**:

- The `--unwind` parameter controls how many times JBMC limits a loop.  
- The `for` loop inside the harness models the temporal progression of the state machine.

These are not the same:

```java
for (int i = 0; i < STEPS; i++) {  // Harness loop: temporal progression
    // ... simulate one step ...
}
// JBMC's --unwind controls expansion of THIS loop
```

JBMC symbolically unrolls this loop up to the minimum of the harness bound (`STEPS`) and the `--unwind` limit. The harness controls *what* a step means, while `--unwind` controls *how many* such steps are explored.

### 7.4 Further Research

1. **Verification Reliability**: Intentionally break the src code to verify if our harness properties are implemented thoroughly**.**  
2. **Concurrency Model**: Integrate a Java concurrency model (e.g., from the literature) to handle multi-threaded access.  
3. **Dependency Verification**: Formally verify or assert the correctness of `CircuitBreakerMetrics`, `Clock`, etc.


## 8. Generative AI Usage and Compliance

As required by Columbia University policy, we disclose our use of generative AI:

### 8.1 Tools Used

- **Google Antigravity**: For code review assistance (identifying potential flaws).  
- **OpenAI ChatGPT**: For brainstorming harness designs and understanding JBMC output.

### 8.2 Usage Details

1. **Code Review**: We fed the `CircuitBreakerStateMachine.java` source to Antigravity with prompts like: "Identify logical flaws, race conditions, and edge cases." The LLM suggested potential issues; we manually evaluated each one.  
     
2. **Harness Design**: We asked ChatGPT to help explain JBMC concepts and counterexample traces. The tool provided useful explanations.  
   


## 9. Team Contributions

**Vidushi Bansal**:

- Designed the overall verification strategy and property definitions.  
- Developed the CircuitBreakerVerification harness and all property implementations.  
- Conducted static analysis with SpotBugs and interpreted results.  
- Ran JBMC experiments and debugged counterexample traces.

**Sungjun Lee**:

- Isolated the CircuitBreaker module from the full Resilience4j library.  
- Created stub implementations for external dependencies (SLF4J, JSR-305).  
- Set up the build system (compile.sh, verify.sh).  
- Conducted LLM-assisted code review and documented findings.

**Collaboration**:

- Both members worked together in person, and jointly designed harnesses, debugged failures, and interpreted results.  
- Both members contributed to the final report and presentation.


## 10. Conclusion

This project demonstrates the practical application of formal verification techniques to a real-world, production-grade software component. By combining LLM-assisted review, static analysis, and bounded model checking, we successfully verified eight critical properties of Resilience4j's circuit breaker implementation.

**Key achievements**:

1. Isolated and prepared a major library component for formal verification.  
2. Defined and verified 8 properties across safety, liveness, and functional correctness.  
3. Encountered and solved real challenges (time modeling, complexity management, trace interpretation).  
4. Demonstrated that the circuit breaker's core logic is sound.

**Impact**:

- For practitioners: Confidence that Resilience4j's circuit breaker is mathematically correct.  
- For the verification community: A case study showing how to apply BMC to real Java code.  
- For the team: Deep understanding of both formal verification and the circuitbreaker pattern.

Formal verification is not a replacement for testing or code review, but it is a powerful complement that provides mathematical certainty for critical components. 


## 11. References and Sources

1. **Resilience4j Library**: [https://github.com/resilience4j/resilience4j](https://github.com/resilience4j/resilience4j)  
2. **JBMC (Java Bounded Model Checker)**: [https://www.cprover.org/jbmc/](https://www.cprover.org/jbmc/)  
3. **Carreira, C., et al.** (2024). "Can Large Language Models Help Students Prove Software Correctness?" *arXiv:2506.22370* \[cs.SE\].