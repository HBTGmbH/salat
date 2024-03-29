package org.tb.reporting.domain;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import org.tb.common.AuditedEntity;

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
