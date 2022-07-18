package org.tb.reporting.action;

import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.AuthorizedUser;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.service.ReportingService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class EditReportAction extends LoginRequiredAction<CreateEditDeleteReportForm> {

    private final ReportingService reportingService;
    private final AuthorizedUser authorizedUser;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping, CreateEditDeleteReportForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        if("create".equals(request.getParameter("task"))) {
            form.setMode("create");
        } else if("edit".equals(request.getParameter("task"))) {
            form.setMode("edit");
        }

        switch(form.getMode()) {
            case "create" -> reportingService.create(authorizedUser, form.getName(), form.getSql());
            case "edit" -> reportingService.update(authorizedUser, form.getReportId(), form.getName(), form.getSql());
        }
        request.getSession().setAttribute("reportDescriptions", reportingService.getReportDefinitions(authorizedUser));
        return mapping.findForward("success");
    }

}
