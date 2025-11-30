#!/bin/bash
mkdir -p classes
find src -path "src/java" -prune -o -name "*.java" -print > sources.txt
javac -d classes @sources.txt
