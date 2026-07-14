package com.gatekeeper.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EqualsAndHashCode(of = "id")
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Instant, not OffsetDateTime: Spring Data Commons' default auditing
     * DateTimeProvider hands @CreatedDate/@LastModifiedDate a LocalDateTime,
     * and its DefaultAuditableBeanWrapperFactory only knows how to convert
     * that into LocalDateTime/LocalDate/LocalTime/Instant/Date/Long -
     * OffsetDateTime is not in that list and fails at the first insert with
     * "Cannot convert unsupported date type". Both map to Postgres'
     * timestamptz identically, so this costs nothing and matches the type
     * RefreshToken.createdAt already used.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
