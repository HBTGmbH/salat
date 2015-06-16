package org.tb.web.action;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Statusreport;
import org.tb.bdom.Timereport;
import org.tb.bdom.Warning;
import org.tb.helper.StatusReportWarningHelper;
import org.tb.logging.TbLogger;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.StatusReportDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.web.form.ShowWelcomeForm;

public class ShowWelcomeAction extends DailyReportAction {
    
    private OvertimeDAO overtimeDAO;
    private TimereportDAO timereportDAO;
    private EmployeecontractDAO employeecontractDAO;
    private EmployeeorderDAO employeeorderDAO;
    private PublicholidayDAO publicholidayDAO;
    private CustomerorderDAO customerorderDAO;
    private StatusReportDAO statusReportDAO;
    
    public void setStatusReportDAO(StatusReportDAO statusReportDAO) {
        this.statusReportDAO = statusReportDAO;
    }
    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }
    public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
        this.publicholidayDAO = publicholidayDAO;
    }
    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }
    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }
    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }
    public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
        this.overtimeDAO = overtimeDAO;
    }
    
    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping,
            ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        
        ShowWelcomeForm welcomeForm = (ShowWelcomeForm)form;
        Employeecontract employeecontract;
        
        // create collection of employeecontracts
        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();
        request.getSession().setAttribute("employeecontracts", employeecontracts);
        
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("refresh")) {
            
            long employeeContractId = welcomeForm.getEmployeeContractId();
            
            employeecontract = employeecontractDAO.getEmployeeContractById(employeeContractId);
            request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
            request.getSession().setAttribute("currentEmployeeContract", employeecontract);
        } else {
            employeecontract = (Employeecontract)request.getSession().getAttribute("currentEmployeeContract");
            if (employeecontract == null) {
                employeecontract = (Employeecontract)request.getSession().getAttribute("loginEmployeeContract");
            }
            welcomeForm.setEmployeeContractId(employeecontract.getId());
            request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
            request.getSession().setAttribute("currentEmployeeContract", employeecontract);
        }
        
        refreshVacationAndOvertime(request, employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);
        
        // warnings
        List<Warning> warnings = new ArrayList<Warning>();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        
        // eoc warning
        List<Employeeorder> employeeorders = new ArrayList<Employeeorder>();
        employeeorders.addAll(employeeorderDAO.getEmployeeordersForEmployeeordercontentWarning(employeecontract));
        
        for (Employeeorder employeeorder : employeeorders) {
            if (!employeecontract.getFreelancer() && !employeeorder.getSuborder().getNoEmployeeOrderContent()) {
                try {
                    if (employeeorder.getEmployeeordercontent() == null) {
                        throw new RuntimeException("null content");
                    } else if (employeeorder.getEmployeeordercontent() != null && employeeorder.getEmployeeordercontent().getCommitted_emp() != true
                            && employeeorder.getEmployeecontract().getEmployee().equals(employeecontract.getEmployee())) {
                        Warning warning = new Warning();
                        warning.setSort(getResources(request).getMessage(getLocale(request), "employeeordercontent.thumbdown.text"));
                        warning.setText(employeeorder.getEmployeeOrderAsString());
                        warning.setLink("/tb/do/ShowEmployeeorder?employeeContractId=" + employeeorder.getEmployeecontract().getId());
                        warnings.add(warning);
                    } else if (employeeorder.getEmployeeordercontent() != null && employeeorder.getEmployeeordercontent().getCommitted_mgmt() != true
                            && employeeorder.getEmployeeordercontent().getContactTechHbt().equals(employeecontract.getEmployee())) {
                        Warning warning = new Warning();
                        warning.setSort(getResources(request).getMessage(getLocale(request), "employeeordercontent.thumbdown.text"));
                        warning.setText(employeeorder.getEmployeeOrderAsString());
                        warning.setLink("/tb/do/ShowEmployeeorder?employeeContractId=" + employeeorder.getEmployeecontract().getId());
                        warnings.add(warning);
                    } else {
                        throw new RuntimeException("query suboptimal");
                    }
                } catch (Exception e) {
                	TbLogger.error(this.getClass().getName(), e.getMessage());
                }
            }
        }
        
        // timereport warning
        List<Timereport> timereports = timereportDAO.getTimereportsOutOfRangeForEmployeeContract(employeecontract);
        for (Timereport timereport : timereports) {
            Warning warning = new Warning();
            warning.setSort(getResources(request).getMessage(getLocale(request), "main.info.warning.timereportnotinrange"));
            warning.setText(timereport.getTimeReportAsString());
            warnings.add(warning);
        }
        
        // timereport warning 2
        timereports = timereportDAO.getTimereportsOutOfRangeForEmployeeOrder(employeecontract);
        for (Timereport timereport : timereports) {
            Warning warning = new Warning();
            warning.setSort(getResources(request).getMessage(getLocale(request), "main.info.warning.timereportnotinrangeforeo"));
            warning.setText(timereport.getTimeReportAsString() + " " + timereport.getEmployeeorder().getEmployeeOrderAsString());
            warnings.add(warning);
        }
        
        // timereport warning 3: no duration
        Employeecontract loginEmployeeContract = (Employeecontract)request.getSession().getAttribute("loginEmployeeContract");
        timereports = timereportDAO.getTimereportsWithoutDurationForEmployeeContractId(employeecontract.getId(), employeecontract.getReportReleaseDate());
        for (Timereport timereport : timereports) {
            Warning warning = new Warning();
            warning.setSort(getResources(request).getMessage(getLocale(request), "main.info.warning.timereport.noduration"));
            warning.setText(timereport.getTimeReportAsString());
            if (loginEmployeeContract.equals(employeecontract)
                    || loginEmployeeContract.getEmployee().getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL)
                    || loginEmployeeContract.getEmployee().getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_PV)
                    || loginEmployeeContract.getEmployee().getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
                warning.setLink("/tb/do/EditDailyReport?trId=" + timereport.getId());
            }
            warnings.add(warning);
        }

        // statusreport due warning
        StatusReportWarningHelper.addWarnings(loginEmployeeContract, request, warnings, statusReportDAO, customerorderDAO);
        
        return mapping.findForward("success");
    }
    
}
