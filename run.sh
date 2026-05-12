#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
mkdir -p out
find src -name "*.java" | xargs javac -d out
java -cp out com.iz.gui.MainWindow
