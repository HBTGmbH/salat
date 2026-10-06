package de.hbt.salat.etl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.HashSet;

import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import de.hbt.salat.common.domain.AuditedEntity;

/**
 * One ETL definition: three blocks of SQL run per reference period, after the definitions it
 * depends on.
 *
 * <p>The definitions are maintained in the database by hand; the application has no way to write
 * them. Since #1207 a dependency is a row in {@code etl_definition_dependency} naming both
 * definitions by id; since #1350 the entity holds it as a reference to the other definition — see {@code docs/etl-definitionen.md} for how to maintain them. The name is
 * unique and what people and the REST interface address a definition by, but nothing refers to it
 * any more: renaming a definition changes neither the order of a run nor whether it runs.
 */
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

  @Column(nullable = false, unique = true)
  private String name;
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "reference_period")
  private ReferencePeriod referencePeriod;

  private SqlStatements init;
  private SqlStatements execute;
  private SqlStatements cleanup;

  /**
   * The definitions this one depends on — they run before it (#1207, #1350). A database foreign key
   * makes sure each of them exists.
   *
   * <p>{@code LAZY} on purpose: the association points back at this entity, and {@code EAGER} would
   * pull in the whole dependency graph, recursively, wherever a single definition is loaded — the
   * selection list, the authorization check by name. The run needs the graph of all definitions and
   * gets it in one query with a fetch join ({@code ETLDefinitionRepository.findAllWithDependencies}).
   */
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "etl_definition_dependency",
      joinColumns = @JoinColumn(name = "etl_definition_id"),
      inverseJoinColumns = @JoinColumn(name = "depends_on_id"))
  private Set<ETLDefinition> dependencies = new HashSet<>();

}
