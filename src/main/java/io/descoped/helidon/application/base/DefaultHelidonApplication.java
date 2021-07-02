package io.descoped.helidon.application.base;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public class DefaultHelidonApplication implements HelidonApplication {

    static Logger LOG;

    static {
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        LOG = LoggerFactory.getLogger(DefaultHelidonApplication.class);
    }

    private final ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 2);
    private final ServiceFactory serviceFactory = ServiceFactory.create();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final WebServerInfoImpl webServerConfig;
    private final Config config;

    public DefaultHelidonApplication(HelidonDeployment deployment) {
        // get all application services
        for (Map.Entry<String, Object> entry : deployment.serviceFactory().entrySet()) {
            // build new serviceFactory and only accept ApplicationService from deployment serviceFactory
            if (!ApplicationService.class.isAssignableFrom(entry.getValue().getClass())) {
                continue;
            }
            // make all application services manged (decorator)
            serviceFactory.put(entry.getKey(), ManagedApplicationService.of((ApplicationService) entry.getValue()));
        }

        // final computed configuration
        config = deployment.config();

        if (deployment.routing() == null) {
            // no web server bootstrap
            webServerConfig = new WebServerInfoImpl(null);
        } else {
            // web server bootstrap
            webServerConfig = new WebServerInfoImpl(config.get(deployment.webserverConfigProperty()));
            serviceFactory.put(WebServer.class, WebServer.builder()
                    .config(config.get(deployment.webserverConfigProperty()))
                    .routing(deployment.routing())
                    .build()
            );
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public Config configuration() {
        return config;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R getService(Class<R> clazz) {
        return (R) serviceFactory.get(clazz);
    }

    @Override
    public WebServerInfo getWebServerInfo() {
        return webServerConfig;
    }

    @Override
    public Single<? extends HelidonApplication> start() {
        if (closed.get()) {
            return Single.just(this);
        }

        return Single.create(
                Single.just(serviceFactory.entrySet().stream()
                        .filter(entry -> ApplicationService.class.isAssignableFrom(entry.getValue().getClass()))
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> (ManagedApplicationService) e.getValue(), (km, vm) -> km, LinkedHashMap::new)))
                        .thenApply(serviceMap -> {
                            List<CompletableFuture<ManagedApplicationService>> futureList = new ArrayList<>();

                            for (Map.Entry<String, ManagedApplicationService> entry : serviceMap.entrySet()) {
                                CompletableFuture<ManagedApplicationService> startupFuture = new CompletableFuture<>();
                                futureList.add(startupFuture);

                                final String serviceName = entry.getKey();
                                final ManagedApplicationService service = entry.getValue();

                                try {
                                    startupFuture.completeAsync(
                                            () -> {
                                                service.start();
                                                return service;
                                            }, pool)
                                            .exceptionally(t -> {
                                                if (t instanceof RuntimeException e) {
                                                    throw e;
                                                }
                                                throw new RuntimeException(t);
                                            })
                                            .whenComplete((v, ignore) -> {
                                                LOG.info("Service started: {}", serviceName);
                                            });
                                } catch (Throwable t) {
                                    startupFuture.completeExceptionally(t);
                                }
                            }
                            return futureList;
                        })
                        .thenCompose(this::waitForApplicationServiceCompletion)
                        .thenApply(v -> this.getService(WebServer.class))
                        .thenCompose(this::startWebServerIfPresent)
                        .thenApply(v -> this)
        );
    }

    private Single<WebServer> startWebServerIfPresent(WebServer webServer) {
        if (webServer == null) {
            return Single.empty();
        } else {
            final Single<WebServer> webServerSingle = webServer.start();
            tick(1);
            return webServerSingle;
        }
    }

    @Override
    public Single<? extends HelidonApplication> stop() {
        if (!closed.compareAndSet(false, true)) {
            return Single.just(this);
        }

        return Single.create(
                Single.just(this)
                        .thenApply(this::shutdownWebServerIfPresent)
                        .thenApply(v -> serviceFactory.entrySet().stream()
                                .filter(entry -> ApplicationService.class.isAssignableFrom(entry.getValue().getClass()))
                                .sorted(Collections.reverseOrder())
                                .collect(Collectors.toMap(Map.Entry::getKey, e -> (ManagedApplicationService) e.getValue(), (km, vm) -> km, LinkedHashMap::new)))
                        .thenApply(serviceMap -> {
                            List<CompletableFuture<ManagedApplicationService>> futureList = new ArrayList<>();

                            for (Map.Entry<String, ManagedApplicationService> entry : serviceMap.entrySet()) {
                                CompletableFuture<ManagedApplicationService> shutdownFuture = new CompletableFuture<>();
                                futureList.add(shutdownFuture);

                                final String serviceName = entry.getKey();
                                final ManagedApplicationService service = entry.getValue();

                                try {
                                    shutdownFuture.completeAsync(
                                            () -> {
                                                service.stop(30, TimeUnit.SECONDS);
                                                return service;
                                            }, pool)
                                            .exceptionally(t -> {
                                                if (t instanceof RuntimeException e) {
                                                    throw e;
                                                }
                                                throw new RuntimeException(t);
                                            })
                                            .whenComplete((v, ignore) -> {
                                                LOG.info("Service stopped: {}", serviceName);
                                            });
                                } catch (Throwable t) {
                                    shutdownFuture.completeExceptionally(t);
                                }
                            }
                            return futureList;
                        })
                        .thenCompose(this::waitForApplicationServiceCompletion)
                        .thenAccept(v -> {
                            pool.shutdown();
                            try {
                                if (!pool.awaitTermination(15, TimeUnit.SECONDS)) {
                                    pool.shutdownNow();
                                    if (!pool.awaitTermination(15, TimeUnit.SECONDS))
                                        System.err.println("Pool did not terminate");
                                }
                            } catch (InterruptedException ie) {
                                pool.shutdownNow();
                                Thread.currentThread().interrupt();
                            }
                        })
                        .thenCompose(v -> Single.just(this))
        );
    }

    private Single<WebServer> shutdownWebServerIfPresent(HelidonApplication ignore) {
        WebServer webServer = this.getService(WebServer.class);
        if (webServer == null) {
            LOG.warn("shutdownWebServerIfPresent: WebServer NOT present!");
            return Single.empty();
        } else {
            // TODO investigate race condition on shutdown. A workaround is using this Thread.sleep() or .whenShutdown, but that doesn't close the channels
            //      adding one tick to eliminate cancel interrupt exception
            tick(1);
            return webServer.shutdown();
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void tick(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private CompletableFuture<Void> waitForApplicationServiceCompletion(List<CompletableFuture<ManagedApplicationService>> futureList) {
        AtomicReference<Throwable> errorCause = new AtomicReference<>(null);
        return CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]))
                .exceptionally(t -> {
                    if (!errorCause.compareAndSet(null, t)) {
                        LOG.error("Caught multiple exceptions");
                    }
                    return null;
                })
                .whenComplete((v, ignore) -> {
                    if (errorCause.get() != null) {
                        if (errorCause.get() instanceof RuntimeException e) {
                            throw e;
                        }
                        throw new RuntimeException(errorCause.get());
                    }
                });
    }

    // TODO cache value when accessed
    class WebServerInfoImpl extends WebServerInfo {

        private final Config config;

        public WebServerInfoImpl(Config config) {
            this.config = config;
        }

        @Override
        public String protocol() {
            return ofNullable(getService(WebServer.class)).map(ws -> ws.configuration().ssl() == null ? "http" : "https").orElse(null);
        }

        @Override
        public String host() {
            return ofNullable(config).map(conf -> conf.get("host").asString().get()).orElse(null);
        }

        @Override
        public int port() {
            return ofNullable(getService(WebServer.class)).map(WebServer::port).orElse(-1);
        }

        @Override
        public boolean isHttp2Enabled() {
            return ofNullable(getService(WebServer.class)).map(ws -> ws.configuration().isHttp2Enabled()).orElse(false);
        }

    }
}
