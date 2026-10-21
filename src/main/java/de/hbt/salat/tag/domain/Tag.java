package de.hbt.salat.tag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import de.hbt.salat.common.domain.AuditedEntity;

/**
 * A freely chosen keyword (#847, → ADR-0042), master data of the module {@code tag}.
 *
 * <p>A tag knows nothing of what carries it. Whoever tags something keeps the reference on its own side — a join
 * table with foreign keys into the owner's table and into {@code tag} — and so the module stays unchanged when a
 * further kind of object becomes taggable.
 *
 * <p>No setter: a tag is created with its name and colour and stays as it is. Renaming, merging and changing the
 * colour belong to the management of tags, a follow-up.
 */
@Entity
@Table(name = "tag", uniqueConstraints = @UniqueConstraint(name = "uk_tag_name_key", columnNames = "name_key"))
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag extends AuditedEntity {

  public static final int NAME_MAX_LENGTH = 100;

  @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
  private String name;

  /** {@link TagName#key()}; unique, so that {@code Festpreis} and {@code festpreis} stay one tag. */
  @Column(name = "name_key", nullable = false, length = NAME_MAX_LENGTH)
  private String nameKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "color", nullable = false, length = 16)
  private TagColor color;

  public Tag(TagName tagName) {
    this.name = tagName.name();
    this.nameKey = tagName.key();
    this.color = TagColor.of(tagName);
  }
}
