package de.hbt.salat.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import de.hbt.salat.common.domain.AuditedEntity;

@Getter
@Setter
@Entity
@Table(name = "authorization_rule")
public class AuthorizationRule extends AuditedEntity {

    /**
     * What the rule is for (#1168). Only for people: {@link de.hbt.salat.auth.service.AuthService} never reads it. Unique
     * regardless of case, checked by the service; {@code null} for a rule that predates the column until it is next
     * saved.
     */
    @Column(name = "name")
    private String name;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "grantee_id", nullable = false)
    private Set<String> granteeId;

    @Column(name = "object_id")
    private Set<String> objectId;

    @Column(name = "access_level", columnDefinition = "varchar", nullable = false)
    private Set<AccessLevel> accessLevels;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

}
