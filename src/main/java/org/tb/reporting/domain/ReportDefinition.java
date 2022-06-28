package org.tb.reporting.domain;

import java.io.Serializable;
import javax.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.tb.common.AuditedEntity;

@Entity
@Getter
@Setter
public class ReportDefinition extends AuditedEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  private String name;
  private String sql;

}
