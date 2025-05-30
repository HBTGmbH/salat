package org.tb.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import org.tb.common.domain.AuditedEntity;

@Entity
@Getter
@Setter
@Table(name = "report_definition")
public class ReportDefinition extends AuditedEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  private String name;
  @Column(name = "`sql`")
  private String sql;

}
