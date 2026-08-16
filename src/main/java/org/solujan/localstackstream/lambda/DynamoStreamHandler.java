package org.solujan.localstackstream.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.net.URI;
import java.util.Map;

public class DynamoStreamHandler
        implements RequestHandler<DynamodbEvent, Void> {

    private final DynamoDbClient dynamoDbClient;

    public DynamoStreamHandler() {

        String endpoint = System.getenv()
                .getOrDefault(
                        "AWS_ENDPOINT_URL",
                        "http://localhost.localstack.cloud:4566"
                );

        this.dynamoDbClient =
                DynamoDbClient.builder()
                        .endpointOverride(URI.create(endpoint))
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create(
                                                "test",
                                                "test"
                                        )
                                )
                        )
                        .build();
    }

    @Override
    public Void handleRequest(
            DynamodbEvent event,
            Context context) {

        for (DynamodbEvent.DynamodbStreamRecord record :
                event.getRecords()) {

            if (!"INSERT".equals(record.getEventName())) {
                continue;
            }

            process(record.getDynamodb());
        }

        return null;
    }

    private void process(StreamRecord streamRecord) {

        Map<String, AttributeValue> image =
                streamRecord.getNewImage();

        String id =
                image.get("id").getS();

        String name =
                image.get("name").getS();

        String email =
                image.get("email").getS();

        var item = Map.of(
                "id",
                software.amazon.awssdk.services.dynamodb.model.AttributeValue
                        .fromS(id),

                "name",
                software.amazon.awssdk.services.dynamodb.model.AttributeValue
                        .fromS(name),

                "email",
                software.amazon.awssdk.services.dynamodb.model.AttributeValue
                        .fromS(email),

                "status",
                software.amazon.awssdk.services.dynamodb.model.AttributeValue
                        .fromS("PROCESSED")
        );

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName("processed_users")
                        .item(item)
                        .build()
        );
    }
}
