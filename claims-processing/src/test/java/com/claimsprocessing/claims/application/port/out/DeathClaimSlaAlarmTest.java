package com.claimsprocessing.claims.application.port.out;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.DeathSlaLevel;
import org.junit.jupiter.api.Test;

class DeathClaimSlaAlarmTest {

    @Test
    void should_reject_an_on_time_alarm() {
        // given
        var claimId = new ClaimId(UUID.randomUUID());

        // when / then
        assertThatThrownBy(() -> new DeathClaimSlaAlarm(claimId, DeathSlaLevel.ON_TIME, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("alarm level must be WARNING or BREACH");
    }
}
