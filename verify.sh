#!/bin/bash
# Script to run JBMC verification

# Ensure classes are compiled
./compile.sh

# Run JBMC
# -cp classes: Classpath
# --unwind 10: Unroll loops 10 times
# --unwinding-assertions: Check that loops don't exceed 10 iterations
# --java-assume-inputs-non-null: Assume inputs are not null (optional, reduces noise)
# Target class: io.github.resilience4j.circuitbreaker.internal.CircuitBreakerVerification

echo "Running JBMC..."
jbmc -cp classes io.github.resilience4j.circuitbreaker.internal.CircuitBreakerVerification \
  --unwind 10 \
  --unwinding-assertions \
  --java-assume-inputs-non-null \
  --trace | tee trace.log
