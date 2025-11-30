#!/bin/bash
# Script to run SpotBugs

# Ensure classes are compiled
./compile.sh

echo "Running SpotBugs..."
spotbugs -textui -effort:max -low -xml:withMessages -output spotbugs.xml classes
spotbugs -textui -effort:max -low classes
