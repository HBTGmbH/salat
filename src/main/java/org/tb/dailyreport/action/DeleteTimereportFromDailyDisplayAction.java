package org.tb.dailyreport.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.TimereportService;

/**
 * Action class for deletion of a timereport initiated from the daily display
 *
 * @author oda
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteTimereportFromDailyDisplayAction extends DailyReportAction<ShowDailyReportForm> {

  private final TimereportService timereportService;

  @Override
  public ActionForward executeAuthenticated(ActionMapping mapping, ShowDailyReportForm form, HttpServletRequest request,
      HttpServletResponse response) throws IOException {

    if (GenericValidator.isBlankOrNull(request.getParameter("trId")) || !GenericValidator.isLong(
        request.getParameter("trId"))) {
      return mapping.getInputForward();
    }

    long trId = Long.parseLong(request.getParameter("trId"));
    TimereportDTO tr = timereportService.getTimereportById(trId);
    if (tr == null) {
      return mapping.getInputForward();
    }

    timereportService.deleteTimereportById(trId);

    return mapping.findForward("success");

  }

  @Override
  protected boolean isAllowedForRestrictedUsers() {
    return true;
  }
}
