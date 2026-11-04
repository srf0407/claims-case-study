package com.claimsprocessing.claims.adapter.out.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import com.claimsprocessing.claims.application.port.out.ClaimQueuedMessage;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimType;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SqsClaimMessageMapperTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final SqsClaimMessageMapper mapper = new SqsClaimMessageMapper(objectMapper);

    @Test
    void should_write_and_read_a_queued_message() {
        // given
        ClaimQueuedMessage message = new ClaimQueuedMessage(
                new ClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111")), ClaimType.DEATH);

        // when
        String body = mapper.toBody(message);
        ClaimQueuedMessage read = mapper.toMessage(body);

        // then
        Map<String, String> fields = objectMapper.readValue(body, new TypeReference<Map<String, String>>() {});
        assertThat(fields).containsOnlyKeys("claimId", "claimType");
        assertThat(fields).containsEntry("claimId", "11111111-1111-1111-1111-111111111111");
        assertThat(fields).containsEntry("claimType", "DEATH");
        assertThat(read).isEqualTo(message);
    }
}
