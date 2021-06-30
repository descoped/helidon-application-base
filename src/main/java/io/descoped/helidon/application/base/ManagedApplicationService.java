package io.descoped.helidon.application.base;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ManagedApplicationService implements ApplicationService {

    private final ApplicationService delegate;
    private final AtomicBoolean closed = new AtomicBoolean();

    // TODO make a CompletableFuture chain

    private ManagedApplicationService(ApplicationService delegate) {
        this.delegate = delegate;
    }

    public static ManagedApplicationService of(ApplicationService delegate) {
        return new ManagedApplicationService(delegate);
    }

    public ApplicationService getDelegate() {
        return delegate;
    }

    @Override
    public void start() {
        if (closed.compareAndSet(false, true)) {
            delegate.start();
        }
    }

    @Override
    public void stop() {
        if (closed.compareAndSet(true, false)) {
            delegate.stop();
        }
    }

    @Override
    public void stop(long timeout, TimeUnit timeoutUnit) {
        if (closed.compareAndSet(true, false)) {
            delegate.stop(timeout, timeoutUnit);
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

}
