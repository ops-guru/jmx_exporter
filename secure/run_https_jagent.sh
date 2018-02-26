#!/usr/bin/env bash
# Script to run a java application for testing jmx4prometheus.

java -javaagent:../jmx_prometheus_javaagent_secure/target/jmx_prometheus_javaagent_secure-0.2.1-SNAPSHOT.jar=5557:config.yaml:keystore.jks:credentials.txt -jar testapp.jar
