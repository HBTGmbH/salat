package org.tb.action.statusreport;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionForm;
import org.tb.GlobalConstants;
import org.tb.action.LoginRequiredAction;
import org.tb.bdom.Employee;
import org.tb.bdom.Statusreport;
import org.tb.persistence.StatusReportDAO;
import org.tb.util.OptionItem;

public abstract class StatusReportAction<F extends ActionForm> extends LoginRequiredAction<F> {

    protected List<OptionItem> getPhaseOptionList(HttpServletRequest request) {
        List<OptionItem> phaseList = new ArrayList<>();
        phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_ORGANISATION, getResources(request).getMessage(getLocale(request), "statusreport.phase.organisation.text")));
        phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_SPECIFICATIO, getResources(request).getMessage(getLocale(request), "statusreport.phase.specification.text")));
        phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_ANALYSIS, getResources(request).getMessage(getLocale(request), "statusreport.phase.analysis.text")));
        phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_REALIZATION, getResources(request).getMessage(getLocale(request), "statusreport.phase.realization.text")));
        phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_ACCEPTANCE, getResources(request).getMessage(getLocale(request), "statusreport.phase.acceptance.text")));
        phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_DELIVERY, getResources(request).getMessage(getLocale(request), "statusreport.phase.delivery.text")));
        phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_ROLLOUT, getResources(request).getMessage(getLocale(request), "statusreport.phase.rollout.text")));
        phaseList.add(new OptionItem(GlobalConstants.PHASE_ID_FINISH, getResources(request).getMessage(getLocale(request), "statusreport.phase.finish.text")));
        return phaseList;
    }

    protected List<OptionItem> getSortOptionList(HttpServletRequest request) {
        List<OptionItem> sortList = new ArrayList<>();
        sortList.add(new OptionItem(GlobalConstants.STATUSREPORT_SORT_PERIODICAL, getResources(request).getMessage(getLocale(request), "statusreport.sort.periodical")));
        sortList.add(new OptionItem(GlobalConstants.STATUSREPORT_SORT_EXTRA, getResources(request).getMessage(getLocale(request), "statusreport.sort.extra")));
        sortList.add(new OptionItem(GlobalConstants.STATUSREPORT_SORT_FINAL, getResources(request).getMessage(getLocale(request), "statusreport.sort.final")));
        return sortList;
    }

    protected boolean isReportEditable(Statusreport statusreport, HttpServletRequest request) {
        // admin may allways edit
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
            return true;
        }
        // released reports can't be edited
        return statusreport == null || statusreport.getReleased() == null;
        // otherwise the report can be edited
    }

    protected boolean isReportReadyForRelease(Long statusReportId, StatusReportDAO statusReportDAO, HttpServletRequest request) {
        if (statusReportId == null) {
            return false;
        }
        Statusreport statusreport = statusReportDAO.getStatusReportById(statusReportId);
        if (statusreport == null) {
            return false;
        }
        if (statusreport.getReleased() != null) {
            return false;
        }
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
            return true;
        }
        return loginEmployee.equals(statusreport.getSender());
    }

    protected boolean isReportReadyForAcceptance(Long statusReportId, StatusReportDAO statusReportDAO, HttpServletRequest request) {
        if (statusReportId == null) {
            return false;
        }
        Statusreport statusreport = statusReportDAO.getStatusReportById(statusReportId);
        if (statusreport == null) {
            return false;
        }
        if (statusreport.getReleased() == null) {
            return false;
        }
        if (statusreport.getAccepted() != null) {
            return false;
        }
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
            return true;
        }
        return loginEmployee.equals(statusreport.getRecipient());
    }

}
