package com.claimsprocessing.claims.application.port.out;

public interface DeathClaimSlaAlarmPort {

    void raise(DeathClaimSlaAlarm alarm);
}
