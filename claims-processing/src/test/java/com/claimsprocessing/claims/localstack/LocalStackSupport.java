package com.claimsprocessing.claims.localstack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;

public final class LocalStackSupport {

    private static final LocalStackContainer LOCALSTACK = start();

    private LocalStackSupport() {}

    public static SqsClient sqsClient() {
        return SqsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(credentials())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    public static DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(credentials())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    private static StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey()));
    }

    private static LocalStackContainer start() {
        Path script = Path.of("infra", "localstack", "ready.d", "init-aws.sh").toAbsolutePath();
        if (!Files.isRegularFile(script)) {
            throw new IllegalStateException("LocalStack init script not found at " + script);
        }
        LocalStackContainer container = new LocalStackContainer(
                        DockerImageName.parse("localstack/localstack:4.14.0"))
                .withServices("sqs", "dynamodb")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(script, 0755),
                        "/etc/localstack/init/ready.d/init-aws.sh")
                .waitingFor(Wait.forLogMessage(".*claims-localstack-init-complete.*\\n", 1)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        container.start();
        return container;
    }
}
