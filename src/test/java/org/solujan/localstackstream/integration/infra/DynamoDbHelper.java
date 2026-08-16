package org.solujan.localstackstream.integration.infra;

import org.junit.jupiter.api.BeforeAll;
import org.solujan.localstackstream.integration.config.LocalStackIntegrationTest;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.StreamViewType;

public class DynamoDbHelper extends LocalStackIntegrationTest {

    protected static DynamoDbClient dynamoDbClient;

    @BeforeAll
    static void beforeAll() {

        dynamoDbClient =
                DynamoDbClient.builder()
                        .endpointOverride(
                                LOCALSTACK.getEndpoint()
                        )
                        .region(Region.of(
                                LOCALSTACK.getRegion()
                        ))
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create(
                                                LOCALSTACK.getAccessKey(),
                                                LOCALSTACK.getSecretKey()
                                        )
                                )
                        )
                        .build();
        createUsersTable();
        createProcessedUsersTable();
    }

    private static void createUsersTable() {

        dynamoDbClient.createTable(builder ->
                builder
                        .tableName("users")
                        .attributeDefinitions(
                                attribute -> attribute
                                        .attributeName("id")
                                        .attributeType(
                                                ScalarAttributeType.S
                                        )
                        )
                        .keySchema(
                                key -> key
                                        .attributeName("id")
                                        .keyType(KeyType.HASH)
                        )
                        .billingMode(
                                BillingMode.PAY_PER_REQUEST
                        )
                        .streamSpecification(
                                stream -> stream
                                        .streamEnabled(true)
                                        .streamViewType(
                                                StreamViewType.NEW_AND_OLD_IMAGES
                                        )
                        )
        );
    }

    private static void createProcessedUsersTable() {

        dynamoDbClient.createTable(builder ->
                builder
                        .tableName("processed_users")
                        .attributeDefinitions(
                                attribute -> attribute
                                        .attributeName("id")
                                        .attributeType(
                                                ScalarAttributeType.S
                                        )
                        )
                        .keySchema(
                                key -> key
                                        .attributeName("id")
                                        .keyType(KeyType.HASH)
                        )
                        .billingMode(
                                BillingMode.PAY_PER_REQUEST
                        )
        );
    }
}
