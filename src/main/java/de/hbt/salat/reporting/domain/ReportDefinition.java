package de.hbt.salat.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import de.hbt.salat.common.domain.AuditedEntity;

@Entity
@Getter
@Setter
@Table(name = "report_definition")
public class ReportDefinition extends AuditedEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  private String name;
  @Column(name = "`sql`")
  private String sql;

  /**
   * The login that owns the report definition (#1330): the id of a {@code SalatUser}, set from the login it was
   * created under. A people lead who is not a manager may change or delete only what they own.
   *
   * <p>Not {@code createdby}: that is the login name, and a login name can be changed or
   * anonymized — the owner would lose the record, and whoever got the name next would inherit it
   * (the reason of #1204). {@code createdby} stays as the audit field it is. {@code null} when no
   * login could be assigned; then only a manager may change it.
   */
  @Column(name = "owner_user_id")
  private Long ownerUserId;

}
