package org.tb.auth;

import static org.tb.common.GlobalConstants.SYSTEM_SIGN;
import static org.tb.common.util.DateUtils.formatYear;
import static org.tb.common.util.DateUtils.today;

import java.time.LocalDate;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.Warning;
import org.tb.common.struts.TypedAction;
import org.tb.common.util.SecureHashUtils;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.Employeeorder;
import org.tb.order.EmployeeorderDAO;
import org.tb.order.Suborder;
import org.tb.order.SuborderDAO;

/**
 * Action class for the login of an employee
 *
 * @author oda, th
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginEmployeeAction extends TypedAction<LoginEmployeeForm> {

    private final EmployeeDAO employeeDAO;
    private final PublicholidayDAO publicholidayDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final SuborderDAO suborderDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final AfterLogin afterLogin;
    private final AuthorizedUser authorizedUser;

    @Override
    public ActionForward executeWithForm(ActionMapping mapping, LoginEmployeeForm loginEmployeeForm, HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.trace("entering {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
        try {
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

            LocalDate today = today();
            Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(loginEmployee.getId(), today);
            if (employeecontract == null && !loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
                return loginFailed(request, "form.login.error.invalidcontract", mapping);
            }

            request.getSession().setAttribute("loginEmployee", loginEmployee);
            String loginEmployeeFullName = loginEmployee.getFirstname() + " " + loginEmployee.getLastname();
            request.getSession().setAttribute("loginEmployeeFullName", loginEmployeeFullName);
            request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());
            authorizedUser.init(loginEmployee);

            // check if public holidays are available
            publicholidayDAO.checkPublicHolidaysForCurrentYear();

            // check if employee has an employee contract and is has employee orders for all standard suborders
            if (employeecontract != null) {
                request.getSession().setAttribute("employeeHasValidContract", true);
                handleEmployeeWithValidContract(request, loginEmployee, today, employeecontract);
            } else {
                request.getSession().setAttribute("employeeHasValidContract", false);
            }

            // property passwordchange is set to true if password has been reset (username and password are equal)
            // in this case show the password change site
            if (Boolean.TRUE.equals(loginEmployee.getPasswordchange())) {
                return mapping.findForward("password");
            }

            // create collection of employeecontracts
            List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsForAuthorizedUser();
            request.getSession().setAttribute("employeecontracts", employeecontracts);

            return mapping.findForward("success");
        } finally {
            log.trace("leaving {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
        }
    }

    private void handleEmployeeWithValidContract(HttpServletRequest request, Employee loginEmployee, LocalDate today,
        Employeecontract employeecontract) {
        // auto generate employee orders
        if (!loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM) &&
            Boolean.FALSE.equals(employeecontract.getFreelancer())) {
            generateEmployeeOrders(today, employeecontract);
        }

        if (employeecontract.getReportAcceptanceDate() == null) {
            LocalDate validFromDate = employeecontract.getValidFrom();
            employeecontract.setReportAcceptanceDate(validFromDate);
            // create tmp employee
            Employee tmp = new Employee();
            tmp.setSign(SYSTEM_SIGN);
            employeecontractDAO.save(employeecontract, tmp);
        }
        if (employeecontract.getReportReleaseDate() == null) {
            LocalDate validFromDate = employeecontract.getValidFrom();
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

        afterLogin.handleOvertime(employeecontract, request.getSession());

        // get warnings
        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        List<Warning> warnings = afterLogin.createWarnings(employeecontract, loginEmployeeContract, getResources(
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

    private void generateEmployeeOrders(LocalDate today, Employeecontract employeecontract) {
        List<Suborder> standardSuborders = suborderDAO.getStandardSuborders();
        if (standardSuborders != null && !standardSuborders.isEmpty()) {
            // test if employeeorder exists
            for (Suborder suborder : standardSuborders) {
                List<Employeeorder> employeeorders = employeeorderDAO
                    .getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate3(
                        employeecontract.getId(), suborder
                            .getId(), today);
                if (employeeorders == null || employeeorders.isEmpty()) {

                    // do not create an employeeorder for past years "URLAUB" !
                    if (suborder.getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)
                        && !formatYear(today).startsWith(suborder.getSign())) {
                        continue;
                    }

                    // find latest untilLocalDate of all employeeorders for this suborder
                    List<Employeeorder> invalidEmployeeorders = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(
                        employeecontract.getId(), suborder.getId());
                    LocalDate dateUntil = null;
                    LocalDate dateFrom = null;
                    for (Employeeorder eo : invalidEmployeeorders) {

                        // employeeorder starts in the future
                        if (eo.getFromDate() != null && eo.getFromDate().isAfter(today)
                            && (dateUntil == null || dateUntil.isAfter(eo.getFromDate()))) {

                            dateUntil = eo.getFromDate();
                            continue;
                        }

                        // employeeorder ends in the past
                        if (eo.getUntilDate() != null && eo.getUntilDate().isBefore(today)
                            && (dateFrom == null || dateFrom.isBefore(eo.getUntilDate()))) {

                            dateFrom = eo.getUntilDate();
                        }
                    }

                    // calculate time period
                    LocalDate ecFromDate = employeecontract.getValidFrom();
                    LocalDate ecUntilDate = employeecontract.getValidUntil();
                    LocalDate soFromDate = suborder.getFromDate();
                    LocalDate soUntilDate = suborder.getUntilDate();
                    LocalDate fromDate = ecFromDate.isBefore(soFromDate) ? soFromDate : ecFromDate;

                    // fromLocalDate should not be before the ending of the most recent contract
                    if (dateFrom != null && dateFrom.isAfter(fromDate)) {
                        fromDate = dateFrom;
                    }
                    LocalDate untilDate = null;

                    if (ecUntilDate == null && soUntilDate == null) {
                        //untildate remains null
                    } else if (ecUntilDate == null) {
                        untilDate = soUntilDate;
                    } else if (soUntilDate == null) {
                        untilDate = ecUntilDate;
                    } else if (ecUntilDate.isBefore(soUntilDate)) {
                        untilDate = ecUntilDate;
                    } else {
                        untilDate = soUntilDate;
                    }

                    Employeeorder employeeorder = new Employeeorder();
                    employeeorder.setFromDate(fromDate);

                    // untilDate should not overreach a future employee contract
                    if (untilDate == null) {
                        untilDate = dateUntil;
                    } else {
                        if (dateUntil != null && dateUntil.isBefore(untilDate)) {
                            untilDate = dateUntil;
                        }
                    }

                    if (untilDate != null) {
                        employeeorder.setUntilDate(untilDate);
                    }
                    if (suborder.getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)
                        && !suborder.getSign().equalsIgnoreCase(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                        // TODO reduce VacationEntitlement if contract is not running the whole year
                        var vacationBudget = employeecontract.getDailyWorkingTime().multipliedBy(employeecontract.getVacationEntitlement());
                        employeeorder.setDebithours(vacationBudget);
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

                    if (untilDate == null || !fromDate.isAfter(untilDate)) {
                        employeeorderDAO.save(employeeorder, tmp);
                    }

                }
            }
        }
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
