package com.gatekeeper.auditlog;

/**
 * Catalog of events AuditLogService can record. AuditLog is introduced as
 * small, generic, reusable infrastructure (Unified Engineering Report
 * Architecture, Section 8 / ADR-049): this milestone wires exactly one
 * producer - ReportPublicationService, recording ENGINEERING_REPORT_PUBLISHED
 * in the same transaction as its EngineeringReport row. Retrofitting the
 * other event types docs/Database.md names as examples (Repository
 * connected, Policy created, Analysis started/completed, Verdict generated)
 * into their respective modules is a deliberate, explicitly deferred
 * follow-up, not claimed as delivered here - this enum grows one constant at
 * a time as each producer actually adopts it, not speculatively ahead of
 * that.
 */
public enum AuditEventType {
    ENGINEERING_REPORT_PUBLISHED
}
