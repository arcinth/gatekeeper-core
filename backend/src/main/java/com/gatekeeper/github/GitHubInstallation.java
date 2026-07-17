package com.gatekeeper.github;

import com.gatekeeper.common.BaseEntity;
import com.gatekeeper.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents one GitHub App installation - the credential/scope boundary GitHub
 * grants when an organization installs the App (Sprint 2 Architecture, Section 3).
 * Rows are created and updated by GitHubInstallationService in response to the
 * "installation" webhook event; installation_repositories (which repositories
 * an installation can see) remains a later milestone.
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
}
