package de.hbt.salat.palette.domain;

import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * How often a person ran an entry of the command palette on one day (#1501), for the achievement
 * „Palette Junkie“. Only the number: neither the command nor the time of day.
 */
@Entity
@Table(name = "palette_use", uniqueConstraints = @UniqueConstraint(name = "uk_palette_use_salat_user_id_use_date",
    columnNames = {"salat_user_id", "use_date"}))
@Getter
@Setter
@NoArgsConstructor
public class PaletteUse extends AuditedEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "salat_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_palette_use_salat_user"))
  private SalatUser salatUser;

  @Column(name = "use_date", nullable = false)
  private LocalDate useDate;

  @Column(name = "use_count", nullable = false)
  private int useCount;
}
