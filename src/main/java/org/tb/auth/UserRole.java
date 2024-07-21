package org.tb.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.tb.common.AuditedEntity;

@Getter
@Setter
@Entity
@Table(name = "user_role")
public class UserRole extends AuditedEntity {

  public enum Role { ADMIN, MANAGER }

  public enum ObjectType { ORDER, EMPLOYEE }

  @Enumerated(EnumType.STRING)
  @Column(name = "role", columnDefinition = "varchar")
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(name = "object_type", columnDefinition = "varchar")
  private ObjectType objectType;

  @Column(name = "user_id")
  private String userId;

  @Column(name = "object_id")
  private String objectId;

  @Column(name = "valid_from")
  private LocalDate validFrom;

  @Column(name = "valid_until")
  private LocalDate validUntil;

}
