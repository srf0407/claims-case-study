package com.claimsprocessing.claims.adapter.in.web;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

import com.claimsprocessing.claims.application.port.in.GetClaimUseCase;
import com.claimsprocessing.claims.application.port.in.SubmitClaimUseCase;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/claims")
public class ClaimController {

    private final SubmitClaimUseCase submitClaim;
    private final SubmitClaimMapper submitClaimMapper;
    private final GetClaimUseCase getClaim;
    private final GetClaimMapper getClaimMapper;

    public ClaimController(
            SubmitClaimUseCase submitClaim,
            SubmitClaimMapper submitClaimMapper,
            GetClaimUseCase getClaim,
            GetClaimMapper getClaimMapper) {
        this.submitClaim = Objects.requireNonNull(submitClaim, "submitClaim");
        this.submitClaimMapper = Objects.requireNonNull(submitClaimMapper, "submitClaimMapper");
        this.getClaim = Objects.requireNonNull(getClaim, "getClaim");
        this.getClaimMapper = Objects.requireNonNull(getClaimMapper, "getClaimMapper");
    }

    @PostMapping
    public ResponseEntity<SubmitClaimResponse> submit(@RequestBody SubmitClaimRequest request) {
        ClaimId claimId = submitClaim.submit(submitClaimMapper.toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(claimId.value())
                .toUri();
        return ResponseEntity.created(location).body(submitClaimMapper.toResponse(claimId));
    }

    @GetMapping("/{id}")
    public GetClaimResponse get(@PathVariable UUID id) {
        Claim claim = getClaim.get(getClaimMapper.toQuery(id));
        return getClaimMapper.toResponse(claim);
    }
}
