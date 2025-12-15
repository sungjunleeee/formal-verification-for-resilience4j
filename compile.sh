#!/bin/bash
mkdir -p classes
find src -name "*.java" -print > sources.txt
javac -d classes @sources.txt
