# Application Server Base

A tiny non-intrusive library that bootstraps your Helidon WebServer with Application services. 

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
            System.exit(0);
            return null;
        });
```
