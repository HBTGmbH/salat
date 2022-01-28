package org.tb.web.action;

import org.apache.struts.action.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tb.GlobalConstants;
import org.tb.bdom.*;
import org.tb.helper.AfterLogin;
import org.tb.persistence.*;
import org.tb.util.SecureHashUtils;
import org.tb.web.form.LoginEmployeeForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Action class for the login of an employee
 *
 * @author oda, th
 */
public class LoginEmployeeAction extends Action {
    private final static Logger LOG = LoggerFactory.getLogger(LoginEmployeeAction.class);

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
        LOG.trace("entering {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
        try {
            LoginEmployeeForm loginEmployeeForm = (LoginEmployeeForm) form;

            Employee loginEmployee = employeeDAO.getLoginEmployee(loginEmployeeForm.getLoginname(), SecureHashUtils.makeMD5(loginEmployeeForm.getPassword()));
            if (loginEmployee == null) {
                ActionMessages errors = getErrors(request);
                if (errors == null) {
                    errors = new ActionMessages();
                }
                errors.add(null, new ActionMessage("form.login.error.unknownuser"));

                saveErrors(request, errors);
                return mapping.getInputForward();
                //return mapping.findForward("error");
            }
            Date date = new Date();
            Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(loginEmployee.getId(), date);
            if (employeecontract == null && !loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
                ActionMessages errors = getErrors(request);
                if (errors == null) {
                    errors = new ActionMessages();
                }
                errors.add(null, new ActionMessage("form.login.error.invalidcontract"));

                saveErrors(request, errors);
                return mapping.getInputForward();
            }

            Map<String, Object> attributes = employeeDAO.getAttributes(request, loginEmployee);
            attributes.forEach((key, value) -> request.getSession().setAttribute(key, value));


            // not necessary at the moment
            //		if(employeeDAO.isAdmin(loginEmployee)) {
            //			request.getSession().setAttribute("admin", Boolean.TRUE);
            //		}

            // check if public holidays are available
            publicholidayDAO.checkPublicHolidaysForCurrentYear();

            // check if employee has an employee contract and is has employee orders for all standard suborders
            //		Date date = new Date();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
            String dateString2 = simpleDateFormat.format(date);
            date = simpleDateFormat.parse(dateString2);
            //		Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(loginEmployee.getId(), date);

            if (employeecontract != null) {
                request.getSession().setAttribute("employeeHasValidContract", true);

                // auto generate employee orders
                if (!loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM) &&
                        !employeecontract.getFreelancer()) {
                    List<Suborder> standardSuborders = suborderDAO.getStandardSuborders();
                    if (standardSuborders != null && standardSuborders.size() > 0) {
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
                                List<Employeeorder> invalidEmployeeorders = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(employeecontract.getId(), suborder.getId());
                                Date dateUntil = null;
                                Date dateFrom = null;
                                for (Employeeorder eo : invalidEmployeeorders) {

                                    // employeeorder starts in the future
                                    if (eo.getFromDate() != null && eo.getFromDate().after(date)) {
                                        if (dateUntil == null || dateUntil.after(eo.getFromDate())) {
                                            dateUntil = eo.getFromDate();
                                            continue;
                                        }
                                    }

                                    // employeeorder ends in the past
                                    if (eo.getUntilDate() != null && eo.getUntilDate().before(date)) {
                                        if (dateFrom == null || dateFrom.before(eo.getUntilDate())) {
                                            dateFrom = eo.getUntilDate();
                                        }
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
                                tmp.setSign("system");

                                if (untilDate == null || !fromDate.after(untilDate)) {
                                    employeeorderDAO.save(employeeorder, tmp);
                                }

                            }
                        }
                    }
                }
                if (employeecontract.getReportAcceptanceDate() == null) {
                    java.sql.Date validFromDate = employeecontract.getValidFrom();
                    employeecontract.setReportAcceptanceDate(validFromDate);
                    // create tmp employee
                    Employee tmp = new Employee();
                    tmp.setSign("system");
                    employeecontractDAO.save(employeecontract, tmp);
                }
                if (employeecontract.getReportReleaseDate() == null) {
                    java.sql.Date validFromDate = employeecontract.getValidFrom();
                    employeecontract.setReportReleaseDate(validFromDate);
                    // create tmp employee
                    Employee tmp = new Employee();
                    tmp.setSign("system");
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
                List<Warning> warnings = AfterLogin.createWarnings(employeecontract, loginEmployeeContract, employeeorderDAO, timereportDAO, statusReportDAO, customerorderDAO, getResources(request), getLocale(request));

                if (!warnings.isEmpty()) {
                    request.getSession().setAttribute("warnings", warnings);
                    request.getSession().setAttribute("warningsPresent", true);
                } else {
                    request.getSession().setAttribute("warningsPresent", false);
                }

            } else {
                request.getSession().setAttribute("employeeHasValidContract", false);
            }

            // show change password site, if password equals username
            if (loginEmployee.getPasswordchange()) {
                return mapping.findForward("password");
            }

            // create collection of employeecontracts
            List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsForEmployee(loginEmployee);
            request.getSession().setAttribute("employeecontracts", employeecontracts);

            return mapping.findForward("success");
        } finally {
            LOG.trace("leaving {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
        }
    }
}
