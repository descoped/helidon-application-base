package io.descoped.helidon.application.base;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ServiceBootstrapTest {

    static final Logger LOG = LoggerFactory.getLogger(ServiceBootstrapTest.class);

    @Test
    void testBootstrap() throws ExecutionException, InterruptedException {
        List<ManagedApplicationService> serviceList = new ArrayList<>();
        serviceList.add(ManagedApplicationService.of(new TestApplicationService()));
        serviceList.add(ManagedApplicationService.of(new TestApplicationService()));

        List<CompletableFuture<ManagedApplicationService>> futureList = new ArrayList<>();
        for (ManagedApplicationService service : serviceList) {
            CompletableFuture<ManagedApplicationService> startupFuture = new CompletableFuture<>();
            futureList.add(startupFuture);

            try {
                startupFuture.completeAsync(() -> startup(service))
                        .exceptionally(t -> {
                            if (t instanceof RuntimeException e) {
                                throw e;
                            }
                            throw new RuntimeException(t);
                        })
                        .whenComplete((v, ignore) -> {
                            TestApplicationService testService = (TestApplicationService) service.getDelegate();
                            LOG.info("Started: {} -- {} -- {}", service.isClosed(), testService.serviceId, testService.hello);
                        });
            } catch (Throwable t) {
                startupFuture.completeExceptionally(t);
            }
        }

        AtomicReference<Throwable> errorCause = new AtomicReference<>(null);
        Single<ServiceBootstrapTest> single = Single.create(
                CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]))
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
                            LOG.info("Completed!");
                        })
                        .thenCompose(v -> Single.just(this))
        );

//        single.thenCompose(test -> {
//            LOG.info("ON COMPLETE");
//            return CompletableFuture.completedFuture(new Object());
//        });

        Multi<ServiceBootstrapTest> multi = Multi.create(single)
                .onTerminate(() -> {
            LOG.info("ON COMPLETE");
        });

        multi.subscribe(test -> LOG.info("{}", test.hello()));

        single.onComplete(() -> LOG.info("øaksdøkasødkaølskdø")).subscribe(test -> {});

    }

    private String hello() {
        return "Hello there!";
    }

    private ManagedApplicationService startup(ManagedApplicationService service) {
        service.start();
        return service;
    }

    static class TestApplicationService implements ApplicationService {

        final static AtomicInteger count = new AtomicInteger();
        final int serviceId;
        final AtomicReference<String> hello = new AtomicReference<>();

        public TestApplicationService() {
            serviceId = count.incrementAndGet();
        }

        @Override
        public void start() {
            hello.compareAndSet(null, "hello");
            if (serviceId == 1) {
//                throw new RuntimeException("My bad");
            }
        }

        @Override
        public void stop(long timeout, TimeUnit timeoutUnit) {
            hello.compareAndSet("hello", null);
        }
    }

}
