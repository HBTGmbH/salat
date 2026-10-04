package de.hbt.salat.reporting.listener;

import static de.hbt.salat.common.exception.ServiceFeedbackMessage.info;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.event.SignsRenamedEvent;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.reporting.service.ReportService;

/**
 * Tells the person renaming an order or a suborder which report definitions still name the old sign
 * (#1206). Their SQL is free text and is not rewritten.
 */
@Component
@RequiredArgsConstructor
public class ReportSignReferenceListener {

  private final ReportService reportService;

  @EventListener
  public void onSignsRenamed(SignsRenamedEvent event) {
    var names = reportService.getNamesOfDefinitionsMentioning(event);
    if (!names.isEmpty()) {
      event.addNotice(info(ErrorCode.RP_DEFINITIONS_NAME_OLD_SIGN, String.join(", ", names), event.getOldSign()));
    }
  }
}
