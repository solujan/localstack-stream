package org.solujan.localstackstream.integration.test;

import org.junit.jupiter.api.Test;
import org.solujan.localstackstream.integration.config.LocalStackIntegrationTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class UserIntegrationTest extends LocalStackSmokeTest {

    @LocalServerPort
    int port;

    @Test
    void shouldCreateAndProcessUser() {

        var restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        restClient.post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "id": "3001",
                      "name": "Sofia",
                      "email": "sofia@test.com"
                    }
                    """)
                .retrieve()
                .toBodilessEntity();

        await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {

                    var response = dynamoDbClient.getItem(
                            GetItemRequest.builder()
                                    .tableName("processed_users")
                                    .key(Map.of(
                                            "id",
                                            AttributeValue.fromS("3001")
                                    ))
                                    .build()
                    );

                    assertThat(response.hasItem())
                            .isTrue();

                    assertThat(
                            response.item()
                                    .get("status")
                                    .s()
                    ).isEqualTo("PROCESSED");

                    assertThat(
                            response.item()
                                    .get("name")
                                    .s()
                    ).isEqualTo("Sofia");

                    assertThat(
                            response.item()
                                    .get("email")
                                    .s()
                    ).isEqualTo("sofia@test.com");
                });
    }
}
