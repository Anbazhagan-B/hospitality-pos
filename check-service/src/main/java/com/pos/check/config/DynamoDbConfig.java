package com.pos.check.config;

import com.pos.check.repository.dynamo.CheckRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.time.Duration;

/**
 * DynamoDB clients for the check store.
 *
 * <p>Only created when {@code pos.check.store=dynamodb}, so the JPA path carries
 * no AWS client and opens no connections.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "pos.check.store", havingValue = "dynamodb")
public class DynamoDbConfig {

    @Value("${pos.dynamodb.region:ap-south-1}")
    private String region;

    /**
     * Set to a DynamoDB Local address for development. Empty in AWS, where the
     * SDK resolves the real regional endpoint.
     */
    @Value("${pos.dynamodb.endpoint:}")
    private String endpoint;

    @Value("${pos.dynamodb.table-name:pos-checks}")
    private String tableName;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        var builder = DynamoDbClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        // A POS terminal request has a person standing at a
                        // counter behind it. Failing fast and surfacing an error
                        // beats the SDK's default multi-second retry ladder.
                        .apiCallTimeout(Duration.ofSeconds(5))
                        .apiCallAttemptTimeout(Duration.ofSeconds(2))
                        // Three attempts total. DynamoDB throttling is
                        // transient, so a bounded retry is worth having; an
                        // unbounded one just moves the queue into the client.
                        .retryPolicy(RetryPolicy.builder().numRetries(2).build())
                        .build());

        if (!endpoint.isBlank()) {
            log.warn("DynamoDB endpoint overridden to {} - expected only for local development", endpoint);
            builder.endpointOverride(URI.create(endpoint))
                    // DynamoDB Local validates the signature but not the
                    // credentials themselves, so any non-empty pair works.
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("local", "local")));
        } else {
            // In EKS this resolves to the IRSA web identity token - no static
            // credential exists anywhere in the pod.
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(client)
                .build();
    }

    @Bean
    public DynamoDbTable<CheckRecord> checkTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(CheckRecord.class));
    }
}
