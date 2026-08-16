package org.solujan.localstackstream.service;

import org.solujan.localstackstream.records.CreateUserRequest;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;

@Service
public class UserService {

    private final DynamoDbClient dynamoDbClient;

    public UserService(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public void createUser(CreateUserRequest request) {

        var item = Map.of(
                "id", AttributeValue.fromS(request.id()),
                "name", AttributeValue.fromS(request.name()),
                "email", AttributeValue.fromS(request.email())
        );

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName("users")
                        .item(item)
                        .build()
        );
    }
}
