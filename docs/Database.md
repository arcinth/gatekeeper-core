# GateKeeper

# Database Design

**Version:** 1.0  
**Status:** Approved  
**Document Owner:** GateKeeper Team

---

# Purpose

This document defines the logical database design for GateKeeper.

The database is the system of record for all governance activities performed by GateKeeper. It stores engineering organizations, repositories, Pull Requests, analysis executions, findings, governance decisions, and audit history.

This document describes the logical data model and relationships only. SQL implementation details, migrations, and optimization strategies will be implemented during development.

---

# Database Philosophy

The GateKeeper database follows five principles.

### 1. Business-Driven Design

Database entities mirror the Domain Model rather than implementation details.

### 2. Immutable Analysis

Every Pull Request analysis creates a new immutable Analysis Run.

Historical analyses are never overwritten.

### 3. Explainable Governance

Every governance decision must be traceable back to deterministic findings.

### 4. AI Isolation

AI-generated findings are stored separately from deterministic findings conceptually, but share a common Finding abstraction.

AI findings never participate in governance decisions.

### 5. Auditability

Every important governance action is permanently recorded.

---

# Database Technology

| Component | Technology |
|------------|------------|
| Database | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| Migration Tool | Flyway |
| Connection Pool | HikariCP |

---

# Entity Overview

The MVP contains the following core entities.

```
Organization
User
Role
Repository
PullRequest
AnalysisRun
Finding
Verdict
EngineeringReport
AuditLog
PolicyDefinition
```

---

# Entity Relationships

```
Organization
│
├── Users
├── Repositories
└── Policy Definitions

Repository
│
└── Pull Requests

Pull Request
│
└── Analysis Runs

Analysis Run
│
├── Findings
├── Verdict
└── Engineering Report

Engineering Report
│
├── Findings
└── Verdict

Audit Log
│
└── References all major entities
```

---

# Entity Descriptions

## Organization

Represents an engineering organization using GateKeeper.

Owns:

- Users
- Repositories
- Policies

Relationship:

```
One Organization
        │
        ├── Many Users
        ├── Many Repositories
        └── Many Policy Definitions
```

---

## User

Represents an authenticated engineering user.

Belongs to one Organization.

Assigned one Role.

---

## Role

Defines permissions granted to users.

Examples:

- Developer
- Technical Lead
- Engineering Manager
- Platform Engineer
- DevSecOps Engineer

---

## Repository

Represents a GitHub repository monitored by GateKeeper.

Belongs to one Organization.

Contains many Pull Requests.

---

## Pull Request

Represents a GitHub Pull Request.

Belongs to one Repository.

Contains many Analysis Runs.

---

## Analysis Run

Represents one complete execution of the GateKeeper analysis pipeline.

Contains:

- Findings
- Verdict
- Engineering Report

Analysis Runs are immutable.

---

## Finding

Represents an engineering observation.

Finding is the parent abstraction for:

- Policy Finding
- Security Finding
- AI Finding

Every Finding contains:

- Source Engine
- Severity
- Classification
- Description
- Recommendation

Classification:

- Deterministic
- Advisory

---

## Verdict

Represents the final governance decision.

Generated only from deterministic findings.

Possible values:

- PASSED
- FAILED

---

## Engineering Report

Represents the unified report shown to developers.

Contains:

- Findings
- Verdict

---

## Policy Definition

Represents organization-specific engineering policies.

Examples:

- No TODO comments
- Controllers must not access repositories directly
- REST APIs require authentication

---

## Audit Log

Stores immutable governance events.

Examples:

- Repository connected
- Policy created
- Analysis started
- Analysis completed
- Verdict generated

---

# High-Level ER Diagram

```
Organization
      │
      ├──────────────┐
      │              │
      ▼              ▼
User          Repository
 │                │
 ▼                ▼
Role       Pull Request
                    │
                    ▼
              Analysis Run
              ├───────────────┐
              ▼               ▼
          Findings        Verdict
              │               │
              └──────┬────────┘
                     ▼
           Engineering Report

Audit Log
  │
  └── References all entities
```

---

# Data Ownership

| Entity | Owner |
|---------|-------|
| Organization | Platform |
| User | Organization |
| Repository | Organization |
| Pull Request | Repository |
| Analysis Run | Pull Request |
| Finding | Analysis Run |
| Verdict | Analysis Run |
| Engineering Report | Analysis Run |
| Policy Definition | Organization |
| Audit Log | System |

---

# Indexing Strategy

The following entities should be indexed.

- Organization
- Repository
- Pull Request Number
- Analysis Run
- Verdict
- Finding Severity
- Repository + Pull Request
- Analysis Timestamp

---

# Integrity Constraints

The database should enforce:

- Every Repository belongs to exactly one Organization.
- Every Pull Request belongs to exactly one Repository.
- Every Analysis Run belongs to exactly one Pull Request.
- Every Finding belongs to exactly one Analysis Run.
- Every Verdict belongs to exactly one Analysis Run.
- Every Engineering Report belongs to exactly one Analysis Run.
- AI Findings must never be used to generate Verdicts.
- Analysis Runs are immutable after completion.

---

# Data Retention

GateKeeper maintains historical analysis records.

A new Analysis Run is created whenever:

- Pull Request opened
- New commits pushed
- Pull Request synchronized
- Manual re-analysis triggered

Previous Analysis Runs remain available for audit and comparison.

---

# Audit Strategy

Every governance event is recorded.

Examples:

- User login
- Repository registration
- Pull Request received
- Analysis started
- Analysis completed
- AI unavailable
- Verdict generated
- Policy modified

Audit records are immutable.

---

# Scalability Considerations

The database is designed for horizontal feature growth.

Future entities may include:

- Architecture Findings
- Performance Findings
- Compliance Findings
- Plugin Registry
- Policy Marketplace
- Risk Scores

These additions should integrate without changing existing entity relationships.

---

# Future Expansion

Future versions may introduce:

- Multi-tenant organizations
- Multiple SCM providers
- Distributed analysis workers
- Plugin ecosystem
- Policy versioning
- Finding history
- Repository groups
- Compliance frameworks

---

# Guiding Principles

The database exists to preserve engineering truth.

- Every Pull Request may produce many Analysis Runs.
- Every Analysis Run records one execution of the analysis pipeline.
- Every Finding belongs to one Analysis Run.
- Only deterministic findings contribute to the Verdict.
- AI findings remain advisory.
- Every governance decision must be explainable.
- Every important action must be auditable.