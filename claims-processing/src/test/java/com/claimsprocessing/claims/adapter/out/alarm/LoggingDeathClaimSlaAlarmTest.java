package com.claimsprocessing.claims.adapter.out.alarm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.claimsprocessing.claims.application.port.out.DeathClaimSlaAlarm;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.DeathSlaLevel;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingDeathClaimSlaAlarmTest {

    private final LoggingDeathClaimSlaAlarm alarm = new LoggingDeathClaimSlaAlarm();

    @Test
    void should_log_a_warning_at_warn_with_the_claim_id_and_elapsed_time() {
        // given
        var claimId = new ClaimId(UUID.randomUUID());
        var elapsed = Duration.ofHours(3);
        ListAppender<ILoggingEvent> logs = attach();

        // when
        try {
            alarm.raise(new DeathClaimSlaAlarm(claimId, DeathSlaLevel.WARNING, elapsed));
        } finally {
            detach(logs);
        }

        // then
        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(logs.list.getFirst().getFormattedMessage()).contains(claimId.value().toString(), elapsed.toString());
    }

    @Test
    void should_log_a_breach_at_error_with_the_claim_id_and_elapsed_time() {
        // given
        var claimId = new ClaimId(UUID.randomUUID());
        var elapsed = Duration.ofHours(4);
        ListAppender<ILoggingEvent> logs = attach();

        // when
        try {
            alarm.raise(new DeathClaimSlaAlarm(claimId, DeathSlaLevel.BREACH, elapsed));
        } finally {
            detach(logs);
        }

        // then
        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.getFirst().getLevel()).isEqualTo(Level.ERROR);
        assertThat(logs.list.getFirst().getFormattedMessage()).contains(claimId.value().toString(), elapsed.toString());
    }

    @Test
    void should_reject_a_null_alarm() {
        // given / when / then
        assertThatThrownBy(() -> alarm.raise(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("alarm");
    }

    private static ListAppender<ILoggingEvent> attach() {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingDeathClaimSlaAlarm.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detach(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingDeathClaimSlaAlarm.class);
        logger.detachAppender(appender);
    }
}
