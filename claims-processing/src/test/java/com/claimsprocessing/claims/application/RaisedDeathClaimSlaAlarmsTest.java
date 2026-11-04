package com.claimsprocessing.claims.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.claimsprocessing.claims.domain.ClaimId;
import org.junit.jupiter.api.Test;

class RaisedDeathClaimSlaAlarmsTest {

    private final ClaimId claimId = new ClaimId(UUID.randomUUID());
    private final RaisedDeathClaimSlaAlarms raisedAlarms = new RaisedDeathClaimSlaAlarms();

    @Test
    void should_report_nothing_raised_for_a_new_claim() {
        // given / when / then
        assertThat(raisedAlarms.warningRaised(claimId)).isFalse();
        assertThat(raisedAlarms.breachRaised(claimId)).isFalse();
    }

    @Test
    void should_record_a_warning_without_recording_a_breach() {
        // given / when
        raisedAlarms.recordWarning(claimId);

        // then
        assertThat(raisedAlarms.warningRaised(claimId)).isTrue();
        assertThat(raisedAlarms.breachRaised(claimId)).isFalse();
    }

    @Test
    void should_record_a_breach_and_the_warning() {
        // given / when
        raisedAlarms.recordBreach(claimId);

        // then
        assertThat(raisedAlarms.warningRaised(claimId)).isTrue();
        assertThat(raisedAlarms.breachRaised(claimId)).isTrue();
    }

    @Test
    void should_reject_a_null_claim_id() {
        // given / when / then
        assertThatThrownBy(() -> raisedAlarms.warningRaised(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimId");
        assertThatThrownBy(() -> raisedAlarms.breachRaised(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimId");
        assertThatThrownBy(() -> raisedAlarms.recordWarning(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimId");
        assertThatThrownBy(() -> raisedAlarms.recordBreach(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimId");
    }
}
