package com.example.common;

import com.azure.cosmos.implementation.apachecommons.lang.StringUtils;
import com.azure.cosmos.implementation.guava25.base.Strings;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Configurations {

    public final static String endpoint = System.getProperty("endpoint", System.getenv("endpoint"));
    public final static String key = System.getProperty("key", System.getenv("key"));
    public static final String DATABASE_ID = System.getProperty("DATABASE_ID",
            StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("DATABASE_ID")), "MigrationDB"));
    public static final String CONTAINER_ID = System.getProperty("CONTAINER_ID",
            StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("CONTAINER_ID")), "MigrationContainer"));
    public static final String PARTITION_KEY_PATH = "/pk";
    public static final String TOTAL_DOCUMENTS = System.getProperty("TOTAL_DOCUMENTS",
            StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("TOTAL_DOCUMENTS")), "100000"));
    public static final int TOTAL_NUMBER_OF_DOCUMENTS = Integer.parseInt(TOTAL_DOCUMENTS);
    public static final String CONNECTION_MODE_AS_STRING = System.getProperty("CONNECTION_MODE",
                    StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("CONNECTION_MODE")), "DIRECT"))
            .toUpperCase(Locale.ROOT);
    public static final boolean IS_MANAGED_IDENTITY_ENABLED = Boolean.parseBoolean(
            System.getProperty("IS_MANAGED_IDENTITY_ENABLED",
                    StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("IS_MANAGED_IDENTITY_ENABLED")), "false")));
    public static final String AAD_LOGIN_ENDPOINT = System.getProperty("AAD_LOGIN_ENDPOINT",
            StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("AAD_LOGIN_ENDPOINT")), "https://login.microsoftonline.com/"));
    public static final String AAD_MANAGED_IDENTITY_ID = System.getProperty("AAD_MANAGED_IDENTITY_ID",
            StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("AAD_MANAGED_IDENTITY_ID")), ""));
    public static final String AAD_TENANT_ID = System.getProperty("AAD_TENANT_ID",
            StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("AAD_TENANT_ID")), ""));
    public static final int COSMOS_CLIENT_COUNT = Integer.parseInt(System.getProperty("COSMOS_CLIENT_COUNT",
            StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("COSMOS_CLIENT_COUNT")), "1")));
    public static final List<String> PREFERRED_REGIONS = Arrays.asList(
            System.getProperty("PREFERRED_REGIONS",
                            StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("PREFERRED_REGIONS")), "East US 2 EUAP,Central US EUAP"))
                    .split(","));
    public static final int QPS = Integer.parseInt(System.getProperty("QPS",
            StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("QPS")), "-1")));
    
    // Workload duration in ISO8601 format (e.g., "PT1H" for 1 hour, "PT30M" for 30 minutes)
    public static final String WORKLOAD_DURATION = System.getProperty("WORKLOAD_DURATION",
            StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("WORKLOAD_DURATION")), null));
    
    public static final Duration WORKLOAD_DURATION_PARSED = WORKLOAD_DURATION != null ? 
            Duration.parse(WORKLOAD_DURATION) : null;
    
    // Operation type controls - if any of these are true, only that operation type will be executed
    public static final boolean ONLY_READS = Boolean.parseBoolean(
            System.getProperty("ONLY_READS",
                    StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("ONLY_READS")), "false")));
    
    public static final boolean ONLY_QUERIES = Boolean.parseBoolean(
            System.getProperty("ONLY_QUERIES",
                    StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("ONLY_QUERIES")), "false")));
    
    public static final boolean ONLY_UPSERTS = Boolean.parseBoolean(
            System.getProperty("ONLY_UPSERTS",
                    StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("ONLY_UPSERTS")), "false")));
    
    // ReadAll workload configuration
    public static final boolean ONLY_READALL = Boolean.parseBoolean(
            System.getProperty("ONLY_READALL",
                    StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("ONLY_READALL")), "false")));

    public static final boolean ONLY_UNPARAMETERIZED_QUERIES = Boolean.parseBoolean(
            System.getProperty("ONLY_UNPARAMETERIZED_QUERIES",
                    StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("ONLY_UNPARAMETERIZED_QUERIES")), "false")));
    
    // Predefined PK values for readAll workload (comma-separated integers)
    public static final String READALL_PK_VALUES = System.getProperty("READALL_PK_VALUES",
            StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("READALL_PK_VALUES")), generateDefaultPkList()));

    public static final String USER_AGENT_SUFFIX = System.getProperty("USER_AGENT_SUFFIX",
            StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("USER_AGENT_SUFFIX")), ""));

    public static final boolean SHOULD_PREINSERT = Boolean.parseBoolean(
            System.getProperty("SHOULD_PREINSERT",
                    StringUtils.defaultString(Strings.emptyToNull(System.getenv().get("SHOULD_PREINSERT")), "true")));

    public static final List<String> READALL_PK_LIST = Arrays.asList(READALL_PK_VALUES.split(","));
    
    // Generate default PK list from 1 to 5000
    private static String generateDefaultPkList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5000; i++) {
            if (i > 1) {
                sb.append(",");
            }
            sb.append(i);
        }
        return sb.toString();
    }
}
