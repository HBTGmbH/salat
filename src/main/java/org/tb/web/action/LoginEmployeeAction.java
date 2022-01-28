package org.tb.web.action;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.Warning;
import org.tb.helper.AfterLogin;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.StatusReportDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.util.SecureHashUtils;
import org.tb.web.form.LoginEmployeeForm;

/**
 * Action class for the login of an employee
 *
 * @author oda, th
 */
@Slf4j
public class LoginEmployeeAction extends Action {
    private static final String SYSTEM_SIGN = "system";

    private EmployeeDAO employeeDAO;
    private PublicholidayDAO publicholidayDAO;
    private EmployeecontractDAO employeecontractDAO;
    private SuborderDAO suborderDAO;
    private EmployeeorderDAO employeeorderDAO;
    private OvertimeDAO overtimeDAO;
    private TimereportDAO timereportDAO;
    private CustomerorderDAO customerorderDAO;
    private StatusReportDAO statusReportDAO;

    public void setStatusReportDAO(StatusReportDAO statusReportDAO) {
        this.statusReportDAO = statusReportDAO;
    }

    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }

    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }

    public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
        this.overtimeDAO = overtimeDAO;
    }

    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }

    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
        this.publicholidayDAO = publicholidayDAO;
    }

    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.trace("entering {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
        try {
            LoginEmployeeForm loginEmployeeForm = (LoginEmployeeForm) form;

            Employee loginEmployee = employeeDAO.getLoginEmployee(loginEmployeeForm.getLoginname());
            boolean passwordMatches = loginEmployee != null && SecureHashUtils.passwordMatches(
                loginEmployeeForm.getPassword(),
                loginEmployee.getPassword()
            );
            if (!passwordMatches) {
                boolean legacyPasswordMatches = loginEmployee != null && SecureHashUtils.legacyPasswordMatches(
                    loginEmployeeForm.getPassword(), loginEmployee.getPassword()
                );
                if (legacyPasswordMatches) {
                    // employee still has old password form
                    // store password again with new hashing algorithm
                    Employee em = employeeDAO.getEmployeeById(loginEmployee.getId());
                    em.changePassword(loginEmployeeForm.getPassword());
                    loginEmployee.changePassword(loginEmployeeForm.getPassword());
                    employeeDAO.save(em, loginEmployee);
                } else {
                    return loginFailed(request, "form.login.error.unknownuser", mapping);
                }
            }

            // check if user is internal or extern
            setEmployeeIsInternalAttribute(request);

            Date date = new Date();
            Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(loginEmployee.getId(), date);
            if (employeecontract == null && !loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
                return loginFailed(request, "form.login.error.invalidcontract", mapping);
            }

            request.getSession().setAttribute("loginEmployee", loginEmployee);
            String loginEmployeeFullName = loginEmployee.getFirstname() + " " + loginEmployee.getLastname();
            request.getSession().setAttribute("loginEmployeeFullName", loginEmployeeFullName);
            request.getSession().setAttribute("report", "W");
            request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());
            request.getSession().setAttribute("employeeAuthorized", employeeHasAuthorization(loginEmployee));

            // check if public holidays are available
            publicholidayDAO.checkPublicHolidaysForCurrentYear();

            // check if employee has an employee contract and is has employee orders for all standard suborders
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
            String dateString = simpleDateFormat.format(date);
            date = simpleDateFormat.parse(dateString);

            if (employeecontract != null) {
                request.getSession().setAttribute("employeeHasValidContract", true);
                handleEmployeeWithValidContract(request, loginEmployee, date, employeecontract, dateString);
            } else {
                request.getSession().setAttribute("employeeHasValidContract", false);
            }
            
            // property passwordchange is set to true if password has been reset (username and password are equal)
            // in this case show the password change site
            if (Boolean.TRUE.equals(loginEmployee.getPasswordchange())) {
                return mapping.findForward("password");
            }

            // create collection of employeecontracts
            List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsForEmployee(loginEmployee);
            request.getSession().setAttribute("employeecontracts", employeecontracts);

            return mapping.findForward("success");
        } finally {
            log.trace("leaving {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
        }
    }

    private void handleEmployeeWithValidContract(HttpServletRequest request, Employee loginEmployee, Date date,
        Employeecontract employeecontract, String dateString) {
        // auto generate employee orders
        if (!loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM) &&
            Boolean.FALSE.equals(employeecontract.getFreelancer())) {
            generateEmployeeOrders(date, employeecontract, dateString);
        }

        if (employeecontract.getReportAcceptanceDate() == null) {
            java.sql.Date validFromDate = employeecontract.getValidFrom();
            employeecontract.setReportAcceptanceDate(validFromDate);
            // create tmp employee
            Employee tmp = new Employee();
            tmp.setSign(SYSTEM_SIGN);
            employeecontractDAO.save(employeecontract, tmp);
        }
        if (employeecontract.getReportReleaseDate() == null) {
            java.sql.Date validFromDate = employeecontract.getValidFrom();
            employeecontract.setReportReleaseDate(validFromDate);
            // create tmp employee
            Employee tmp = new Employee();
            tmp.setSign(SYSTEM_SIGN);
            employeecontractDAO.save(employeecontract, tmp);
        }
        // set used employee contract of login employee
        request.getSession().setAttribute("loginEmployeeContract", employeecontract);
        request.getSession().setAttribute("loginEmployeeContractId", employeecontract.getId());
        request.getSession().setAttribute("currentEmployeeContract", employeecontract);

        // get info about vacation, overtime and report status
        request.getSession().setAttribute("releaseWarning", employeecontract.getReleaseWarning());
        request.getSession().setAttribute("acceptanceWarning", employeecontract.getAcceptanceWarning());

        String releaseDate = employeecontract.getReportReleaseDateString();
        String acceptanceDate = employeecontract.getReportAcceptanceDateString();

        request.getSession().setAttribute("releasedUntil", releaseDate);
        request.getSession().setAttribute("acceptedUntil", acceptanceDate);

        AfterLogin.handleOvertime(employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO, request.getSession());

        // get warnings
        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        List<Warning> warnings = AfterLogin.createWarnings(employeecontract, loginEmployeeContract, employeeorderDAO, timereportDAO, statusReportDAO, customerorderDAO, getResources(
            request), getLocale(request));

        if (!warnings.isEmpty()) {
            request.getSession().setAttribute("warnings", warnings);
            request.getSession().setAttribute("warningsPresent", true);
        } else {
            request.getSession().setAttribute("warningsPresent", false);
        }
    }

    private void setEmployeeIsInternalAttribute(HttpServletRequest request) {
        String clientIP = request.getRemoteHost();
        boolean isInternal = clientIP.startsWith("10.") ||
            clientIP.startsWith("192.168.") ||
            clientIP.startsWith("172.16.") ||
            clientIP.startsWith("127.0.0.");
        request.getSession().setAttribute("clientIntern", isInternal);
    }

    private void generateEmployeeOrders(Date date, Employeecontract employeecontract, String dateString2) {
        List<Suborder> standardSuborders = suborderDAO.getStandardSuborders();
        if (standardSuborders != null && !standardSuborders.isEmpty()) {
            // test if employeeorder exists
            for (Suborder suborder : standardSuborders) {
                List<Employeeorder> employeeorders = employeeorderDAO
                        .getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate3(
                                employeecontract.getId(), suborder
                                        .getId(), date);
                if (employeeorders == null || employeeorders.isEmpty()) {

                    // do not create an employeeorder for past years "URLAUB" !
                    if (suborder.getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)
                            && !dateString2.startsWith(suborder.getSign())) {
                        continue;
                    }

                    // find latest untilDate of all employeeorders for this suborder
                    List<Employeeorder> invalidEmployeeorders = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(
                        employeecontract.getId(), suborder.getId());
                    Date dateUntil = null;
                    Date dateFrom = null;
                    for (Employeeorder eo : invalidEmployeeorders) {

                        // employeeorder starts in the future
                        if (eo.getFromDate() != null && eo.getFromDate().after(date) 
                            && (dateUntil == null || dateUntil.after(eo.getFromDate()))) {
                            
                            dateUntil = eo.getFromDate();
                            continue;
                        }

                        // employeeorder ends in the past
                        if (eo.getUntilDate() != null && eo.getUntilDate().before(date) 
                            && (dateFrom == null || dateFrom.before(eo.getUntilDate()))) {
                            
                            dateFrom = eo.getUntilDate();
                        }
                    }

                    // calculate time period
                    Date ecFromDate = employeecontract.getValidFrom();
                    Date ecUntilDate = employeecontract.getValidUntil();
                    Date soFromDate = suborder.getFromDate();
                    Date soUntilDate = suborder.getUntilDate();
                    Date fromDate = ecFromDate.before(soFromDate) ? soFromDate : ecFromDate;

                    // fromDate should not be before the ending of the most recent contract
                    if (dateFrom != null && dateFrom.after(fromDate)) {
                        fromDate = dateFrom;
                    }
                    Date untilDate = null;

                    if (ecUntilDate == null && soUntilDate == null) {
                        //untildate remains null
                    } else if (ecUntilDate == null) {
                        untilDate = soUntilDate;
                    } else if (soUntilDate == null) {
                        untilDate = ecUntilDate;
                    } else if (ecUntilDate.before(soUntilDate)) {
                        untilDate = ecUntilDate;
                    } else {
                        untilDate = soUntilDate;
                    }

                    Employeeorder employeeorder = new Employeeorder();

                    java.sql.Date sqlFromDate = new java.sql.Date(fromDate.getTime());
                    employeeorder.setFromDate(sqlFromDate);

                    // untilDate should not overreach a future employee contract
                    if (untilDate == null) {
                        untilDate = dateUntil;
                    } else {
                        if (dateUntil != null && dateUntil.before(untilDate)) {
                            untilDate = dateUntil;
                        }
                    }

                    if (untilDate != null) {
                        java.sql.Date sqlUntilDate = new java.sql.Date(untilDate.getTime());
                        employeeorder.setUntilDate(sqlUntilDate);
                    }
                    if (suborder.getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)
                            && !suborder.getSign().equalsIgnoreCase(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                        employeeorder.setDebithours(employeecontract
                                .getDailyWorkingTime()
                                * employeecontract
                                .getVacationEntitlement());
                        employeeorder.setDebithoursunit(GlobalConstants.DEBITHOURS_UNIT_TOTALTIME);
                    } else {
                        // not decided yet
                    }
                    employeeorder.setEmployeecontract(employeecontract);
                    employeeorder.setSign(" ");
                    employeeorder.setSuborder(suborder);

                    // create tmp employee
                    Employee tmp = new Employee();
                    tmp.setSign(SYSTEM_SIGN);

                    if (untilDate == null || !fromDate.after(untilDate)) {
                        employeeorderDAO.save(employeeorder, tmp);
                    }

                }
            }
        }
    }

    private boolean employeeHasAuthorization(Employee loginEmployee) {
        return loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_BL) ||
            loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_PV) ||
            loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM);
    }

    private ActionForward loginFailed(HttpServletRequest request, String key, ActionMapping mapping) {
        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }
        errors.add(null, new ActionMessage(key));

        saveErrors(request, errors);
        return mapping.getInputForward();
    }
}
