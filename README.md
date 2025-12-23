# Formal Verification of Resilience4j's Circuit Breaker

This repository contains a **formally verified, isolated version** of the `CircuitBreakerStateMachine` from the [Resilience4j](https://github.com/resilience4j/resilience4j) library.

The project demonstrates a comprehensive verification strategy using **JBMC (Java Bounded Model Checker)** to mathematically prove safety, liveness, and functional correctness properties of the circuit breaker pattern.

## 📄 Detailed Report

For a complete explanation of the verification methodology, defined properties, challenges, and results, please refer to the extensive **[Formal Verification Report](Formal_Verification_Report.md)** included in this repository.

## 📂 Directory Structure

*   **`src/`**: Source code (including stubbed dependencies for isolation).
    *   `io.github.resilience4j.circuitbreaker`: Core logic & verification harness.
    *   `org.cprover`: JBMC helper classes.
*   **`compile.sh`**: Compilation script.
*   **`verify.sh`**: Script to run JBMC verification.

## 🚀 How to Run

### Prerequisites
*   Java Development Kit (JDK) 8+
*   [JBMC](https://www.cprover.org/jbmc/) installed and in your PATH.

### 1. Compile the Project
This script compiles the isolated source code and stubs with zero external dependencies.

```bash
./compile.sh
```

### 2. Run Formal Verification
Execute the verification script to check the properties using JBMC.

```bash
./verify.sh
```

## ✅ What is Verified?
We successfully verified **8 critical properties** across three categories:

1.  **Safety**: No illegal state transitions; strict enforcement of OPEN state blocking.
2.  **Liveness**: Guaranteed recovery from OPEN and HALF_OPEN states when conditions are met.
3.  **Functional Correctness**: Correct behavior of failure rate thresholds and probe limits.
