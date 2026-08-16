package org.solujan.localstackstream.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Configuration
public class AwsConfiguration {

    @Bean
    DynamoDbClient dynamoDbClient(
            @Value("${aws.endpoint}") String endpoint,
            @Value("${aws.access_key}") String accessKey,
            @Value("${aws.secret_access_key}") String secretAccessKey) {

        return DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        accessKey,
                                        secretAccessKey
                                )
                        )
                )
                .build();
    }
}