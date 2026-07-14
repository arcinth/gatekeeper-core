package com.gatekeeper.pullrequest;

import com.gatekeeper.repository.Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists Pull Requests received from GitHub. Upserts by githubPrId rather
 * than exposing separate create/update methods: a webhook redelivery for a PR
 * GateKeeper already knows about must update it, not fail or duplicate it
 * (Sprint 2 Architecture, Section 8: Pull Request Lifecycle).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PullRequestService {

    private final PullRequestRepository pullRequestRepository;

    @Transactional
    public PullRequest upsert(Repository repository, PullRequestUpsertCommand command) {
        return pullRequestRepository.findByGithubPrId(command.githubPrId())
                .map(existing -> applyUpdate(existing, command))
                .orElseGet(() -> insertOrRecoverFromRace(repository, command));
    }

    /**
     * Handles the case where a concurrent webhook redelivery for the same PR
     * inserts first: the unique constraint on github_pr_id turns our insert
     * into a DataIntegrityViolationException instead of a duplicate row, and
     * that loss is not a real failure - the PR is correctly persisted either way.
     */
    private PullRequest insertOrRecoverFromRace(Repository repository, PullRequestUpsertCommand command) {
        try {
            return pullRequestRepository.saveAndFlush(buildNew(repository, command));
        } catch (DataIntegrityViolationException ex) {
            return pullRequestRepository.findByGithubPrId(command.githubPrId())
                    .map(existing -> applyUpdate(existing, command))
                    .orElseThrow(() -> ex);
        }
    }

    private PullRequest applyUpdate(PullRequest pullRequest, PullRequestUpsertCommand command) {
        pullRequest.setTitle(command.title());
        pullRequest.setHeadSha(command.headSha());
        pullRequest.setStatus(resolveStatus(command));
        return pullRequestRepository.save(pullRequest);
    }

    private PullRequest buildNew(Repository repository, PullRequestUpsertCommand command) {
        return PullRequest.builder()
                .repository(repository)
                .githubPrId(command.githubPrId())
                .number(command.number())
                .title(command.title())
                .authorLogin(command.authorLogin())
                .sourceBranch(command.sourceBranch())
                .targetBranch(command.targetBranch())
                .headSha(command.headSha())
                .status(resolveStatus(command))
                .build();
    }

    private PullRequestStatus resolveStatus(PullRequestUpsertCommand command) {
        if (command.merged()) {
            return PullRequestStatus.MERGED;
        }
        return "closed".equals(command.githubState()) ? PullRequestStatus.CLOSED : PullRequestStatus.OPEN;
    }
}
