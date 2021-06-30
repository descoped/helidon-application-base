package io.descoped.helidon.application.base;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ServiceFactory {

    private final Map<String, Object> serviceByName = new LinkedHashMap<>();

    private ServiceFactory() {
    }

    public static ServiceFactory create() {
        return new ServiceFactory();
    }

    public static ServiceFactory copyOf(ServiceFactory serviceFactory, Class<?>... filter) {
        ServiceFactory copyOfServiceFactory = new ServiceFactory();
        serviceFactory.entrySet().stream()
                .filter(entry -> List.of(filter).stream().anyMatch(filterClass -> filterClass.isAssignableFrom(entry.getValue().getClass())))
                .forEach(entry -> copyOfServiceFactory.put(entry.getKey(), entry.getValue()));
        return copyOfServiceFactory;
    }

    public void put(String serviceName, Object service) {
        serviceByName.put(serviceName, service);
    }

    public void put(Class<?> serviceClass, Object service) {
        serviceByName.put(serviceClass.getName(), service);
    }

    @SuppressWarnings("unchecked")
    public <R> R get(Class<?> serviceClass) {
        return (R) serviceByName.get(serviceClass.getName());
    }

    @SuppressWarnings("unchecked")
    public <R> R get(String serviceName) {
        return (R) serviceByName.get(serviceName);
    }

    public Set<Map.Entry<String, Object>> entrySet() {
        return serviceByName.entrySet();
    }

}
