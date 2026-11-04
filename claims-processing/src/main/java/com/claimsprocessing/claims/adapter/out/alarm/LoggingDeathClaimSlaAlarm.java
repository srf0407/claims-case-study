package com.claimsprocessing.claims.adapter.out.alarm;

import java.util.Objects;

import com.claimsprocessing.claims.application.port.out.DeathClaimSlaAlarm;
import com.claimsprocessing.claims.application.port.out.DeathClaimSlaAlarmPort;
import com.claimsprocessing.claims.domain.DeathSlaLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingDeathClaimSlaAlarm implements DeathClaimSlaAlarmPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingDeathClaimSlaAlarm.class);

    @Override
    public void raise(DeathClaimSlaAlarm alarm) {
        Objects.requireNonNull(alarm, "alarm");
        if (alarm.level() == DeathSlaLevel.WARNING) {
            log.warn("Death-claim SLA warning claimId={} elapsed={}", alarm.claimId().value(), alarm.elapsed());
        } else if (alarm.level() == DeathSlaLevel.BREACH) {
            log.error("Death-claim SLA breach claimId={} elapsed={}", alarm.claimId().value(), alarm.elapsed());
        }
    }
}
