$ErrorActionPreference = "Stop"

$BaseUrl = "http://localhost:8080"
$CallbackSecret = "dev-callback-secret"
$PollSeconds = 2
$TimeoutSeconds = 60

function Fail([string]$Message) {
    [Console]::Error.WriteLine($Message)
    exit 1
}

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Uri,
        [string]$Body,
        [int]$ExpectedStatus,
        [hashtable]$Headers
    )
    $request = @{
        Method          = $Method
        Uri             = $Uri
        UseBasicParsing = $true
    }
    if (-not [string]::IsNullOrEmpty($Body)) {
        $request.ContentType = "application/json"
        $request.Body = [Text.Encoding]::UTF8.GetBytes($Body)
    }
    if ($Headers) {
        $request.Headers = $Headers
    }
    try {
        $response = Invoke-WebRequest @request
    } catch {
        Fail "$Method $Uri failed: $($_.Exception.Message)"
    }
    $status = [int]$response.StatusCode
    if ($status -ne $ExpectedStatus) {
        Fail "$Method $Uri returned $status; expected $ExpectedStatus. Body: $($response.Content)"
    }
    if ([string]::IsNullOrWhiteSpace($response.Content)) {
        return $null
    }
    return $response.Content | ConvertFrom-Json
}

function Wait-ClaimStatus {
    param(
        [string]$ClaimId,
        [string]$Expected,
        [string[]]$Pending
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $last = $null
    do {
        $claim = Invoke-Json -Method GET -Uri "$BaseUrl/claims/$ClaimId" -ExpectedStatus 200
        $last = [string]$claim.status
        if ($last -eq $Expected) {
            return $claim
        }
        if ($Pending -notcontains $last) {
            Fail "Claim $ClaimId returned status $last; expected $Expected."
        }
        Start-Sleep -Seconds $PollSeconds
    } while ((Get-Date) -lt $deadline)
    Fail "Timed out after ${TimeoutSeconds}s waiting for claim $ClaimId to reach $Expected; last status was $last."
}

function Get-PaymentSignature([byte[]]$BodyBytes) {
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = [Text.Encoding]::UTF8.GetBytes($CallbackSecret)
    $hash = $hmac.ComputeHash($BodyBytes)
    $hmac.Dispose()
    $encoded = foreach ($byte in $hash) { $byte.ToString("x2") }
    return ($encoded -join "")
}

function Submit-Claim([string]$Body) {
    $created = Invoke-Json -Method POST -Uri "$BaseUrl/claims" -Body $Body -ExpectedStatus 201
    if (-not $created.claimId) {
        Fail "POST /claims did not return a claimId. Body: $Body"
    }
    return [string]$created.claimId
}

$funeralBody = '{"claimType":"FUNERAL","clientId":"C-1","policyNumber":"P-1","amount":150.00,"incidentDate":"2026-03-01"}'
$deathBody = '{"claimType":"DEATH","clientId":"C-1","policyNumber":"P-1","amount":150.00,"incidentDate":"2026-03-01","dateOfDeath":"2026-03-01","deathCertificateRef":"DC-1"}'
$unknownBody = '{"claimType":"FUNERAL","clientId":"C-404","policyNumber":"P-1","amount":150.00,"incidentDate":"2026-03-01"}'

$funeralId = Submit-Claim $funeralBody
$funeralPending = Wait-ClaimStatus -ClaimId $funeralId -Expected "PAYMENT_PENDING" -Pending @("ACCEPTED")
$callback = '{"claimId":"' + $funeralPending.claimId + '","outcome":"PAID","idempotencyKey":"' + $funeralPending.claimId + '"}'
$callbackBytes = [Text.Encoding]::UTF8.GetBytes($callback)
$signature = Get-PaymentSignature $callbackBytes
Invoke-Json -Method POST -Uri "$BaseUrl/payments/callback" -Body $callback -ExpectedStatus 200 -Headers @{
    "X-Payment-Signature" = $signature
} | Out-Null
$funeralPaid = Wait-ClaimStatus -ClaimId $funeralId -Expected "PAID" -Pending @("PAYMENT_PENDING")
Write-Output "FUNERAL $funeralId $($funeralPaid.status)"

$deathId = Submit-Claim $deathBody
Wait-ClaimStatus -ClaimId $deathId -Expected "AWAITING_ANALYST" -Pending @("ACCEPTED") | Out-Null
$decision = Invoke-Json -Method POST -Uri "$BaseUrl/claims/$deathId/decision" -Body '{"decision":"APPROVE"}' -ExpectedStatus 200
if ([string]$decision.status -ne "PAYMENT_PENDING") {
    Fail "POST /claims/$deathId/decision returned status $($decision.status); expected PAYMENT_PENDING."
}
Write-Output "DEATH $deathId decision $($decision.status)"
$deathAfter = Wait-ClaimStatus -ClaimId $deathId -Expected "PAYMENT_PENDING" -Pending @("APPROVED")
Write-Output "DEATH $deathId GET $($deathAfter.status)"

$unknownId = Submit-Claim $unknownBody
$unknown = Wait-ClaimStatus -ClaimId $unknownId -Expected "REJECTED" -Pending @("ACCEPTED")
if ([string]$unknown.rejectionReason -ne "UNKNOWN_CLIENT") {
    Fail "Claim $unknownId was REJECTED with reason $($unknown.rejectionReason); expected UNKNOWN_CLIENT."
}
Write-Output "CLIENT C-404 $unknownId $($unknown.status) $($unknown.rejectionReason)"
