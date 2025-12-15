#!/bin/bash
# Script to run JBMC verification

# Ensure classes are compiled
./compile.sh
echo "Running JBMC..."
jbmc -cp classes io.github.resilience4j.circuitbreaker.internal.CircuitBreakerVerification \
  --unwind 10 \
  --unwinding-assertions \
  --java-assume-inputs-non-null \
  --trace | tee trace.log
