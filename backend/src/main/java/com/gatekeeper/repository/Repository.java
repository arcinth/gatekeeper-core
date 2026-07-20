package com.gatekeeper.repository;

import com.gatekeeper.common.BaseEntity;
import com.gatekeeper.github.GitHubInstallation;
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
 * Represents a repository tracked by GateKeeper (docs/Database.md - Repository entity).
 * Every row originates from a GitHub App installation (Milestone 8: Repository
 * Onboarding removed manual creation) - {@code githubRepositoryId}/
 * {@code githubInstallation} are still nullable at the column level only
 * because they predate that decision and existing rows must remain valid;
 * every row RepositoryService now creates populates both.
 */
@Getter
@Setter
@Entity
@Table(name = "repositories")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Repository extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String name;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "github_repository_id", unique = true)
    private Long githubRepositoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_installation_id")
    private GitHubInstallation githubInstallation;

    @Column(name = "default_branch")
    private String defaultBranch;

    /** The GitHub owner/org login, e.g. "octocat" from full_name "octocat/gatekeeper-core". */
    @Column(name = "owner")
    private String owner;
}
