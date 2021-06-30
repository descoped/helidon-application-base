package io.descoped.helidon.application.base;

import java.util.concurrent.TimeUnit;

public interface ApplicationService {

    void start();

    default void stop() {
        stop(10, TimeUnit.SECONDS);
    }

    void stop(long timeout, TimeUnit timeoutUnit);

}
