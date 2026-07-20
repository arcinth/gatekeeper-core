package com.gatekeeper.github.dto;

/**
 * The URL that starts GitHub's own "install this App" flow (Milestone 8:
 * Repository Onboarding). The GitHub App's identity (id, slug) never leaves
 * the backend - the frontend only ever sees the fully-formed URL, or
 * {@code appConfigured=false} when {@code gatekeeper.github.app.id}/
 * {@code gatekeeper.github.app.slug} aren't set, so the UI can show "GitHub
 * App not configured" instead of a broken link.
 */
public record InstallUrlResponse(String url, boolean appConfigured) {
}
