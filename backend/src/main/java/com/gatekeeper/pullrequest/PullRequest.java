package com.gatekeeper.pullrequest;

import com.gatekeeper.common.BaseEntity;
import com.gatekeeper.repository.Repository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Represents a GitHub Pull Request tracked by GateKeeper (docs/Database.md -
 * Pull Request entity). Owned by exactly one Repository; contains many
 * AnalysisRuns, one per analyzed commit.
 */
@Getter
@Setter
@Entity
@Table(name = "pull_requests")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PullRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @Column(name = "github_pr_id", nullable = false, unique = true)
    private Long githubPrId;

    @Column(name = "pr_number", nullable = false)
    private Integer number;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(name = "author_login", nullable = false)
    private String authorLogin;

    @Column(name = "source_branch", nullable = false, length = 500)
    private String sourceBranch;

    @Column(name = "target_branch", nullable = false, length = 500)
    private String targetBranch;

    @Column(name = "head_sha", nullable = false, length = 40)
    private String headSha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PullRequestStatus status;
}
