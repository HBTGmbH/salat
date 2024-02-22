package org.tb.auth;

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

    public enum Category { TIMEREPORT, REPORT_DEFINITION}

    @Enumerated(EnumType.STRING)
    @Column(name = "category", columnDefinition = "varchar")
    private Category category;

    @Column(name = "grantor_id")
    private String grantorId;

    @Column(name = "grantee_id")
    private String granteeId;

    @Column(name = "object_id")
    private String objectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", columnDefinition = "varchar")
    private AccessLevel accessLevel;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

}
