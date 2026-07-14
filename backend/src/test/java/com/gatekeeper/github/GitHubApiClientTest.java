package com.gatekeeper.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.gatekeeper.github.dto.InstallationAccessTokenResponse;
import com.gatekeeper.github.exception.GitHubApiException;
import java.time.Instant;
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

    private MockRestServiceServer mockServer;
    private GitHubApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubApiClient(builder, BASE_URL);
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
}
