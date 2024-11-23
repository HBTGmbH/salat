package org.tb.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.tb.common.domain.AuditedEntity;

@Getter
@Setter
@Entity
@Table(name = "authorization_rule")
public class AuthorizationRule extends AuditedEntity {

    @Column(name = "category")
    private String category;

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
