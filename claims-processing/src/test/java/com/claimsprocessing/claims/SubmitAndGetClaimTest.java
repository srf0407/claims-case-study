package com.claimsprocessing.claims;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "claims.death-sla.check-interval=PT24H",
        "claims.worker.poll-interval=PT24H"
})
class SubmitAndGetClaimTest {

    private final WebApplicationContext context;

    SubmitAndGetClaimTest(WebApplicationContext context) {
        this.context = context;
    }

    @Test
    void should_return_the_accepted_claim_after_it_is_submitted() throws Exception {
        // given
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String body = """
                {"claimType":"FUNERAL","clientId":"C-1","policyNumber":"P-1","amount":150.00,"incidentDate":"2026-03-01"}
                """;

        // when
        MvcResult created = mockMvc.perform(post("/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String claimId = JsonPath.read(created.getResponse().getContentAsString(), "$.claimId");
        var fetched = mockMvc.perform(get("/claims/" + claimId));

        // then
        fetched.andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value(claimId))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }
}
