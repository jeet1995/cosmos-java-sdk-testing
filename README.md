# Azure Cosmos java SDK testing repository

cosmos java SDK testing repository for experiments with Azure Cosmos DB Java SDK

### Cosmos DB DR Drill Testing workload
Steps to run Cosmos DB DR Drill Testing workload:

1. cd to the project folder
2. `mvn package` (build the fat jar with dependencies)
3. Running the drill

3.1 With Key-Based Authentication
`java -cp target/Java-SDK-testing-1.0-SNAPSHOT-jar-with-dependencies.jar -Dendpoint="account-endpoint" -Dkey="account-key" -DDATABASE_ID="database-id" -DCONTAINER_ID="container-id" -DTOTAL_DOCUMENTS="total-documents" com.example.common.CosmosDRDrillTesting` (run the jar with command line params)

3.2 With AAD Authentication
`java -cp target/Java-SDK-testing-1.0-SNAPSHOT-jar-with-dependencies.jar -Dendpoint="account-endpoint" -DDATABASE_ID="database-id" -DCONTAINER_ID="container-id" -DTOTAL_DOCUMENTS="total-documents" -DIS_MANAGED_IDENTITY_ENABLED="true" com.example.common.CosmosDRDrillTesting`

3.3 With Gateway Mode
`java -cp target/Java-SDK-testing-1.0-SNAPSHOT-jar-with-dependencies.jar -Dendpoint="account-endpoint" -DDATABASE_ID="database-id" -DCONTAINER_ID="container-id" -DTOTAL_DOCUMENTS="total-documents" -DIS_MANAGED_IDENTITY_ENABLED="true" -DCONNECTION_MODE="GATEWAY" com.example.common.CosmosDRDrillTesting`

3.4 With Duration Limit (ISO8601 format)
`java -cp target/Java-SDK-testing-1.0-SNAPSHOT-jar-with-dependencies.jar -Dendpoint="account-endpoint" -Dkey="account-key" -DDATABASE_ID="database-id" -DCONTAINER_ID="container-id" -DTOTAL_DOCUMENTS="total-documents" -DWORKLOAD_DURATION="PT1H" com.example.common.CosmosDRDrillTesting`

3.5 With Operation Type Control
`java -cp target/Java-SDK-testing-1.0-SNAPSHOT-jar-with-dependencies.jar -Dendpoint="account-endpoint" -Dkey="account-key" -DDATABASE_ID="database-id" -DCONTAINER_ID="container-id" -DTOTAL_DOCUMENTS="total-documents" -DONLY_READS="true" com.example.common.CosmosDRDrillTesting`

3.6 With Combined Duration and Operation Type Control
`java -cp target/Java-SDK-testing-1.0-SNAPSHOT-jar-with-dependencies.jar -Dendpoint="account-endpoint" -Dkey="account-key" -DDATABASE_ID="database-id" -DCONTAINER_ID="container-id" -DTOTAL_DOCUMENTS="total-documents" -DWORKLOAD_DURATION="PT30M" -DONLY_QUERIES="true" com.example.common.CosmosDRDrillTesting`

3.7 With ReadAll Workload (Predefined PK Values)
`java -cp target/Java-SDK-testing-1.0-SNAPSHOT-jar-with-dependencies.jar -Dendpoint="account-endpoint" -Dkey="account-key" -DDATABASE_ID="database-id" -DCONTAINER_ID="container-id" -DTOTAL_DOCUMENTS="total-documents" -DONLY_READALL="true" com.example.common.CosmosDRDrillTesting`

3.8 With ReadAll Workload (Custom PK Values)
`java -cp target/Java-SDK-testing-1.0-SNAPSHOT-jar-with-dependencies.jar -Dendpoint="account-endpoint" -Dkey="account-key" -DDATABASE_ID="database-id" -DCONTAINER_ID="container-id" -DTOTAL_DOCUMENTS="total-documents" -DONLY_READALL="true" -DREADALL_PK_VALUES="1,2,3,4,5" com.example.common.CosmosDRDrillTesting`

Common ISO8601 duration examples:
- `PT30M` - 30 minutes
- `PT1H` - 1 hour
- `PT2H30M` - 2 hours 30 minutes
- `PT1H30M15S` - 1 hour 30 minutes 15 seconds
- `P1D` - 1 day
- `P1DT6H` - 1 day 6 hours

Operation Type Control Options:
- `-DONLY_READS="true"` - Execute only read operations
- `-DONLY_QUERIES="true"` - Execute only query operations  
- `-DONLY_UPSERTS="true"` - Execute only upsert operations
- `-DONLY_READALL="true"` - Execute only readAll operations (reads all items with predefined PK values)
- If none are specified, all operation types are executed (default behavior)

ReadAll Configuration:
- `-DREADALL_PK_VALUES="1,2,3,4,5"` - Comma-separated list of PK values (default: "1,2,3,...,5000" - 5000 entries)
- The workload will randomly select from these PK values and read all items with partition key "pojo-pk-{value}"

4. Kill the workload when done (or it will stop automatically if duration is specified).
