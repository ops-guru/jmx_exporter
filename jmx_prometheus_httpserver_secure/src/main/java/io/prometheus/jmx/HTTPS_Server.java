package io.prometheus.jmx;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.zip.GZIPOutputStream;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpContext;
import java.io.FileInputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.util.Properties;


/**
 * Expose Prometheus metrics using a plain Java HttpsServer.
 * Based on com.sun.net.httpserver.HttpsServer
 * and io.prometheus.client.exporter.HTTPServer
 * <p>
 * Example Usage:
 * <pre>
 * {@code
 * HTTPS_Server server = new HTTPS_Server(1234);
 * }
 * </pre>
 * */
public class HTTPS_Server {

    protected static final String KEYSTORE_PASSWORD = "qwerty";

    protected final HttpsServer server;
    protected final ExecutorService executorService;

    private static class LocalByteArray extends ThreadLocal<ByteArrayOutputStream> {
        protected ByteArrayOutputStream initialValue()
        {
            return new ByteArrayOutputStream(1 << 20);
        }
    }

    static class HTTPMetricHandler implements HttpHandler {
        private CollectorRegistry registry;
        private final LocalByteArray response = new LocalByteArray();

        HTTPMetricHandler(CollectorRegistry registry) {
          this.registry = registry;
        }


