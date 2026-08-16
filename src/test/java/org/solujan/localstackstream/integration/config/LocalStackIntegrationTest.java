package org.solujan.localstackstream.integration.config;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public abstract class LocalStackIntegrationTest {

    @Container
    public static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(
                    DockerImageName.parse("localstack/localstack:4.8.1")
            );

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry) {

        registry.add(
                "aws.endpoint",
                () -> LOCALSTACK.getEndpoint().toString()
        );
    }
}
