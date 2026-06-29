package org.tb.settings.domain;

import static org.tb.common.GlobalConstants.DEFAULT_WORK_DAY_START;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.tb.auth.domain.SalatUser;
import org.tb.common.domain.AuditedEntity;

@Entity
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class UserPreference extends AuditedEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "salat_user_id", nullable = false, unique = true)
  private SalatUser salatUser;

  @Column(nullable = false)
  private LocalTime workDayStart = LocalTime.of(DEFAULT_WORK_DAY_START, 0);

}
