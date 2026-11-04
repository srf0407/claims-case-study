package com.claimsprocessing.claims.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.claimsprocessing.claims.application.ClaimQueuedMessageMapper;
import com.claimsprocessing.claims.application.SubmitClaimService;
import com.claimsprocessing.claims.application.port.in.GetClaimQuery;
import com.claimsprocessing.claims.application.port.in.GetClaimUseCase;
import com.claimsprocessing.claims.application.port.in.SubmitClaimCommand;
import com.claimsprocessing.claims.application.port.in.SubmitClaimUseCase;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.application.port.out.InMemoryClaimQueue;
import com.claimsprocessing.claims.application.port.out.InMemoryClaimRepository;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimType;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ClaimControllerTest {

    private static final String CLAIM_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void should_return_201_with_location_and_claim_id() throws Exception {
        // given
        FakeSubmitClaimUseCase useCase = new FakeSubmitClaimUseCase();
        MockMvc mockMvc = mockMvc(useCase);
        String body = """
                {"claimType":"FUNERAL","clientId":"C-1","policyNumber":"P-1","amount":150.00,"incidentDate":"2026-03-01"}
                """;

        // when
        ResultActions actions = mockMvc.perform(post("/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // then
        actions.andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimId").value(CLAIM_ID));
        assertThat(actions.andReturn().getResponse().getHeader("Location"))
                .endsWith("/claims/" + CLAIM_ID);
        assertThat(useCase.command).isEqualTo(new SubmitClaimCommand(
                ClaimType.FUNERAL,
                "C-1",
                "P-1",
                new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 1),
                null,
                null));
    }

    @Test
    void should_return_400_when_the_json_is_malformed() throws Exception {
        // given
        FakeSubmitClaimUseCase useCase = new FakeSubmitClaimUseCase();
        MockMvc mockMvc = mockMvc(useCase);

        // when
        ResultActions actions = mockMvc.perform(post("/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"));

        // then
        expectProblem(
                actions,
                400,
                "/problems/malformed-request",
                "Malformed request",
                "Request body could not be read",
                "/claims");
        assertThat(useCase.calls).isZero();
    }

    @Test
    void should_return_400_when_the_claim_is_invalid_and_leave_the_queue_empty() throws Exception {
        // given
        InMemoryClaimQueue queue = new InMemoryClaimQueue();
        SubmitClaimUseCase useCase = new SubmitClaimService(
                new InMemoryClaimRepository(),
                queue,
                new ClaimQueuedMessageMapper(),
                Clock.fixed(Instant.parse("2026-03-01T10:15:30Z"), ZoneOffset.UTC));
        MockMvc mockMvc = mockMvc(useCase);
        String body = """
                {"claimType":"FUNERAL","clientId":" ","policyNumber":"P-1","amount":150.00,"incidentDate":"2026-03-01"}
                """;

        // when
        ResultActions actions = mockMvc.perform(post("/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // then
        expectProblem(
                actions,
                400,
                "/problems/invalid-claim",
                "Invalid claim",
                "clientId is required",
                "/claims");
        assertThat(queue.messages()).isEmpty();
    }

    @Test
    void should_accept_a_claim_that_includes_an_unknown_json_property() throws Exception {
        // given
        FakeSubmitClaimUseCase useCase = new FakeSubmitClaimUseCase();
        MockMvc mockMvc = mockMvc(useCase);
        String body = """
                {"claimType":"FUNERAL","clientId":"C-1","policyNumber":"P-1","amount":150.00,"incidentDate":"2026-03-01","note":"ignore-me"}
                """;

        // when
        ResultActions actions = mockMvc.perform(post("/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // then
        actions.andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimId").value(CLAIM_ID));
        assertThat(useCase.command).isEqualTo(new SubmitClaimCommand(
                ClaimType.FUNERAL,
                "C-1",
                "P-1",
                new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 1),
                null,
                null));
    }

    @Test
    void should_log_an_unexpected_failure_at_error_with_the_exception() throws Exception {
        // given
        RuntimeException failure = new RuntimeException("boom-marker");
        SubmitClaimUseCase useCase = command -> {
            throw failure;
        };
        MockMvc mockMvc = mockMvc(useCase);
        ListAppender<ILoggingEvent> logs = attachHandlerLog();

        // when
        ResultActions actions;
        try {
            actions = mockMvc.perform(post("/claims")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"claimType":"FUNERAL","clientId":"C-1","policyNumber":"P-1","amount":150.00,"incidentDate":"2026-03-01"}
                            """));
        } finally {
            detachHandlerLog(logs);
        }

        // then
        expectProblem(
                actions,
                500,
                "/problems/internal-error",
                "Internal error",
                "An unexpected error occurred",
                "/claims");
        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(logs.list.get(0).getThrowableProxy().getClassName()).isEqualTo(RuntimeException.class.getName());
        assertThat(logs.list.get(0).getThrowableProxy().getMessage()).isEqualTo("boom-marker");
    }

    @Test
    void should_not_log_an_invalid_claim() throws Exception {
        // given
        InMemoryClaimQueue queue = new InMemoryClaimQueue();
        SubmitClaimUseCase useCase = new SubmitClaimService(
                new InMemoryClaimRepository(),
                queue,
                new ClaimQueuedMessageMapper(),
                Clock.fixed(Instant.parse("2026-03-01T10:15:30Z"), ZoneOffset.UTC));
        MockMvc mockMvc = mockMvc(useCase);
        ListAppender<ILoggingEvent> logs = attachHandlerLog();

        // when
        ResultActions actions;
        try {
            actions = mockMvc.perform(post("/claims")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"claimType":"FUNERAL","clientId":" ","policyNumber":"P-1","amount":150.00,"incidentDate":"2026-03-01"}
                            """));
        } finally {
            detachHandlerLog(logs);
        }

        // then
        actions.andExpect(status().isBadRequest());
        assertThat(logs.list).isEmpty();
    }

    private static void expectProblem(
            ResultActions actions, int status, String type, String title, String detail, String instance)
            throws Exception {
        actions.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(type))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.instance").value(instance));
    }

    private static ListAppender<ILoggingEvent> attachHandlerLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(ClaimExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachHandlerLog(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(ClaimExceptionHandler.class);
        logger.detachAppender(appender);
    }

    private static MockMvc mockMvc(SubmitClaimUseCase useCase) {
        return MockMvcBuilders.standaloneSetup(new ClaimController(
                        useCase, new SubmitClaimMapper(), unusedGet(), new GetClaimMapper()))
                .setControllerAdvice(new ClaimExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private static final class FakeSubmitClaimUseCase implements SubmitClaimUseCase {

        private int calls;
        private SubmitClaimCommand command;

        @Override
        public ClaimId submit(SubmitClaimCommand command) {
            calls++;
            this.command = command;
            return new ClaimId(UUID.fromString(CLAIM_ID));
        }
    }

    private static GetClaimUseCase unusedGet() {
        return new GetClaimUseCase() {
            @Override
            public Claim get(GetClaimQuery query) {
                throw new UnsupportedOperationException("get");
            }
        };
    }
}
