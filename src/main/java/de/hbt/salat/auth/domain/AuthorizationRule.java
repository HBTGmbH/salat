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

    /**
     * Who is granted: ids of {@link SalatUser} (#1204), or {@code *} for every login. Never a login name — it can be
     * changed or anonymized, and whoever got it next would inherit the rule.
     */
    @Column(name = "grantee_id", nullable = false)
    private Set<String> granteeId;

    /**
     * What is granted, by id since #1204; empty or {@code *} for every object of the category. What an id is belongs to
     * the module of the category ({@link AuthorizationObjectProvider}): a login for {@code EMPLOYEE}, an employee for
     * releases, acceptances and working days, an ETL or report definition, and for {@code TIMEREPORT} person and order
     * together ({@code TimereportRuleObject}). A value starting with
     * {@link de.hbt.salat.auth.service.AuthService#UNRESOLVED_PREFIX} could not be assigned when the rules moved to ids
     * and never matches.
     */
    @Column(name = "object_id")
    private Set<String> objectId;

    @Column(name = "access_level", columnDefinition = "varchar", nullable = false)
    private Set<AccessLevel> accessLevels;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

}
