package com.example.common;

import com.azure.core.credential.TokenCredential;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainerProactiveInitConfigBuilder;
import com.azure.cosmos.CosmosEndToEndOperationLatencyPolicyConfig;
import com.azure.cosmos.CosmosEndToEndOperationLatencyPolicyConfigBuilder;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.GatewayConnectionConfig;
import com.azure.cosmos.implementation.apachecommons.lang.StringUtils;
import com.azure.cosmos.implementation.guava25.base.Strings;
import com.azure.cosmos.models.CosmosClientTelemetryConfig;
import com.azure.cosmos.models.CosmosContainerIdentity;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.azure.identity.DefaultAzureCredentialBuilder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CosmosDRDrillTesting {

    private static final Logger logger = LoggerFactory.getLogger(CosmosDRDrillTesting.class.getName());

    private static final String query = "select * from c where c.id=@id and c.pk=@pk";

    private static List<CosmosAsyncClient> cosmosAsyncClients = new ArrayList<>();
    private static List<CosmosAsyncDatabase> cosmosAsyncDatabases = new ArrayList<>();
    private static List<CosmosAsyncContainer> cosmosAsyncContainers = new ArrayList<>();

    private static final CosmosEndToEndOperationLatencyPolicyConfig SIX_SECOND_E2E_TIMEOUT = new CosmosEndToEndOperationLatencyPolicyConfigBuilder(Duration.ofSeconds(6)).build();

    private static final CosmosItemRequestOptions POINT_REQ_OPTS = new CosmosItemRequestOptions()
            .setCosmosEndToEndOperationLatencyPolicyConfig(SIX_SECOND_E2E_TIMEOUT);

    private static final CosmosQueryRequestOptions QUERY_REQ_OPTS = new CosmosQueryRequestOptions()
            .setCosmosEndToEndOperationLatencyPolicyConfig(SIX_SECOND_E2E_TIMEOUT);

    private static final int PROCESSOR_COUNT = Runtime.getRuntime().availableProcessors();

    private static final CosmosClientTelemetryConfig TELEMETRY_CONFIG = new CosmosClientTelemetryConfig()
            .diagnosticsHandler(new SamplingCosmosDiagnosticsLogger(10, 60_000));

    private static final TokenCredential CREDENTIAL = new DefaultAzureCredentialBuilder()
            .managedIdentityClientId(Configurations.AAD_MANAGED_IDENTITY_ID)
            .authorityHost(Configurations.AAD_LOGIN_ENDPOINT)
            .tenantId(Configurations.AAD_TENANT_ID)
            .build();

    private static final boolean IS_PROACTIVE_CONNECTION_WARMUP_ENABLED = Boolean.parseBoolean(
            System.getProperty("IS_PROACTIVE_CONNECTION_WARMUP_ENABLED",
                    StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("IS_PROACTIVE_CONNECTION_WARMUP_ENABLED")), "false")));

    private static final int AGGRESSIVE_CONNECTION_WARMUP_DURATION_SECONDS = Integer.parseInt(
            System.getProperty("AGGRESSIVE_CONNECTION_WARMUP_DURATION_SECONDS",
                    StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("AGGRESSIVE_CONNECTION_WARMUP_DURATION_SECONDS")), "60")));

    private static final AtomicBoolean isShutdown = new AtomicBoolean(false);

    public static void main(String[] args) {

        // Add shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered. Closing Cosmos clients...");
            isShutdown.set(true);
            for (CosmosAsyncClient client : cosmosAsyncClients) {
                try {
                    client.close();
                } catch (Exception e) {
                    logger.error("Error closing Cosmos client", e);
                }
            }
            logger.info("All Cosmos clients closed.");
        }));

        CosmosClientBuilder cosmosClientBuilder = new CosmosClientBuilder()
                .endpoint(Configurations.endpoint)
                .preferredRegions(Configurations.PREFERRED_REGIONS);

        if (Configurations.CONNECTION_MODE_AS_STRING.equals("DIRECT")) {
            logger.info("Creating client in direct mode");
            cosmosClientBuilder = cosmosClientBuilder.directMode();

            if (IS_PROACTIVE_CONNECTION_WARMUP_ENABLED) {
                logger.info("Enabling proactive connection warmup with duration: {} seconds, database : {} and container : {}", AGGRESSIVE_CONNECTION_WARMUP_DURATION_SECONDS, Configurations.DATABASE_ID, Configurations.CONTAINER_ID);

                CosmosContainerIdentity cosmosContainerIdentity = new CosmosContainerIdentity(Configurations.DATABASE_ID, Configurations.CONTAINER_ID);

                CosmosContainerProactiveInitConfigBuilder cosmosContainerProactiveInitConfigBuilder = new CosmosContainerProactiveInitConfigBuilder(Collections.singletonList(cosmosContainerIdentity))
                        .setAggressiveWarmupDuration(Duration.ofSeconds(AGGRESSIVE_CONNECTION_WARMUP_DURATION_SECONDS));

                cosmosClientBuilder = cosmosClientBuilder.openConnectionsAndInitCaches(cosmosContainerProactiveInitConfigBuilder.build());
            }

        } else if (Configurations.CONNECTION_MODE_AS_STRING.equals("GATEWAY")) {
            logger.info("Creating client in gateway mode");
            GatewayConnectionConfig gatewayConnectionConfig = GatewayConnectionConfig.getDefaultConfig();
            cosmosClientBuilder = cosmosClientBuilder.gatewayMode(gatewayConnectionConfig);
        } else {
            logger.error("Invalid connection mode: {}", Configurations.CONNECTION_MODE_AS_STRING);
            return;
        }

        if (Configurations.IS_MANAGED_IDENTITY_ENABLED) {
            logger.info("Using managed identity based authentication");
            cosmosClientBuilder = cosmosClientBuilder.credential(CREDENTIAL);
        } else {
            logger.info("Using key-based authentication");
            cosmosClientBuilder = cosmosClientBuilder.key(Configurations.key);
        }

        for (int i = 0; i < Configurations.COSMOS_CLIENT_COUNT; i++) {

            logger.info("Creating client {}", i);

            CosmosAsyncClient cosmosAsyncClient = cosmosClientBuilder
                    .userAgentSuffix("client-" + (Configurations.USER_AGENT_SUFFIX.isEmpty() ? "" : "-" + Configurations.USER_AGENT_SUFFIX + "-") + i)
                    .clientTelemetryConfig(TELEMETRY_CONFIG.enableTransportLevelTracing())
                    .buildAsyncClient();

            cosmosAsyncClients.add(cosmosAsyncClient);

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        //  Create initial database and container if they don't exist
        setupDBAndContainer();

        //  Insert initial data for the workload
        insertData(cosmosAsyncContainers.get(0));

        //  Start the workload
        startWorkload();

        // Wait for the specified duration or indefinitely if no duration is set
        if (Configurations.WORKLOAD_DURATION_PARSED != null) {
            logger.info("Workload will run for: {}", Configurations.WORKLOAD_DURATION_PARSED);
            try {
                Thread.sleep(Configurations.WORKLOAD_DURATION_PARSED.toMillis());
                logger.info("Workload duration completed. Shutting down...");
            } catch (InterruptedException e) {
                logger.warn("Main thread interrupted: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        } else {
            logger.info("Workload will run indefinitely. Press Ctrl+C to stop.");
            Object waitObject = new Object();
            synchronized (waitObject) {
                try {
                    waitObject.wait();
                } catch (InterruptedException e) {
                    logger.warn("Main thread interrupted: {}", e.getMessage(), e);
                    try {
                        throw e;
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                } catch (IllegalMonitorStateException e) {
                    logger.error("Illegal monitor state: {}", e.getMessage(), e);
                    throw e;
                }
            }
        }
    }

    private static void startWorkload() {
        // Determine which operations to execute based on configuration
        List<Integer> availableOperations = new ArrayList<>();
        
        if (Configurations.ONLY_UPSERTS) {
            availableOperations.add(0); // Upsert
            logger.info("Workload configured to execute ONLY_UPSERTS");
        } else if (Configurations.ONLY_READS) {
            availableOperations.add(1); // Read
            logger.info("Workload configured to execute ONLY_READS");
        } else if (Configurations.ONLY_QUERIES) {
            availableOperations.add(2); // Query
            logger.info("Workload configured to execute ONLY_QUERIES");
        } else if (Configurations.ONLY_READALL) {
            availableOperations.add(3); // ReadAll
            logger.info("Workload configured to execute ONLY_READALL with PK values: {}", Configurations.READALL_PK_LIST);
        } else {
            // Default behavior - all operations
            availableOperations.add(0); // Upsert
            availableOperations.add(1); // Read
            availableOperations.add(2); // Query
            availableOperations.add(3); // ReadAll
            logger.info("Workload configured to execute all operation types (upserts, reads, queries, readAll)");
        }
        
        if (availableOperations.isEmpty()) {
            logger.error("No operations configured to execute. Exiting.");
            return;
        }
        
        Mono.just(1)
                .repeat(() -> !isShutdown.get())
                .flatMap(integer -> {
                    int randomOperation = availableOperations.get(ThreadLocalRandom.current().nextInt(availableOperations.size()));
                    int containerId = ThreadLocalRandom.current().nextInt(Configurations.COSMOS_CLIENT_COUNT);
                    switch (randomOperation) {
                        case 0:
                            return Configurations.QPS > 0
                                    ? Mono.delay(Duration.ofMillis(1000 / Configurations.QPS))
                                    .then(upsertItem(cosmosAsyncContainers.get(containerId)))
                                    : upsertItem(cosmosAsyncContainers.get(containerId));
                        case 1:
                            return Configurations.QPS > 0
                                    ? Mono.delay(Duration.ofMillis(1000 / Configurations.QPS))
                                    .then(readItem(cosmosAsyncContainers.get(containerId)))
                                    : readItem(cosmosAsyncContainers.get(containerId));
                        case 2:
                            return Configurations.QPS > 0
                                    ? Mono.delay(Duration.ofMillis(1000 / Configurations.QPS))
                                    .then(queryItem(cosmosAsyncContainers.get(containerId)))
                                    : queryItem(cosmosAsyncContainers.get(containerId));
                        case 3:
                            return Configurations.QPS > 0
                                    ? Mono.delay(Duration.ofMillis(1000 / Configurations.QPS))
                                    .then(readAllItems(cosmosAsyncContainers.get(containerId)))
                                    : readAllItems(cosmosAsyncContainers.get(containerId));
                        default:
                            return Mono.empty();
                    }
                }, Configurations.QPS > 0 ? 1 : PROCESSOR_COUNT, Configurations.QPS > 0 ? 1 : PROCESSOR_COUNT)
                .onErrorResume(throwable -> {
                    logger.error("Error occurred in workload", throwable);
                    return Mono.empty();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private static Mono<CosmosItemResponse<Pojo>> upsertItem(CosmosAsyncContainer cosmosAsyncContainer) {
        int finalI = ThreadLocalRandom.current().nextInt(Configurations.TOTAL_NUMBER_OF_DOCUMENTS);

        Pojo item = getItem(finalI, finalI);

        logger.debug("upsert item: {}", finalI);
        return cosmosAsyncContainer.upsertItem(item, new PartitionKey(item.getPk()), POINT_REQ_OPTS)
                .onErrorResume(throwable -> {
                    logger.error("Error occurred while upserting item", throwable);

                    if (throwable instanceof CosmosException) {
                        CosmosException cosmosException = (CosmosException) throwable;
                        logger.error("CosmosException: {} - {}", cosmosException.getStatusCode(), cosmosException.getDiagnostics().getDiagnosticsContext());
                    }

                    return Mono.empty();
                });

    }

    private static Mono<CosmosItemResponse<Pojo>> readItem(CosmosAsyncContainer cosmosAsyncContainer) {

        int finalI = ThreadLocalRandom.current().nextInt(Configurations.TOTAL_NUMBER_OF_DOCUMENTS);
        logger.debug("read item: {}", finalI);
        Pojo item = getItem(finalI, finalI);
        return cosmosAsyncContainer.readItem(item.getId(), new PartitionKey(item.getPk()), POINT_REQ_OPTS, Pojo.class)
                .onErrorResume(throwable -> {
                    logger.error("Error occurred while reading item", throwable);

                    if (throwable instanceof CosmosException) {
                        CosmosException cosmosException = (CosmosException) throwable;
                        logger.error("CosmosException: {} - {}", cosmosException.getStatusCode(), cosmosException.getDiagnostics().getDiagnosticsContext());
                    }

                    return Mono.empty();
                });

    }

    private static Mono<List<Pojo>> queryItem(CosmosAsyncContainer cosmosAsyncContainer) {

        int finalI = ThreadLocalRandom.current().nextInt(Configurations.TOTAL_NUMBER_OF_DOCUMENTS);
        logger.debug("query item: {}", finalI);
        Pojo item = getItem(finalI, finalI);

        SqlQuerySpec querySpec = new SqlQuerySpec(query);
        querySpec.setParameters(Arrays.asList(new SqlParameter("@id", item.getId()), new SqlParameter("@pk",
                item.getPk())));
        return cosmosAsyncContainer.queryItems(querySpec, QUERY_REQ_OPTS, Pojo.class)
                .collectList()
                .onErrorResume(throwable -> {
                    logger.error("Error occurred while querying item", throwable);

                    if (throwable instanceof CosmosException) {
                        CosmosException cosmosException = (CosmosException) throwable;
                        logger.error("CosmosException: {} - {}", cosmosException.getStatusCode(), cosmosException.getDiagnostics().getDiagnosticsContext());
                    }

                    return Mono.empty();
                });

    }

    private static Mono<List<Pojo>> readAllItems(CosmosAsyncContainer cosmosAsyncContainer) {
        // Select a random PK from the predefined list
        int finalI = ThreadLocalRandom.current().nextInt(Configurations.TOTAL_NUMBER_OF_DOCUMENTS);
        String pkValue = "pojo-pk-" + (finalI + 1);
        
        logger.debug("readAll items for pk: {}", pkValue);

        return cosmosAsyncContainer.readAllItems(new PartitionKey(pkValue), Pojo.class)
                .collectList()
                .onErrorResume(throwable -> {
                    logger.error("Error occurred while reading all items for pk: {}", pkValue, throwable);

                    if (throwable instanceof CosmosException) {
                        CosmosException cosmosException = (CosmosException) throwable;
                        logger.error("CosmosException: {} - {}", cosmosException.getStatusCode(), cosmosException.getDiagnostics().getDiagnosticsContext());
                    }

                    return Mono.empty();
                });
    }

    private static void insertData(CosmosAsyncContainer cosmosAsyncContainer) {

        if (!Configurations.SHOULD_PREINSERT) {
            logger.info("Skipping initial data insertion as per configuration.");
            return;
        }

        logger.info("Inserting initial data...");

        AtomicInteger successfulInserts = new AtomicInteger(0);

        while (successfulInserts.get() < Configurations.TOTAL_NUMBER_OF_DOCUMENTS) {
            int finalI = successfulInserts.get();

            Pojo item = getItem(finalI, finalI);
            cosmosAsyncContainer
                    .upsertItem(item, new PartitionKey(item.getPk()), POINT_REQ_OPTS)
                    .doOnSuccess(ignore -> successfulInserts.incrementAndGet())
                    .onErrorResume(throwable -> {
                        logger.error("Error occurred while upserting item", throwable);
                        return Mono.empty();
                    })
                    .block();

            if (finalI % 1000 == 0) {
                logger.info("Inserting initial data - inserted {} records", finalI);
            }
        }

        logger.info("Inserted initial data...");
    }

    private static Pojo getItem(int id, int pk) {
        Pojo pojo = new Pojo();
        pojo.id = "pojo-id-" + id;
        pojo.pk = "pojo-pk-" + pk;
        pojo.field = "field-value-" + UUID.randomUUID();
        return pojo;
    }

    private static void setupDBAndContainer() {

        for (CosmosAsyncClient cosmosAsyncClient : cosmosAsyncClients) {
            try {
                logger.info("Setting up database...");
                cosmosAsyncClient.createDatabaseIfNotExists(Configurations.DATABASE_ID).block();
                CosmosAsyncDatabase cosmosAsyncDatabase = cosmosAsyncClient.getDatabase(Configurations.DATABASE_ID);
                logger.info("Setting up container...");
                cosmosAsyncDatabase.createContainerIfNotExists(Configurations.CONTAINER_ID, Configurations.PARTITION_KEY_PATH).block();
                CosmosAsyncContainer cosmosAsyncContainer = cosmosAsyncDatabase.getContainer(Configurations.CONTAINER_ID);

                cosmosAsyncDatabases.add(cosmosAsyncDatabase);
                cosmosAsyncContainers.add(cosmosAsyncContainer);

            } catch (Exception e) {
                logger.error("Error occurred while creating database and container", e);
            }
            logger.info("Setup complete.");
        }
    }
}
