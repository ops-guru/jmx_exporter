#!/usr/bin/env bash
# Script to run a java application for testing jmx4prometheus.

# Note: You can use localhost:5557 instead of 5557 for configuring socket hostname.

java -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.port=5555 -DkeyStore=keystore.jks -DauthConfig=credentials.txt -jar ../jmx_prometheus_httpserver_secure/target/jmx_prometheus_httpserver_secure-0.2.1-SNAPSHOT-jar-with-dependencies.jar 5557 ../example_configs/httpserver_sample_config.yml
