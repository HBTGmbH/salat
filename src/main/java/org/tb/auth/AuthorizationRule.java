package org.tb.auth;

import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.tb.common.AuditedEntity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "authorization_rule")
public class AuthorizationRule extends AuditedEntity {

    public enum Category { TIMEREPORT, REPORT_DEFINITION, EMPLOYEE }

    @Enumerated(EnumType.STRING)
    @Column(name = "category", columnDefinition = "varchar")
    private Category category;

    @Column(name = "grantor_id")
    private String grantorId;

    @Column(name = "grantee_id")
    private Set<String> granteeId;

    @Column(name = "object_id")
    private Set<String> objectId;

    @Column(name = "access_level", columnDefinition = "varchar")
    private Set<AccessLevel> accessLevels;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

}
