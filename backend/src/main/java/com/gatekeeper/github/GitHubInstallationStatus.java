package com.gatekeeper.github;

/**
 * Lifecycle/health status of a {@link GitHubInstallation}, distinct from
 * {@code active} (Milestone 8: Repository Onboarding). {@code active} answers
 * "does this installation still exist on GitHub" (false only once "deleted"
 * is received); this answers the finer-grained question the onboarding UI
 * needs - "is repository synchronization currently healthy" - so a failed
 * sync is visible instead of silently leaving repositories stale.
 * <p>
 * Transitions (see {@link GitHubInstallationService} and
 * {@link GitHubRepositorySyncService}):
 * <pre>
 * CONNECTING --(sync starts)--&gt; SYNCING --(sync succeeds)--&gt; ACTIVE
 *                                   \--(sync fails)--&gt; ERROR
 * ACTIVE/ERROR --(resync)--&gt; SYNCING --&gt; ...
 * any --(installation deleted)--&gt; DISCONNECTED
 * </pre>
 */
public enum GitHubInstallationStatus {

    /** Installation row just created; no synchronization has completed yet. */
    CONNECTING,

    /** At least one synchronization has completed successfully and the installation is not currently syncing. */
    ACTIVE,

    /** A repository synchronization is in progress right now. */
    SYNCING,

    /** The most recent synchronization attempt failed - see {@link GitHubInstallation#getLastSyncError()}. */
    ERROR,

    /** GitHub reported this installation as deleted/uninstalled. */
    DISCONNECTED
}
