package com.gatekeeper.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

class ApplicationInfoContributorTest {

    @Test
    void contribute_addsJavaAndSpringBootVersionDetails() {
        Info.Builder builder = new Info.Builder();

        new ApplicationInfoContributor("local").contribute(builder);

        Info info = builder.build();
        assertThat((Map<String, Object>) info.get("java")).containsKey("version");
        assertThat((Map<String, Object>) info.get("spring-boot")).containsKey("version");
    }

    @Test
    void contribute_labelsAProdSubstringAsProduction() {
        Info.Builder builder = new Info.Builder();

        new ApplicationInfoContributor("prod-us-east").contribute(builder);

        Map<String, Object> deployment = (Map<String, Object>) builder.build().get("deployment");
        assertThat(deployment).containsEntry("environment", "Production");
    }

    @Test
    void contribute_labelsAStagingSubstringAsStaging() {
        Info.Builder builder = new Info.Builder();

        new ApplicationInfoContributor("staging-01").contribute(builder);

        Map<String, Object> deployment = (Map<String, Object>) builder.build().get("deployment");
        assertThat(deployment).containsEntry("environment", "Staging");
    }

    @Test
    void contribute_labelsADevSubstringAsDevelopment() {
        Info.Builder builder = new Info.Builder();

        new ApplicationInfoContributor("dev").contribute(builder);

        Map<String, Object> deployment = (Map<String, Object>) builder.build().get("deployment");
        assertThat(deployment).containsEntry("environment", "Development");
    }

    @Test
    void contribute_labelsALocalSubstringAsLocal() {
        Info.Builder builder = new Info.Builder();

        new ApplicationInfoContributor("local").contribute(builder);

        Map<String, Object> deployment = (Map<String, Object>) builder.build().get("deployment");
        assertThat(deployment).containsEntry("environment", "Local");
    }

    @Test
    void contribute_fallsBackToTheRawValueWhenItMatchesNoKnownPattern() {
        Info.Builder builder = new Info.Builder();

        new ApplicationInfoContributor("qa-cluster-7").contribute(builder);

        Map<String, Object> deployment = (Map<String, Object>) builder.build().get("deployment");
        assertThat(deployment).containsEntry("environment", "qa-cluster-7");
    }

    @Test
    void contribute_labelsABlankValueAsUnknown() {
        Info.Builder builder = new Info.Builder();

        new ApplicationInfoContributor("").contribute(builder);

        Map<String, Object> deployment = (Map<String, Object>) builder.build().get("deployment");
        assertThat(deployment).containsEntry("environment", "Unknown");
    }
}
