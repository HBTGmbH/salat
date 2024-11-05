package org.tb.reporting.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.reporting.service.ReportingService;

@Component
@RequiredArgsConstructor
public class StoreReportAction extends LoginRequiredAction<CreateEditDeleteReportForm> {

    private final ReportingService reportingService;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping, CreateEditDeleteReportForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        switch(form.getMode()) {
            case "create" -> reportingService.create(form.getName(), form.getSql());
            case "edit" -> reportingService.update(form.getReportId(), form.getName(), form.getSql());
            default -> {} // do nothing
        }
        request.getSession().setAttribute("reportDescriptions", reportingService.getReportDefinitions());
        return mapping.findForward("success");
    }

}
