package com.gatekeeper.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.github.dto.GitHubFileChange;
import com.gatekeeper.policy.PolicyContext;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.repository.Repository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PolicyContextFactoryTest {

    private final PolicyContextFactory factory = new PolicyContextFactory();

    @Test
    void build_extractsOnlyAddedLinesFromThePatchWithThePrefixStripped() {
        String patch = """
                @@ -1,3 +1,4 @@
                 unchanged context line
                -removed line
                +added line one
                +added line two""";
        AnalysisRun run = analysisRun(1L, "gatekeeper/core");

        PolicyContext context = factory.build(run, List.of(new GitHubFileChange("src/Foo.java", "modified", 3, patch)));

        assertThat(context.analysisRunId()).isEqualTo(1L);
        assertThat(context.repositoryFullName()).isEqualTo("gatekeeper/core");
        assertThat(context.changedFiles()).hasSize(1);
        assertThat(context.changedFiles().get(0).path()).isEqualTo("src/Foo.java");
        assertThat(context.changedFiles().get(0).content()).isEqualTo("added line one\nadded line two");
    }

    @Test
    void build_excludesTheDiffFileHeaderLineEvenThoughItStartsWithPlusPlusPlus() {
        String patch = """
                --- a/src/Foo.java
                +++ b/src/Foo.java
                @@ -1,1 +1,2 @@
                 unchanged
                +added line""";

        PolicyContext context = factory.build(analysisRun(1L, "org/repo"),
                List.of(new GitHubFileChange("src/Foo.java", "modified", 1, patch)));

        assertThat(context.changedFiles().get(0).content()).isEqualTo("added line");
    }

    @Test
    void build_skipsFilesWithANullPatch() {
        PolicyContext context = factory.build(analysisRun(1L, "org/repo"),
                List.of(new GitHubFileChange("image.png", "added", 0, null)));

        assertThat(context.changedFiles()).isEmpty();
    }

    @Test
    void build_skipsFilesWithABlankPatch() {
        PolicyContext context = factory.build(analysisRun(1L, "org/repo"),
                List.of(new GitHubFileChange("empty.txt", "modified", 0, "   ")));

        assertThat(context.changedFiles()).isEmpty();
    }

    @Test
    void build_skipsAPureDeletionPatchThatHasNoAddedLines() {
        String patch = """
                @@ -1,2 +0,0 @@
                -removed line one
                -removed line two""";

        PolicyContext context = factory.build(analysisRun(1L, "org/repo"),
                List.of(new GitHubFileChange("deleted.txt", "removed", 2, patch)));

        assertThat(context.changedFiles()).isEmpty();
    }

    @Test
    void build_handlesMultipleFilesAndMultipleHunksInOnePatch() {
        String multiHunkPatch = """
                @@ -1,1 +1,2 @@
                 context
                +first added line
                @@ -10,1 +11,2 @@
                 more context
                +second added line""";

        PolicyContext context = factory.build(analysisRun(1L, "org/repo"), List.of(
                new GitHubFileChange("a.txt", "modified", 2, multiHunkPatch),
                new GitHubFileChange("b.txt", "modified", 1, "+only line in b")));

        assertThat(context.changedFiles()).hasSize(2);
        assertThat(context.changedFiles().get(0).content()).isEqualTo("first added line\nsecond added line");
        assertThat(context.changedFiles().get(1).content()).isEqualTo("only line in b");
    }

    @Test
    void build_returnsEmptyChangedFilesForAnEmptyInputList() {
        PolicyContext context = factory.build(analysisRun(1L, "org/repo"), List.of());

        assertThat(context.changedFiles()).isEmpty();
    }

    private AnalysisRun analysisRun(Long id, String repositoryFullName) {
        Repository repository = Repository.builder().fullName(repositoryFullName).build();
        PullRequest pullRequest = PullRequest.builder().repository(repository).build();
        AnalysisRun run = AnalysisRun.builder().pullRequest(pullRequest).build();
        ReflectionTestUtils.setField(run, "id", id);
        return run;
    }
}
