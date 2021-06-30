package io.descoped.helidon.application.base;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;

public interface HelidonApplication {

    Config configuration();

    <R> R getService(Class<R> clazz);

    WebServerInfo getWebServerInfo();

    boolean isClosed();

    Single<? extends HelidonApplication> start();

    Single<? extends HelidonApplication> stop();

    static Builder newBuilder() {
        return new Builder();
    }

    class Builder {
        private HelidonDeployment deployment;

        private Builder() {
        }

        public Builder deployment(HelidonDeployment deployment) {
            this.deployment = deployment;
            return this;
        }

        public HelidonApplication build() {
            return new DefaultHelidonApplication(deployment);
        }
    }

    abstract class WebServerInfo {
        abstract public String protocol();

        abstract public String host();

        abstract public int port();

        abstract public boolean isHttp2Enabled();
    }
}
