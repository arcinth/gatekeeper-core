package com.gatekeeper.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.gatekeeper.github.dto.CheckRunOutput;
import com.gatekeeper.github.dto.CheckRunResponse;
import com.gatekeeper.github.dto.CreateCheckRunRequest;
import com.gatekeeper.github.dto.GitHubFileChange;
import com.gatekeeper.github.dto.InstallationAccessTokenResponse;
import com.gatekeeper.github.dto.InstallationRepositoriesResponse.RepositorySummary;
import com.gatekeeper.github.dto.UpdateCheckRunRequest;
import com.gatekeeper.github.exception.GitHubApiException;
import com.gatekeeper.github.exception.GitHubTransientApiException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubApiClientTest {

    private static final String BASE_URL = "https://api.github.com";
    private static final int MAX_CHANGED_FILES = 300;

    private MockRestServiceServer mockServer;
    private GitHubApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubApiClient(builder, BASE_URL, MAX_CHANGED_FILES);
    }

    @Test
    void mintInstallationAccessToken_parsesTokenAndExpiryFromResponse() {
        mockServer.expect(requestTo(BASE_URL + "/app/installations/42/access_tokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-app-jwt"))
                .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"token\":\"ghs_abc123\",\"expires_at\":\"2026-07-14T13:00:00Z\"}"));

        InstallationAccessTokenResponse response = client.mintInstallationAccessToken(42, "test-app-jwt");

        assertThat(response.token()).isEqualTo("ghs_abc123");
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-07-14T13:00:00Z"));
    }

    @Test
    void mintInstallationAccessToken_wrapsClientErrorResponseAsGitHubApiException() {
        mockServer.expect(requestTo(BASE_URL + "/app/installations/42/access_tokens"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.mintInstallationAccessToken(42, "bad-jwt"))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("401");
    }

    @Test
    void mintInstallationAccessToken_wrapsServerErrorResponseAsGitHubApiException() {
        mockServer.expect(requestTo(BASE_URL + "/app/installations/42/access_tokens"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.mintInstallationAccessToken(42, "app-jwt"))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("503");
    }

    @Test
    void fetchPullRequestFiles_parsesFilesAndUsesSeparateOwnerAndRepoPathSegments() {
        mockServer.expect(requestTo(BASE_URL + "/repos/gatekeeper/core/pulls/7/files?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer installation-token"))
                .andRespond(withSuccess(
                        "[{\"filename\":\"src/Foo.java\",\"status\":\"modified\",\"changes\":3,"
                                + "\"patch\":\"@@ -1 +1 @@\\n+added\"}]",
                        MediaType.APPLICATION_JSON));

        List<GitHubFileChange> files = client.fetchPullRequestFiles("gatekeeper/core", 7, "installation-token");

        assertThat(files).hasSize(1);
        assertThat(files.get(0).filename()).isEqualTo("src/Foo.java");
        assertThat(files.get(0).patch()).contains("+added");
    }

    @Test
    void fetchPullRequestFiles_splitsRepositoryFullNameSoTheInternalSlashIsNotPercentEncoded() {
        // A naive single "{repo}" path variable containing "gatekeeper/core" would have
        // its internal slash percent-encoded by URI template expansion, producing a URL
        // GitHub would 404 on. Asserting the exact expected URL (unencoded) catches that regression.
        mockServer.expect(requestTo(BASE_URL + "/repos/gatekeeper/core/pulls/7/files?per_page=100&page=1"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.fetchPullRequestFiles("gatekeeper/core", 7, "token")).isEmpty();
    }

    @Test
    void fetchPullRequestFiles_paginatesUntilAPageIsNotFull() {
        String fullPage = fullPageOfFilesJson(100);
        mockServer.expect(requestTo(BASE_URL + "/repos/org/repo/pulls/7/files?per_page=100&page=1"))
                .andRespond(withSuccess(fullPage, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(BASE_URL + "/repos/org/repo/pulls/7/files?per_page=100&page=2"))
                .andRespond(withSuccess(
                        "[{\"filename\":\"last.txt\",\"status\":\"added\",\"changes\":1,\"patch\":\"+x\"}]",
                        MediaType.APPLICATION_JSON));

        List<GitHubFileChange> files = client.fetchPullRequestFiles("org/repo", 7, "token");

        assertThat(files).hasSize(101);
        assertThat(files.get(100).filename()).isEqualTo("last.txt");
    }

    @Test
    void fetchPullRequestFiles_truncatesAtTheConfiguredMaximum() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubApiClient smallLimitClient = new GitHubApiClient(builder, BASE_URL, 50);

        server.expect(requestTo(BASE_URL + "/repos/org/repo/pulls/7/files?per_page=100&page=1"))
                .andRespond(withSuccess(fullPageOfFilesJson(100), MediaType.APPLICATION_JSON));

        List<GitHubFileChange> files = smallLimitClient.fetchPullRequestFiles("org/repo", 7, "token");

        assertThat(files).hasSize(50);
    }

    @Test
    void fetchPullRequestFiles_wrapsA5xxResponseAsTransient() {
        mockServer.expect(requestTo(BASE_URL + "/repos/org/repo/pulls/7/files?per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.fetchPullRequestFiles("org/repo", 7, "token"))
                .isInstanceOf(GitHubTransientApiException.class);
    }

    @Test
    void fetchPullRequestFiles_wrapsA429ResponseAsTransient() {
        mockServer.expect(requestTo(BASE_URL + "/repos/org/repo/pulls/7/files?per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.fetchPullRequestFiles("org/repo", 7, "token"))
                .isInstanceOf(GitHubTransientApiException.class);
    }

    @Test
    void fetchPullRequestFiles_wrapsA404ResponseAsPermanentNotTransient() {
        mockServer.expect(requestTo(BASE_URL + "/repos/org/repo/pulls/7/files?per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchPullRequestFiles("org/repo", 7, "token"))
                .isInstanceOf(GitHubApiException.class)
                .isNotInstanceOf(GitHubTransientApiException.class);
    }

    @Test
    void fetchPullRequestFiles_rejectsARepositoryFullNameWithoutASlash() {
        assertThatThrownBy(() -> client.fetchPullRequestFiles("no-slash", 7, "token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createCheckRun_parsesTheCreatedCheckRunId() {
        Instant now = Instant.parse("2026-07-18T12:00:00Z");
        CreateCheckRunRequest request = new CreateCheckRunRequest(
                "GateKeeper", "abc123", "completed", "success", now, now,
                new CheckRunOutput("Verdict: APPROVED", "No findings."));

        mockServer.expect(requestTo(BASE_URL + "/repos/gatekeeper/core/check-runs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer installation-token"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":555666}"));

        CheckRunResponse response = client.createCheckRun("gatekeeper/core", request, "installation-token");

        assertThat(response.id()).isEqualTo(555666L);
    }

    @Test
    void createCheckRun_wrapsAnErrorResponseAsGitHubApiException() {
        CreateCheckRunRequest request = new CreateCheckRunRequest(
                "GateKeeper", "abc123", "completed", "success", Instant.now(), Instant.now(), null);
        mockServer.expect(requestTo(BASE_URL + "/repos/gatekeeper/core/check-runs"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.createCheckRun("gatekeeper/core", request, "installation-token"))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("403");
    }

    @Test
    void createCheckRun_rejectsARepositoryFullNameWithoutASlash() {
        CreateCheckRunRequest request = new CreateCheckRunRequest(
                "GateKeeper", "abc123", "completed", "success", Instant.now(), Instant.now(), null);

        assertThatThrownBy(() -> client.createCheckRun("no-slash", request, "token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateCheckRun_sendsAPatchToTheCheckRunsIdEndpoint() {
        UpdateCheckRunRequest request = new UpdateCheckRunRequest(
                "completed", "failure", Instant.parse("2026-07-18T12:05:00Z"),
                new CheckRunOutput("Verdict: BLOCKED", "1 blocking finding."));

        mockServer.expect(requestTo(BASE_URL + "/repos/gatekeeper/core/check-runs/555666"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer installation-token"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("{}"));

        client.updateCheckRun("gatekeeper/core", 555666L, request, "installation-token");
    }

    @Test
    void updateCheckRun_wrapsAnErrorResponseAsGitHubApiException() {
        UpdateCheckRunRequest request = new UpdateCheckRunRequest("completed", "failure", Instant.now(), null);
        mockServer.expect(requestTo(BASE_URL + "/repos/gatekeeper/core/check-runs/555666"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.updateCheckRun("gatekeeper/core", 555666L, request, "installation-token"))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("404");
    }

    @Test
    void listInstallationRepositories_parsesEveryRepositoryOnASinglePage() {
        mockServer.expect(requestTo(BASE_URL + "/installation/repositories?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer installation-token"))
                .andRespond(withSuccess(
                        "{\"total_count\":1,\"repositories\":[{\"id\":1299531781,\"name\":\"gatekeeper-core\","
                                + "\"full_name\":\"arcinth/gatekeeper-core\"}]}",
                        MediaType.APPLICATION_JSON));

        List<RepositorySummary> repositories = client.listInstallationRepositories("installation-token");

        assertThat(repositories).hasSize(1);
        assertThat(repositories.get(0).id()).isEqualTo(1299531781L);
        assertThat(repositories.get(0).fullName()).isEqualTo("arcinth/gatekeeper-core");
    }

    @Test
    void listInstallationRepositories_paginatesUntilAPageIsNotFull() {
        mockServer.expect(requestTo(BASE_URL + "/installation/repositories?per_page=100&page=1"))
                .andRespond(withSuccess(fullPageOfRepositoriesJson(100), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(BASE_URL + "/installation/repositories?per_page=100&page=2"))
                .andRespond(withSuccess(
                        "{\"total_count\":101,\"repositories\":[{\"id\":999,\"name\":\"last\",\"full_name\":\"org/last\"}]}",
                        MediaType.APPLICATION_JSON));

        List<RepositorySummary> repositories = client.listInstallationRepositories("installation-token");

        assertThat(repositories).hasSize(101);
        assertThat(repositories.get(100).name()).isEqualTo("last");
    }

    @Test
    void listInstallationRepositories_wrapsA5xxResponseAsTransient() {
        mockServer.expect(requestTo(BASE_URL + "/installation/repositories?per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.listInstallationRepositories("installation-token"))
                .isInstanceOf(GitHubTransientApiException.class);
    }

    @Test
    void listInstallationRepositories_wrapsA401ResponseAsPermanentNotTransient() {
        mockServer.expect(requestTo(BASE_URL + "/installation/repositories?per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.listInstallationRepositories("installation-token"))
                .isInstanceOf(GitHubApiException.class)
                .isNotInstanceOf(GitHubTransientApiException.class);
    }

    private String fullPageOfRepositoriesJson(int count) {
        StringBuilder json = new StringBuilder("{\"total_count\":").append(count).append(",\"repositories\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("{\"id\":").append(i).append(",\"name\":\"repo").append(i)
                    .append("\",\"full_name\":\"org/repo").append(i).append("\"}");
        }
        return json.append("]}").toString();
    }

    private String fullPageOfFilesJson(int count) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("{\"filename\":\"file").append(i).append(".txt\",\"status\":\"modified\",")
                    .append("\"changes\":1,\"patch\":\"+line\"}");
        }
        return json.append("]").toString();
    }
}
