package com.claimsprocessing.claims.adapter.in.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.claimsprocessing.claims.application.port.in.CheckDeathClaimSlaUseCase;
import org.junit.jupiter.api.Test;

class DeathClaimSlaCheckSchedulerTest {

    @Test
    void should_call_the_use_case_once() {
        // given
        var useCase = new RecordingUseCase();
        var scheduler = new DeathClaimSlaCheckScheduler(useCase);

        // when
        scheduler.check();

        // then
        assertThat(useCase.calls).isEqualTo(1);
    }

    @Test
    void should_reject_a_null_use_case() {
        // given / when / then
        assertThatThrownBy(() -> new DeathClaimSlaCheckScheduler(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("useCase");
    }

    private static final class RecordingUseCase implements CheckDeathClaimSlaUseCase {

        private int calls;

        @Override
        public void check() {
            calls++;
        }
    }
}
