package de.hbt.salat.jira.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * One import of a ticket file into a scope (#1386): who imported which file when, how its columns were
 * read, and what came of it. The tickets it wrote point at it ({@code jira_ticket.import_id}).
 *
 * <p>The latest import of a scope has two further uses: its column reading is proposed again for the
 * next file, and its inherited additional fields hold for the tickets of the scope maintained by hand —
 * importing again without the mark switches an inheritance off.
 */
@Entity
@Table(name = "jira_ticket_import")
@Getter
@Setter
@NoArgsConstructor
public class JiraTicketImport extends AuditedEntity {

  /** Master data of the order module (ADR-0036): read only, no cascade. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customerorder_id", nullable = false,
      foreignKey = @ForeignKey(name = "fk_jira_ticket_import_customerorder"))
  private Customerorder customerorder;

  /** {@code null} for the whole order. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "suborder_id", foreignKey = @ForeignKey(name = "fk_jira_ticket_import_suborder"))
  private Suborder suborder;

  @Column(name = "file_name")
  private String fileName;

  /** One entry per column of the file, in its order. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "column_mapping")
  private List<JiraImportMappingEntry> columnMapping = new ArrayList<>();

  @Column(name = "created_count", nullable = false)
  private int createdCount;

  @Column(name = "updated_count", nullable = false)
  private int updatedCount;

  /** The id of {@link #customerorder}, read off the reference without loading the order. */
  public Long getCustomerorderId() {
    return customerorder != null ? customerorder.getId() : null;
  }

  /** The id of {@link #suborder}, {@code null} for the whole order; the suborder is not loaded. */
  public Long getSuborderId() {
    return suborder != null ? suborder.getId() : null;
  }

  /** The additional fields this import marked as inherited. */
  public Set<String> inheritedFields() {
    var fields = new LinkedHashSet<String>();
    columnMapping.stream()
        .filter(entry -> entry.target() == JiraImportTarget.ADDITIONAL && entry.inherited() && entry.fieldName() != null)
        .forEach(entry -> fields.add(entry.fieldName().trim()));
    return fields;
  }
}
