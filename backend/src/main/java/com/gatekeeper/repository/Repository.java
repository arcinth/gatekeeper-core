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
 * Sprint 1 introduced local CRUD only. Sprint 2 Milestone 2 adds the GitHub linkage
 * fields (nullable, since Sprint 1's manually-created repositories predate them and
 * repository onboarding via the installation webhook family isn't implemented yet -
 * only lookup of already-linked repositories is in scope this milestone).
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
}
