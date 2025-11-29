#!/bin/bash
mkdir -p classes
find src -name "*.java" > sources.txt
javac -d classes @sources.txt
