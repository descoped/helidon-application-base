package io.descoped.helidon.application.base;

import io.helidon.config.ConfigSources;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class BaseServerTest {

    static final Logger LOG = LoggerFactory.getLogger(BaseServerTest.class);

    @Test
    void testBaseServer() throws InterruptedException, IOException {
        long startTime = System.currentTimeMillis();

        HelidonApplication application = HelidonApplication.newBuilder()
                .deployment(HelidonDeployment.newBuilder()
                                .configSource(ConfigSources.classpath("application-test.yaml").build())
                                .routing(builder -> builder.get("/greet", (req, res) -> res.send("Hello World!")))
                                .register("service", new ApplicationService() {
                                    final static Logger LOG = LoggerFactory.getLogger(ApplicationService.class);

                                    @Override
                                    public void start() {
                                        LOG.info("Starting test service");
//                                        throw new RuntimeException("Blow");
                                    }

                                    @Override
                                    public void stop(long timeout, TimeUnit timeoutUnit) {
                                        LOG.info("Stopped test service ({}, {})!", timeout, timeout);
//                                        throw new RuntimeException("Blow");
                                    }
                                })
                                .register(new Service() {
                                    @Override
                                    public void update(Routing.Rules rules) {
                                        rules.get("/path/{id}", this::handlePath);
                                    }

                                    void handlePath(ServerRequest req, ServerResponse res) {
                                        res.status(200).send("Path: " + req.path());
                                    }
                                })
                                .register(factory -> new Service() {
                                    @Override
                                    public void update(Routing.Rules rules) {
                                        rules.get("/path2/{id}", this::handlePath);
                                    }

                                    void handlePath(ServerRequest req, ServerResponse res) {
                                        res.status(200).send("Path: " + req.path() + " -> " + factory.get("service"));
                                    }
                                })
                                .build()
                )
                .build();

        CountDownLatch latch = new CountDownLatch(2);

        Thread shutdownHook = new Thread(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            LOG.warn("ShutdownHook triggered..");
            application.stop()
                    .toCompletableFuture()
                    .orTimeout(30, TimeUnit.SECONDS)
                    .thenAccept(app -> LOG.info("Uptime duration was: {} ms", System.currentTimeMillis() - startTime))
                    .exceptionally(throwable -> {
                        LOG.error("While shutting down application", throwable);
                        System.exit(1);
                        return null;
                    });
        });
        shutdownHook.start();

        AtomicReference<Throwable> errorCause = new AtomicReference<>();

        application
                .start()
                .toCompletableFuture()
                .orTimeout(30, TimeUnit.SECONDS)
                .thenAccept(app -> LOG.info("Webserver running at port: {}, started in {} ms", app.getService(WebServer.class).port(), System.currentTimeMillis() - startTime))
                .thenAccept(app -> latch.countDown())
                .exceptionally(throwable -> {
                    errorCause.set(throwable);
                    LOG.error("While starting application", throwable);
                    latch.countDown();
                    return null;
                });

        HelidonApplication.WebServerInfo webServerInfo = application.getWebServerInfo();

        {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(String.format("%s://%s:%s/greet", webServerInfo.protocol(), webServerInfo.host(), webServerInfo.port())))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, response.statusCode());
            assertEquals("Hello World!", response.body());
        }

        {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(String.format("http://localhost:%s/path/1", webServerInfo.port())))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, response.statusCode());
            assertEquals("Path: /path/1", response.body());
        }

        {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(String.format("http://localhost:%s/path2/1", webServerInfo.port())))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, response.statusCode());
            assertEquals("Path: /path2/1 -> io.descoped.helidon.application.base.BaseServerTest", response.body().substring(0, response.body().indexOf("$")));
        }

        latch.countDown();

        shutdownHook.join();

        assertNull(errorCause.get(), "Failed exceptionally: " + errorCause.get());
    }

    @Test
    void testNoWebServer() throws InterruptedException {
        long startTime = System.currentTimeMillis();

        HelidonApplication application = HelidonApplication.newBuilder()
                .deployment(HelidonDeployment.newBuilder()
                        .configSource(ConfigSources.classpath("application-test.yaml").build())
                        .register("service", new ApplicationService() {
                            final static Logger LOG = LoggerFactory.getLogger(ApplicationService.class);

                            @Override
                            public void start() {
                                LOG.info("Starting test service");
//                                        throw new RuntimeException("Blow");
                            }

                            @Override
                            public void stop(long timeout, TimeUnit timeoutUnit) {
                                LOG.info("Stopped test service ({}, {})!", timeout, timeout);
//                                        throw new RuntimeException("Blow");
                            }
                        })
                        .build()
                )
                .build();

        CountDownLatch latch = new CountDownLatch(2);

        Thread shutdownHook = new Thread(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            LOG.warn("ShutdownHook triggered..");
            application.stop()
                    .toCompletableFuture()
                    .orTimeout(30, TimeUnit.SECONDS)
                    .thenAccept(app -> LOG.info("Uptime duration was: {} ms", System.currentTimeMillis() - startTime))
                    .exceptionally(throwable -> {
                        LOG.error("While shutting down application", throwable);
                        System.exit(1);
                        return null;
                    });
        });
        shutdownHook.start();

        application
                .start()
                .toCompletableFuture()
                .orTimeout(30, TimeUnit.SECONDS)
                .thenAccept(app -> LOG.info("Application started in {} ms", System.currentTimeMillis() - startTime))
                .thenAccept(app -> latch.countDown())
                .exceptionally(throwable -> {
                    LOG.error("While starting application", throwable);
                    System.exit(1);
                    return null;
                });

        latch.countDown();

        shutdownHook.join();
    };

    public static void _main(String[] args) {
        long startTime = System.currentTimeMillis();

        HelidonApplication application = HelidonApplication.newBuilder()
                .deployment(HelidonDeployment.newBuilder()
                                .configSource(ConfigSources.classpath("application-test.yaml").build())
                                .routing(builder -> builder.get("/greet", (req, res) -> res.send("Hello World!")))
                                .register("service", new ApplicationService() {
                                    final static Logger LOG = LoggerFactory.getLogger(ApplicationService.class);

                                    @Override
                                    public void start() {
                                        LOG.info("Starting test service");
//                                        throw new RuntimeException("Blow");
                                    }

                                    @Override
                                    public void stop(long timeout, TimeUnit timeoutUnit) {
                                        LOG.info("Stopped test service ({}, {})!", timeout, timeout);
//                                        throw new RuntimeException("Blow");
                                    }
                                })
                                .register(new Service() {
                                    @Override
                                    public void update(Routing.Rules rules) {
                                        rules.get("/path/{id}", this::handlePath);
                                    }

                                    void handlePath(ServerRequest req, ServerResponse res) {
                                        res.status(200).send("Path: " + req.path());
                                    }
                                })
                                .register(factory -> new Service() {
                                    @Override
                                    public void update(Routing.Rules rules) {
                                        rules.get("/path2/{id}", this::handlePath);
                                    }

                                    void handlePath(ServerRequest req, ServerResponse res) {
                                        res.status(200).send("Path: " + req.path() + " -> " + factory.get("service"));
                                    }
                                })
                                .build()
                )
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.warn("ShutdownHook triggered..");
            application.stop()
                    .toCompletableFuture()
                    .orTimeout(30, TimeUnit.SECONDS)
                    .thenAccept(app -> LOG.info("Uptime duration was: {} ms", System.currentTimeMillis() - startTime))
                    .exceptionally(throwable -> {
                        LOG.error("While shutting down application", throwable);
                        System.exit(1);
                        return null;
                    });
        }));

        application
                .start()
                .toCompletableFuture()
                .orTimeout(30, TimeUnit.SECONDS)
                .thenAccept(app -> LOG.info("Webserver running at port: {}, started in {} ms", app.getService(WebServer.class).port(), System.currentTimeMillis() - startTime))
                .exceptionally(throwable -> {
                    LOG.error("While starting application", throwable);
                    System.exit(1);
                    return null;
                });
    }
}
