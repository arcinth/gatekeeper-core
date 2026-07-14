package com.gatekeeper.github;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives GitHub webhook deliveries. Not a client-facing API (API-Design.md:
 * "not intended for frontend consumption") and not JWT-authenticated - GitHub
 * has no way to present our access tokens, so trust here comes entirely from
 * WebhookSignatureVerifier instead (see SecurityConfig's permitAll for this path).
 */
@RestController
@RequestMapping("/api/v1/github")
@RequiredArgsConstructor
@SecurityRequirements
public class GitHubWebhookController {

    private final WebhookSignatureVerifier webhookSignatureVerifier;
    private final GitHubEventRouter gitHubEventRouter;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveWebhook(
            @RequestBody byte[] payload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId) {

        webhookSignatureVerifier.verify(payload, signature);
        gitHubEventRouter.route(eventType, payload, deliveryId);

        return ResponseEntity.ok().build();
    }
}
