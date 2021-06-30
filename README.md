# Application Server Base

Helidon Application is a simple framework that solves:

* Application services with Lifecycle management
* Application deployment
* Application bootstrap with Helidon WebServer 

## Example of use

```java
HelidonApplication.newBuilder()
        .deployment(HelidonDeployment.newBuilder()
                .config(Config.builder().sources(ConfigSources.classpath("application-test.yaml")).build())
                .routing(builder -> builder.get("/greet", (req, res) -> res.send("Hello World!")))
                .build())
        .build()
        .start()
        .toCompletableFuture()
        .orTimeout(10, TimeUnit.SECONDS)
        .exceptionally(throwable -> {
            LOG.error("While starting application", throwable);
            System.exit(1);
            return null;
        });
```
