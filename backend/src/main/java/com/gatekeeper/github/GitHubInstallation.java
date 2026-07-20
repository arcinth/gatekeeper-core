package com.gatekeeper.github;

import com.gatekeeper.common.BaseEntity;
import com.gatekeeper.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents one GitHub App installation - the credential/scope boundary GitHub
 * grants when an organization installs the App (Sprint 2 Architecture, Section 3).
 * Rows are created and updated by GitHubInstallationService in response to the
 * "installation" webhook event.
 * <p>
 * {@code active} answers "does this installation still exist on GitHub"
 * (false only once "deleted" is received) - unchanged since Sprint 2.
 * {@code status}/{@code lastSuccessfulSyncAt}/{@code lastSyncError} are new in
 * Milestone 8 (Repository Onboarding): a finer-grained, UI-facing view of
 * repository-synchronization health, maintained by GitHubInstallationService's
 * mark* methods and read by GitHubInstallationController - see
 * {@link GitHubInstallationStatus}'s own Javadoc for the state machine.
 */
@Getter
@Setter
@Entity
@Table(name = "github_installations")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitHubInstallation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "installation_id", nullable = false, unique = true)
    private Long installationId;

    @Column(name = "github_account_login", nullable = false)
    private String githubAccountLogin;

    @Column(name = "github_account_id")
    private Long githubAccountId;

    @Column(name = "github_account_type")
    private String githubAccountType;

    @Column(name = "repository_selection")
    private String repositorySelection;

    /** Raw JSON object as GitHub sent it, e.g. {"contents":"read","pull_requests":"write"}. */
    @Column(name = "permissions")
    private String permissions;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GitHubInstallationStatus status = GitHubInstallationStatus.CONNECTING;

    /** When repository synchronization last completed successfully; null until the first successful sync. */
    @Column(name = "last_successful_sync_at")
    private Instant lastSuccessfulSyncAt;

    /** The most recent synchronization failure's message; null while status isn't ERROR, cleared on the next success. */
    @Column(name = "last_sync_error", length = 1000)
    private String lastSyncError;
}
