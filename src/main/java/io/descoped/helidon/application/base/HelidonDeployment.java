package io.descoped.helidon.application.base;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.OverrideSource;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

@SuppressWarnings("ClassCanBeRecord")
public class HelidonDeployment {

    static final Logger LOG = LoggerFactory.getLogger(HelidonDeployment.class);

    private final Config config;
    private final List<ConfigSource> configSources; // keep reference in order copy make a Builder copy of Deployment
    private final List<OverrideSource> overrideSources; // keep reference in order copy make a Builder copy of Deployment
    private final String webserverProperty;
    private final Routing.Builder routingBuilder; // keep reference in order copy make a Builder copy of Deployment
    private final Routing routing;
    private final ServiceFactory serviceFactory;

    private HelidonDeployment(Config config, List<ConfigSource> configSources, List<OverrideSource> overrideSources, String webserverProperty, Routing.Builder routingBuilder, ServiceFactory serviceFactory) {
        this.config = config;
        this.configSources = Collections.unmodifiableList(configSources);
        this.overrideSources = Collections.unmodifiableList(overrideSources);
        this.webserverProperty = webserverProperty;
        this.routingBuilder = routingBuilder;
        this.routing = routingBuilder == null ? null : routingBuilder.build();
        this.serviceFactory = serviceFactory;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder of(HelidonDeployment deployment) {
        Builder copyOfBuilder = new Builder();
        copyOfBuilder.configBuilder = ConfigHelper.createEmptyConfigBuilder().sources(ConfigSources.create(deployment.config));
        copyOfBuilder.configSources = new ArrayList<>(deployment.configSources);
        copyOfBuilder.overrideSources = new ArrayList<>(deployment.overrideSources);
        copyOfBuilder.webserverProperty = deployment.webserverProperty;
        copyOfBuilder.ifRoutingPresent.set(deployment.routingBuilder != null);
        copyOfBuilder.routingBuilder = deployment.routingBuilder;
        copyOfBuilder.serviceFactory = ServiceFactory.copyOf(deployment.serviceFactory);
        return copyOfBuilder;
    }

    public Config config() {
        return config;
    }

    public List<ConfigSource> configSources() {
        return configSources;
    }

    public List<OverrideSource> overrideSources() {
        return overrideSources;
    }

    public String webserverConfigProperty() {
        return webserverProperty;
    }

    public Routing routing() {
        return routing;
    }

    public ServiceFactory serviceFactory() {
        return serviceFactory;
    }

    public static class Builder {

        private Config.Builder configBuilder;
        private List<ConfigSource> configSources = new ArrayList<>();
        private List<OverrideSource> overrideSources = new ArrayList<>();
        private String webserverProperty = "webserver";
        private final AtomicBoolean ifRoutingPresent = new AtomicBoolean(false);
        private Routing.Builder routingBuilder;
        private ServiceFactory serviceFactory = ServiceFactory.create();
        private Config.Builder finalConfigBuilder;

        public Builder() {
            routingBuilder = Routing.builder();
        }

        public Builder configBuilder(Config.Builder defaultConfigBuilder) {
            this.configBuilder = defaultConfigBuilder;
            return this;
        }

        public Builder finalConfigBuilder(Config.Builder finalConfigBuilder) {
            this.finalConfigBuilder = finalConfigBuilder;
            return this;
        }

        public Config.Builder configBuilder() {
            return configBuilder;
        }

        public Builder configSource(ConfigSource configSource) {
            if (!configSources.contains(configSource)) {
                configSources.add(configSource);
            }
            return this;
        }

        public Builder overrideSource(OverrideSource overrideSource) {
            overrideSources.add(overrideSource);
            return this;
        }

        public List<ConfigSource> configSources() {
            return configSources;
        }

        public List<OverrideSource> overrideSources() {
            return overrideSources;
        }

        public Builder webserverConfigProperty(String propertyName) {
            this.webserverProperty = propertyName;
            return this;
        }

        public String webserverConfigProperty() {
            return webserverProperty;
        }

        public Builder routing(Routing.Builder routingBuilder) {
            ifRoutingPresent.compareAndSet(false, true);
            this.routingBuilder = routingBuilder;
            return this;
        }

        public Builder routing(Consumer<Routing.Builder> builder) {
            ifRoutingPresent.compareAndSet(false, true);
            builder.accept(this.routingBuilder);
            return this;
        }

        /**
         * Register Application service
         *
         * @param service
         * @return
         */
        public Builder register(ApplicationService... service) {
            Objects.requireNonNull(service);
            for (ApplicationService _service : service) {
                if (_service.getClass().isAnnotationPresent(Name.class)) {
                    String serviceName = _service.getClass().getDeclaredAnnotation(Name.class).value();
                    serviceFactory.put(serviceName, _service);
                } else {
                    serviceFactory.put(_service.getClass(), _service);
                }
            }
            return this;
        }

        /**
         * Register Application service
         *
         * @param serviceName
         * @param service
         * @return
         */
        public Builder register(String serviceName, ApplicationService service) {
            Objects.requireNonNull(service);
            serviceFactory.put(serviceName, service);
            return this;
        }

        /**
         * Register WebServer service
         *
         * @param service
         * @return
         */
        public Builder register(Service... service) {
            Objects.requireNonNull(service);
            ifRoutingPresent.compareAndSet(false, true);
            routingBuilder.register(service);
            Arrays.asList(service).forEach(_service -> serviceFactory.put(_service.getClass(), _service));
            return this;
        }

        /**
         * Register WebServer service
         *
         * @param serviceSupplier
         * @return
         */
        public Builder register(Function<ServiceFactory, Service> serviceSupplier) {
            Objects.requireNonNull(serviceSupplier);
            ifRoutingPresent.compareAndSet(false, true);
            Service service = serviceSupplier.apply(serviceFactory);
            routingBuilder.register(service);
            serviceFactory.put(service.getClass(), service);
            return this;
        }

        public HelidonDeployment build() {
            Objects.requireNonNull(webserverProperty);
            Objects.requireNonNull(serviceFactory);

            Config.Builder configBuilder = ofNullable(finalConfigBuilder).orElseGet(() -> {
                final Config.Builder mutableBuilder = ofNullable(this.configBuilder).orElseGet(ConfigHelper::createDefaultConfigBuilder);
                configSources.forEach(mutableBuilder::addSource);
                overrideSources.forEach(mutableBuilder::overrides);
                finalConfigBuilder(mutableBuilder); // workaround for: "io.helidon.config.ConfigException: Attempting to load a single config source multiple times. This is a bug."
                return mutableBuilder;
            });

            Config computedConfig = configBuilder.build();

            //LOG.trace("TestServer.Builder Config:\n\t{}", computedConfig.detach().asMap().get().toString().replace(", ", "\n\t").replace("{", "").replace("}", ""));

            return new HelidonDeployment(computedConfig, configSources, overrideSources, webserverProperty, ifRoutingPresent.get() ? routingBuilder : null, serviceFactory);
        }
    }
}