        public void handle(HttpExchange t) throws IOException {
            String query = t.getRequestURI().getRawQuery();

            ByteArrayOutputStream response = this.response.get();
            response.reset();
            OutputStreamWriter osw = new OutputStreamWriter(response);
            TextFormat.write004(osw,
                    registry.filteredMetricFamilySamples(parseQuery(query)));
            osw.flush();
            osw.close();
            response.flush();
            response.close();

            t.getResponseHeaders().set("Content-Type",
                    TextFormat.CONTENT_TYPE_004);
            t.getResponseHeaders().set("Content-Length",
                    String.valueOf(response.size()));
            if (shouldUseCompression(t)) {
                t.getResponseHeaders().set("Content-Encoding", "gzip");
                t.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
                final GZIPOutputStream os = new GZIPOutputStream(t.getResponseBody());
                response.writeTo(os);
                os.finish();
            } else {
                t.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.size());
                response.writeTo(t.getResponseBody());
            }
            t.close();
        }

    }

    protected static boolean shouldUseCompression(HttpExchange exchange) {
        List<String> encodingHeaders = exchange.getRequestHeaders().get("Accept-Encoding");
        if (encodingHeaders == null) return false;

        for (String encodingHeader : encodingHeaders) {
            String[] encodings = encodingHeader.split(",");
            for (String encoding : encodings) {
                if (encoding.trim().toLowerCase().equals("gzip")) {
                    return true;
                }
            }
        }
        return false;
    }

    protected static Set<String> parseQuery(String query) throws IOException {
        Set<String> names = new HashSet<String>();
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx != -1 && URLDecoder.decode(pair.substring(0, idx), "UTF-8").equals("name[]")) {
                    names.add(URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
                }
            }
        }
        return names;
    }


    static class DaemonThreadFactory implements ThreadFactory {
        private ThreadFactory delegate;
        private final boolean daemon;

        DaemonThreadFactory(ThreadFactory delegate, boolean daemon) {
            this.delegate = delegate;
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = delegate.newThread(r);
            t.setDaemon(daemon);
            return t;
        }

        static ThreadFactory defaultThreadFactory(boolean daemon) {
            return new DaemonThreadFactory(Executors.defaultThreadFactory(), daemon);
        }
    }

    /**
     * Start a HTTPS server serving Prometheus metrics from the given registry.
     */
    public HTTPS_Server(InetSocketAddress addr,
                        CollectorRegistry registry,
                        String keyStoreFile,
                        String authConfigFile,
                        boolean daemon) throws IOException {
        server = HttpsServer.create();
        server.bind(addr, 3);
        HttpHandler mHandler = new HTTPMetricHandler(registry);
        HttpContext rootContext = server.createContext("/", mHandler);
        HttpContext metricsContext = server.createContext("/metrics", mHandler);

        try {
            final Properties properties = new Properties();
//            FileInputStream propFile = new FileInputStream("credentials.txt");
            FileInputStream propFile = new FileInputStream(authConfigFile);
            properties.load(propFile);

            BasicAuthenticator authenticator = new BasicAuthenticator("get") {
                @Override
                public boolean checkCredentials (String user, String pwd) {
                    return user.equals(properties.getProperty("username")) && pwd.equals(properties.getProperty("password"));
                }
            };
            rootContext.setAuthenticator(authenticator);
            metricsContext.setAuthenticator(authenticator);

            executorService = Executors.newFixedThreadPool(5, DaemonThreadFactory.defaultThreadFactory(daemon));
            server.setExecutor(executorService);

            KeyStore keyStore = KeyStore.getInstance("JKS");
//            FileInputStream jksFile = new FileInputStream("keystore.jks");
            FileInputStream jksFile = new FileInputStream(keyStoreFile);
            final char[] ks_password = KEYSTORE_PASSWORD.toCharArray();
            keyStore.load(jksFile, ks_password);

            final KeyManagerFactory keyMF = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyMF.init(keyStore, ks_password);

            final TrustManagerFactory trustMF = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustMF.init(keyStore);

            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyMF.getKeyManagers(), trustMF.getTrustManagers(), null);

            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                @Override
                public void configure(final HttpsParameters params) {
                    final SSLContext cfgsslContext = getSSLContext();
                    final SSLParameters sslparams = cfgsslContext.getDefaultSSLParameters();
                    params.setSSLParameters(sslparams);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("\nUnexpected exception on starting HTTPS_Server:\n", e);
        }

        start(daemon);
    }

    /**
     * Start a HTTP server serving Prometheus metrics from the given registry using non-daemon threads.
     */
    public HTTPS_Server(InetSocketAddress addr,
                        CollectorRegistry registry,
                        String keyStoreFile,
                        String authConfigFile) throws IOException {
        this(addr, registry, keyStoreFile, authConfigFile, false);
    }

    /**
     * Start a HTTP server serving the default Prometheus registry.
     */
    public HTTPS_Server(int port,
                        String keyStoreFile,
                        String authConfigFile,
                        boolean daemon) throws IOException {
        this(new InetSocketAddress(port), CollectorRegistry.defaultRegistry, keyStoreFile, authConfigFile, daemon);
    }

    /**
     * Start a HTTP server serving the default Prometheus registry using non-daemon threads.
     */
    public HTTPS_Server(int port,
                        String keyStoreFile,
                        String authConfigFile) throws IOException {
        this(port, keyStoreFile, authConfigFile, false);
    }

    /**
     * Start a HTTP server serving the default Prometheus registry.
     */
    public HTTPS_Server(String host,
                        int port,
                        String keyStoreFile,
                        String authConfigFile,
                        boolean daemon) throws IOException {
        this(new InetSocketAddress(host, port), CollectorRegistry.defaultRegistry, keyStoreFile, authConfigFile, daemon);
    }

    /**
     * Start a HTTP server serving the default Prometheus registry using non-daemon threads.
     */
    public HTTPS_Server(String host,
                        int port,
                        String keyStoreFile,
                        String authConfigFile) throws IOException {
        this(new InetSocketAddress(host, port), CollectorRegistry.defaultRegistry, keyStoreFile, authConfigFile, false);
    }

    /**
     * Start a HTTP server by making sure that its background thread inherit proper daemon flag.
     */
    private void start(boolean daemon) {
        if (daemon == Thread.currentThread().isDaemon()) {
            server.start();
        } else {
            FutureTask<Void> startTask = new FutureTask<Void>(new Runnable() {
                @Override
                public void run() {
                    server.start();
                }
            }, null);
            DaemonThreadFactory.defaultThreadFactory(daemon).newThread(startTask).start();
            try {
                startTask.get();
            } catch (ExecutionException e) {
                throw new RuntimeException("Unexpected exception on starting HTTPSever", e);
            } catch (InterruptedException e) {
                // This is possible only if the current tread has been interrupted,
                // but in real use cases this should not happen.
                // In any case, there is nothing to do, except to propagate interrupted flag.
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Stop the HTTP server.
     */
    public void stop() {
        server.stop(0);
        executorService.shutdown(); // Free any (parked/idle) threads in pool
    }
}
