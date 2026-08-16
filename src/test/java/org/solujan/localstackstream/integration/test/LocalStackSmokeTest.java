package org.solujan.localstackstream.integration.test;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.solujan.localstackstream.integration.config.LocalStackIntegrationTest;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.StreamViewType;
import software.amazon.awssdk.services.lambda.LambdaClient;
import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import software.amazon.awssdk.services.lambda.model.EventSourcePosition;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.Runtime;

class LocalStackSmokeTest
        extends LocalStackIntegrationTest {
    protected static DynamoDbClient dynamoDbClient;
    protected static LambdaClient lambdaClient;
    @BeforeAll
    static void beforeAll() throws Exception {

        dynamoDbClient =
                DynamoDbClient.builder()
                        .endpointOverride(
                                LOCALSTACK.getEndpoint()
                        )
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                credentials()
                        )
                        .build();

        lambdaClient =
                LambdaClient.builder()
                        .endpointOverride(
                                LOCALSTACK.getEndpoint()
                        )
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                credentials()
                        )
                        .build();

        createUsersTable();

        createProcessedUsersTable();

        createLambda();

        String streamArn =
                getStreamArn();

        createEventSourceMapping(
                streamArn
        );
    }

    private static StaticCredentialsProvider credentials() {

        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                        LOCALSTACK.getAccessKey(),
                        LOCALSTACK.getSecretKey()
                )
        );
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

    private static void createLambda() throws IOException {

        byte[] lambdaJar =
                Files.readAllBytes(
                        Path.of("target/lambda.jar")
                );

        lambdaClient.createFunction(builder ->
                builder
                        .functionName(
                                "dynamodb-stream-handler"
                        )
                        .runtime(Runtime.JAVA17)
                        .handler(
                                "org.solujan.localstackstream.lambda.DynamoStreamHandler::handleRequest"
                        )
                        .role(
                                "arn:aws:iam::000000000000:role/lambda-role"
                        )
                        .code(
                                code -> code
                                        .zipFile(
                                                SdkBytes.fromByteArray(
                                                        lambdaJar
                                                )
                                        )
                        )
        );
    }

    private static String getStreamArn() {

        var response =
                dynamoDbClient.describeTable(
                        builder ->
                                builder.tableName("users")
                );

        return response
                .table()
                .latestStreamArn();
    }

    private static void createEventSourceMapping(
            String streamArn) {

        lambdaClient.createEventSourceMapping(
                builder ->
                        builder
                                .functionName(
                                        "dynamodb-stream-handler"
                                )
                                .eventSourceArn(
                                        streamArn
                                )
                                .startingPosition(
                                        EventSourcePosition.LATEST
                                )
                                .batchSize(1)
        );
    }

    @Test
    void shouldStartLocalStack() {

        assertThat(LOCALSTACK.isRunning())
                .isTrue();
    }

}
