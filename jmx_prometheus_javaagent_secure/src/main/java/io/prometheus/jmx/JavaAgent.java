package io.prometheus.jmx;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.InetSocketAddress;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.hotspot.DefaultExports;

import io.prometheus.jmx.HTTPS_Server;

public class JavaAgent {

    static HTTPS_Server server;

    public static void premain(String agentArgument, Instrumentation instrumentation) throws Exception {
        // Bind to all interfaces by default (this includes IPv6).
        String host = "0.0.0.0";

        // If we have IPv6 address in square brackets, extract it first and then
        // remove it from arguments to prevent confusion from too namy colons.
        Integer indexOfClosingSquareBracket = agentArgument.indexOf("]:");
        if (indexOfClosingSquareBracket >= 0) {
            host = agentArgument.substring(0, indexOfClosingSquareBracket + 1);
            agentArgument = agentArgument.substring(indexOfClosingSquareBracket + 2);
        }

        String[] args = agentArgument.split(":");
        if (args.length < 4 || args.length > 5) {
            System.err.println("Usage: -javaagent:/path/to/JavaAgent.jar=[host:]<port>:<yaml configuration file>:<path_to_keystore>:<path_to_auth_config>");
            System.exit(1);
        }

        int port;
        String file;
        InetSocketAddress socket;
        String keyStoreFile;
        String authConfigFile;

        if (args.length == 5) {
            port = Integer.parseInt(args[1]);
            socket = new InetSocketAddress(args[0], port);
            file = args[2];
            keyStoreFile = args[3];
            authConfigFile = args[4];
        } else {
            port = Integer.parseInt(args[0]);
            socket = new InetSocketAddress(port);
            file = args[1];
            keyStoreFile = args[2];
            authConfigFile = args[3];
        }

        new JmxCollector(new File(file)).register();
        DefaultExports.initialize();
        server = new HTTPS_Server(socket, CollectorRegistry.defaultRegistry, keyStoreFile, authConfigFile, true);
    }
}
