package com.claimsprocessing.claims;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = {
        "claims.death-sla.check-interval=PT24H",
        "claims.worker.poll-interval=PT24H"
})
class ClaimsApplicationTests {

    private final ApplicationContext context;

    ClaimsApplicationTests(ApplicationContext context) {
        this.context = context;
    }

    @Test
    void should_start_application_context() {
        // given / when: @SpringBootTest starts ClaimsApplication before this method

        // then
        assertThat(context).isNotNull();
    }
}
