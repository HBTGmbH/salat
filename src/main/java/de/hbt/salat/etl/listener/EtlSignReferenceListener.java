package de.hbt.salat.etl.listener;

import static de.hbt.salat.common.exception.ServiceFeedbackMessage.info;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.event.SignsRenamedEvent;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.etl.service.ETLService;

/**
 * Nennt beim Umbenennen eines Auftrags oder Unterauftrags die ETL-Definitionen, die das alte Kürzel
 * noch enthalten (#1206). Ihr SQL wird nicht umgeschrieben; ETL-Definitionen werden von Hand gepflegt.
 */
@Component
@RequiredArgsConstructor
public class EtlSignReferenceListener {

  private final ETLService etlService;

  @EventListener
  public void onSignsRenamed(SignsRenamedEvent event) {
    var names = etlService.getNamesOfDefinitionsMentioning(event);
    if (!names.isEmpty()) {
      event.addNotice(info(ErrorCode.ETL_DEFINITIONS_NAME_OLD_SIGN, String.join(", ", names), event.getOldSign()));
    }
  }
}
