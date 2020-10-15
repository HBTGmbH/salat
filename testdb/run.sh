#!/usr/bin/env bash
docker build -t salat-testdb .
docker run --rm -p 3306:3306 --name salat-testdb salat-testdb
# Shutdown with Control-\ (Ctrl+AltGr+\)
