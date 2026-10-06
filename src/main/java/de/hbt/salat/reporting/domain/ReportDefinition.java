package de.hbt.salat.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.common.domain.AuditedEntity;

@Entity
@Getter
@Setter
@Table(name = "report_definition",
    uniqueConstraints = @UniqueConstraint(name = "uk_report_definition_name", columnNames = "name"))
public class ReportDefinition extends AuditedEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Unique (#1333): the REST interface calls a report by its name. */
  private String name;
  @Column(name = "`sql`")
  private String sql;

  /**
   * The login that owns the report definition (#1330), set from the login it was created under. A
   * reference to master data of auth (#1370, ADR-0036): read only, no cascade. A people lead who is
   * not a manager may change or delete only what they own.
   *
   * <p>Not {@code createdby}: that is the login name, and a login name can be changed or
   * anonymized — the owner would lose the record, and whoever got the name next would inherit it
   * (the reason of #1204). {@code createdby} stays as the audit field it is. {@code null} when no
   * login could be assigned; then only a manager may change it.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_user_id")
  private SalatUser owner;

  /** The id of {@link #owner}, read off the reference without loading the login. */
  public Long getOwnerUserId() {
    return owner != null ? owner.getId() : null;
  }

}
