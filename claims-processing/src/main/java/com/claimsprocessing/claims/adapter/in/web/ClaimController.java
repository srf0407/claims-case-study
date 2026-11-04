package com.claimsprocessing.claims.adapter.in.web;

import java.net.URI;
import java.util.Objects;

import com.claimsprocessing.claims.application.port.in.SubmitClaimUseCase;
import com.claimsprocessing.claims.domain.ClaimId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/claims")
public class ClaimController {

    private final SubmitClaimUseCase submitClaim;
    private final SubmitClaimMapper mapper;

    public ClaimController(SubmitClaimUseCase submitClaim, SubmitClaimMapper mapper) {
        this.submitClaim = Objects.requireNonNull(submitClaim, "submitClaim");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @PostMapping
    public ResponseEntity<SubmitClaimResponse> submit(@RequestBody SubmitClaimRequest request) {
        ClaimId claimId = submitClaim.submit(mapper.toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(claimId.value())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(claimId));
    }
}
