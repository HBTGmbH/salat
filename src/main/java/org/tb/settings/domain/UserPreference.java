package org.tb.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "user_preference")
public class UserPreference extends AuditedEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "salat_user_id", nullable = false, unique = true)
  private SalatUser salatUser;

  @Column(nullable = false, columnDefinition = "TEXT")
  @Convert(converter = UserPreferenceConverter.class)
  private UserPreferenceMap settings = UserPreferenceMap.empty();

}
