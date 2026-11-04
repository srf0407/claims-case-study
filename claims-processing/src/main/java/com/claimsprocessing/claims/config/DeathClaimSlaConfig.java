package com.claimsprocessing.claims.config;

import java.time.Clock;

import com.claimsprocessing.claims.adapter.in.schedule.DeathClaimSlaCheckScheduler;
import com.claimsprocessing.claims.adapter.out.alarm.LoggingDeathClaimSlaAlarm;
import com.claimsprocessing.claims.application.CheckDeathClaimSlaService;
import com.claimsprocessing.claims.application.RaisedDeathClaimSlaAlarms;
import com.claimsprocessing.claims.application.port.in.CheckDeathClaimSlaUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.application.port.out.DeathClaimSlaAlarmPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(DeathClaimSlaProperties.class)
public class DeathClaimSlaConfig {

    @Bean
    RaisedDeathClaimSlaAlarms raisedDeathClaimSlaAlarms() {
        return new RaisedDeathClaimSlaAlarms();
    }

    @Bean
    DeathClaimSlaAlarmPort deathClaimSlaAlarmPort() {
        return new LoggingDeathClaimSlaAlarm();
    }

    @Bean
    CheckDeathClaimSlaUseCase checkDeathClaimSlaUseCase(
            ClaimRepositoryPort claimRepository,
            DeathClaimSlaAlarmPort alarmPort,
            RaisedDeathClaimSlaAlarms raisedAlarms,
            Clock clock,
            DeathClaimSlaProperties properties) {
        return new CheckDeathClaimSlaService(
                claimRepository,
                alarmPort,
                raisedAlarms,
                clock,
                properties.duration(),
                properties.warningFraction());
    }

    @Bean
    DeathClaimSlaCheckScheduler deathClaimSlaCheckScheduler(CheckDeathClaimSlaUseCase checkDeathClaimSla) {
        return new DeathClaimSlaCheckScheduler(checkDeathClaimSla);
    }
}
