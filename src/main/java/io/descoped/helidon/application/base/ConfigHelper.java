package io.descoped.helidon.application.base;

import io.helidon.config.Config;

public class ConfigHelper {

    public static Config.Builder createDefaultConfigBuilder() {
        return Config.builder();
    }

    public static Config.Builder createEmptyConfigBuilder() {
        return Config.builder().disableEnvironmentVariablesSource().disableSystemPropertiesSource();
    }

}
