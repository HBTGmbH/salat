package de.hbt.salat.etl.configuration;

import static de.hbt.salat.common.exception.ErrorCode.ETL_DISABLED;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.exception.BusinessRuleException;

/**
 * Ob in dieser Umgebung ETL-Läufe stattfinden ({@code salat.etl.enabled}, #1458).
 *
 * <p>Eine Umgebung mit einer Kopie der Produktionsdaten, etwa eine QA-Stufe, soll die Datenbank
 * nicht mit Läufen belasten. Gefragt wird an der Tür jedes Laufs, in {@code ETLService}, und nicht
 * nur in den Controllern: so bleibt auch ein künftiger Einstieg zu. Der nächtliche Lauf fragt vorher
 * selbst, damit er ohne Fehler und ohne Zeile in der Liste entfällt.
 *
 * <p>Die Aufräumjobs fragen nicht: sie lesen kein Fremdsystem und belasten die Datenbank kaum.
 */
@Slf4j
@Component
public class EtlSwitch {

  private final SalatProperties properties;

  public EtlSwitch(SalatProperties properties) {
    this.properties = properties;
    if (!isEnabled()) {
      log.info("ETL runs are switched off in this environment (salat.etl.enabled=false)");
    }
  }

  public boolean isEnabled() {
    return properties.getEtl().isEnabled();
  }

  /** @throws BusinessRuleException {@code ETL-0010}, wenn ETL in dieser Umgebung abgeschaltet ist */
  public void requireEnabled() {
    if (!isEnabled()) {
      throw new BusinessRuleException(ETL_DISABLED);
    }
  }

}
