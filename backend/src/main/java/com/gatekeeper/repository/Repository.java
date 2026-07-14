package com.gatekeeper.repository;

import com.gatekeeper.common.BaseEntity;
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
 * Sprint 1 only supports local CRUD; GitHub connectivity is introduced in a later sprint
 * by the github-integration-module described in docs/Architecture.md.
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
}
