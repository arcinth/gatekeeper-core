package com.gatekeeper.github;

/**
 * Published by GitHubInstallationService after an installation row is
 * created or updated (not on deletion - nothing to synchronize when an App
 * is uninstalled). Triggers GitHubRepositorySyncService to enumerate the
 * installation's accessible repositories via GitHub's REST API and upsert
 * them as Repository rows - see that class's Javadoc for why this can't rely
 * on the installation_repositories webhook alone.
 * <p>
 * Carries only the id, the same convention AnalysisRunReadyForExecutionEvent
 * and VerdictProducedEvent already established: the GitHubInstallation was
 * loaded/saved in the publisher's own transaction and would be stale/unsafe
 * to touch from the listener's own transaction and thread.
 */
record InstallationRepositorySyncRequestedEvent(Long installationId) {
}
