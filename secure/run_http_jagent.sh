#!/usr/bin/env bash
# Script to run a java application for testing jmx4prometheus.

#java -jar testapp.jar
java -javaagent:../jmx_prometheus_javaagent/target/jmx_prometheus_javaagent-0.2.1-SNAPSHOT.jar=5556:config.yaml -jar testapp.jar