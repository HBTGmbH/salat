package org.tb.etl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.tb.common.domain.AuditedEntity;

@Entity
@Table(name = "etl_definition")
@Getter
@Setter
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ETLDefinition extends AuditedEntity implements Serializable {

  public enum ReferencePeriod {
    YEAR,
    QUARTER,
    MONTH,
    WEEK,
    DAY
  }

  private String name;
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "reference_period")
  private ReferencePeriod referencePeriod;

  private SqlStatements init;
  private SqlStatements execute;
  private SqlStatements cleanup;

}
