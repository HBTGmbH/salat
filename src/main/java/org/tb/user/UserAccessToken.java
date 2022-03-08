package org.tb.user;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ForeignKey;
import org.tb.common.AuditedEntity;
import org.tb.employee.Employee;

@Entity
@Table(name = "user_access_token")
@Getter
@Setter
public class UserAccessToken extends AuditedEntity {

  @ManyToOne
  @ForeignKey(name = "employee_id")
  private Employee employee;

  @Column(name = "token_id")
  private String tokenId;

  @Column(name = "token_secret_encrypted")
  private String tokenSecretEncrypted;

  @Column(name = "valid_until")
  private LocalDateTime validUntil;

  private String comment;

}
