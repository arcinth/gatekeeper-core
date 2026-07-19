-- GateKeeper Core - Milestone 6: Policy Management. Stores ONLY per-organization
-- overrides of an existing, code-defined PolicyRule - never the rule catalog
-- itself (id, description, default category/severity stay in code, sourced
-- live from the PolicyRule beans PolicyEngine already discovers via Spring's
-- classpath scan). A row's absence for a given (organization_id, rule_id)
-- means "use that rule's own default" - an organization that never touches
-- this feature gets today's exact behavior, with zero migration risk to any
-- already-persisted PolicyFinding or Verdict.
--
-- rule_id is a plain VARCHAR, not a foreign key to any table, because there
-- is no rule table to reference - see docs/Policy-Development.md for why
-- rule IDs are a stable product contract enforced in code (PolicyEngine
-- fails fast at startup on a duplicate id), not by a database constraint.
--
-- Mutable/updatable (has updated_at, unlike write-once tables unify verdicts/
-- policy_findings): an administrator is expected to toggle these repeatedly.
-- This never touches or retroactively changes any historical PolicyFinding -
-- Analysis Runs remain immutable; only future evaluations see a changed
-- configuration.

CREATE TABLE policy_configurations (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations (id),
    rule_id           VARCHAR(100) NOT NULL,
    enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    severity_override VARCHAR(20),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_policy_configurations_org_rule UNIQUE (organization_id, rule_id)
);

CREATE INDEX idx_policy_configurations_organization_id ON policy_configurations (organization_id);
