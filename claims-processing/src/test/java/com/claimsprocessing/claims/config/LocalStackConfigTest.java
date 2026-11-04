package com.claimsprocessing.claims.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimsprocessing.claims.adapter.out.persistence.DynamoDbClaimRepository;
import com.claimsprocessing.claims.adapter.out.queue.SqsClaimQueue;
import com.claimsprocessing.claims.application.port.out.ClaimQueuePort;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.sqs.SqsClient;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "claims.death-sla.check-interval=PT24H",
        "claims.worker.poll-interval=PT24H"
})
@ActiveProfiles("localstack")
class LocalStackConfigTest {

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS, enforceOverride = true)
    @SuppressWarnings("unused")
    private SqsClient sqsClient;

    @Autowired
    private LocalStackProperties properties;

    @Autowired
    private ClaimQueuePort queue;

    @Autowired
    private ClaimRepositoryPort repository;

    @Test
    void should_register_localstack_properties_and_adapters() {
        // given / when: the localstack profile starts the application context

        // then
        assertThat(properties.endpoint()).isEqualTo("http://localhost:4566");
        assertThat(properties.region()).isEqualTo("us-east-1");
        assertThat(properties.accessKey()).isEqualTo("test");
        assertThat(properties.secretKey()).isEqualTo("test");
        assertThat(properties.deathQueueName()).isEqualTo("claims-death");
        assertThat(properties.standardQueueName()).isEqualTo("claims-standard");
        assertThat(properties.tableName()).isEqualTo("claims");
        assertThat(queue).isInstanceOf(SqsClaimQueue.class);
        assertThat(repository).isInstanceOf(DynamoDbClaimRepository.class);
    }
}
