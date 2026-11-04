package com.claimsprocessing.claims.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ProblemDetailsTest {

    @Test
    void should_build_a_problem_response_with_the_five_fields_and_problem_json() {
        // given / when
        ResponseEntity<ProblemDetail> response = ProblemDetails.of(
                "/problems/invalid-claim",
                "Invalid claim",
                400,
                "clientId is required",
                "/claims");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail body = response.getBody();
        assertThat(body.getType()).isEqualTo(URI.create("/problems/invalid-claim"));
        assertThat(body.getTitle()).isEqualTo("Invalid claim");
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getDetail()).isEqualTo("clientId is required");
        assertThat(body.getInstance()).isEqualTo(URI.create("/claims"));
    }
}
