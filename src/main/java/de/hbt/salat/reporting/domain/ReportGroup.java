package de.hbt.salat.reporting.domain;

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

/**
 * A group of report definitions (#1523), in place of the prefix that grouped them in their name.
 *
 * <p>Either a group managers maintain, which carries a {@link #name}, or the personal group of a people
 * lead, which carries its {@link #owner} and no name: it is shown with the name of the person, so that it
 * follows a change of the name instead of going stale with a copy of it. Each login has one personal
 * group at most.
 */
@Entity
@Getter
@Setter
@Table(name = "report_group", uniqueConstraints = {
    @UniqueConstraint(name = "uk_report_group_name", columnNames = "name"),
    @UniqueConstraint(name = "uk_report_group_owner_user_id", columnNames = "owner_user_id")})
public class ReportGroup extends AuditedEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  /** The name of a group managers maintain; {@code null} for a personal group. */
  private String name;

  /**
   * The login whose personal group this is, {@code null} for a group managers maintain. A reference to
   * master data of auth (ADR-0036): read only, no cascade.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_user_id")
  private SalatUser owner;

  public boolean isPersonal() {
    return owner != null;
  }

  /** The id of {@link #owner}, read off the reference without loading the login. */
  public Long getOwnerUserId() {
    return owner != null ? owner.getId() : null;
  }
}
