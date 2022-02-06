package org.tb.action.admin;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.*;
import org.tb.GlobalConstants;
import org.tb.bdom.*;
import org.tb.persistence.*;
import org.tb.util.DateUtils;
import org.tb.action.LoginRequiredAction;
import org.tb.form.AddCustomerOrderForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

/**
 * action class for storing a customer order permanently
 *
 * @author oda
 */
public class StoreCustomerorderAction extends LoginRequiredAction<AddCustomerOrderForm> {

    private CustomerDAO customerDAO;
    private SuborderDAO suborderDAO;
    private TimereportDAO timereportDAO;
    private CustomerorderDAO customerorderDAO;

    private EmployeeDAO employeeDAO;
    private EmployeeorderDAO employeeorderDAO;
    private EmployeecontractDAO employeecontractDAO;

    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }

    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }

    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }

    public void setCustomerDAO(CustomerDAO customerDAO) {
        this.customerDAO = customerDAO;
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }


    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddCustomerOrderForm coForm, HttpServletRequest request, HttpServletResponse response) throws IOException {
        SimpleDateFormat format = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);

        /* remove list with timereports out of range */
        request.getSession().removeAttribute("timereportsOutOfRange");

        // Task for setting the date, previous, next and to-day for both, until and from date
        if (request.getParameter("task") != null && request.getParameter("task").equals("setDate")) {
            String which = request.getParameter("which").toLowerCase();
            int howMuch = Integer.parseInt(request.getParameter("howMuch"));

            String datum = which.equals("until") ? coForm.getValidUntil() : coForm.getValidFrom();
            int day, month, year;
            Calendar cal = Calendar.getInstance();

            if (howMuch != 0) {
                ActionMessages errorMessages = valiDate(request, coForm, which);
                if (errorMessages.size() > 0) {
                    return mapping.getInputForward();
                }

                day = Integer.parseInt(datum.substring(8));
                month = Integer.parseInt(datum.substring(5, 7));
                year = Integer.parseInt(datum.substring(0, 4));

                cal.set(Calendar.DATE, day);
                cal.set(Calendar.MONTH, month - 1);
                cal.set(Calendar.YEAR, year);

                cal.add(Calendar.DATE, howMuch);
            }

            datum = howMuch == 0 ? format.format(new java.util.Date()) : format.format(cal.getTime());

            request.getSession().setAttribute(which.equals("until") ? "validUntil" : "validFrom", datum);

            if (which.equals("until")) {
                coForm.setValidUntil(datum);
            } else {
                coForm.setValidFrom(datum);
            }

            return mapping.findForward("reset");
        }

        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("save") ||
                request.getParameter("coId") != null) {

            ActionMessages errorMessages = validateFormData(request, coForm);
            if (errorMessages.size() > 0) {
                return mapping.getInputForward();
            }

            // 'main' task - prepare everything to store the customer.
            // I.e., copy properties from the form into the customerorder before saving.
            long coId = -1;
            Customerorder co = null;
            if (request.getSession().getAttribute("coId") != null) {
                // edited customerorder
                coId = Long.parseLong(request.getSession().getAttribute("coId").toString());
            }

            Date untilDate;
            if (coForm.getValidUntil() != null && !coForm.getValidUntil().trim().equals("")) {
                untilDate = Date.valueOf(coForm.getValidUntil());
            } else {
                untilDate = null;
            }
            Date fromDate = Date.valueOf(coForm.getValidFrom());

            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

            /* adjust suborders */
            List<Suborder> suborders = suborderDAO.getSubordersByCustomerorderId(coId, false);
            if (suborders != null && !suborders.isEmpty()) {
                for (Suborder so : suborders) {
                    boolean suborderchanged = false;
                    if (so.getFromDate().before(fromDate)) {
                        so.setFromDate(fromDate);
                        suborderchanged = true;
                    }
                    if (so.getUntilDate() != null && so.getUntilDate().before(fromDate)) {
                        so.setUntilDate(fromDate);
                        suborderchanged = true;
                    }
                    if (untilDate != null) {
                        if (so.getFromDate().after(untilDate)) {
                            so.setFromDate(untilDate);
                            suborderchanged = true;
                        }
                        if (so.getUntilDate() == null || so.getUntilDate().after(untilDate)) {
                            so.setUntilDate(untilDate);
                            suborderchanged = true;
                        }
                    }

                    if (suborderchanged) {

                        suborderDAO.save(so, loginEmployee);

                        // adjust employeeorders
                        List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrdersBySuborderId(so.getId());
                        if (employeeorders != null && !employeeorders.isEmpty()) {
                            for (Employeeorder employeeorder : employeeorders) {
                                boolean changed = false;
                                if (employeeorder.getFromDate().before(so.getFromDate())) {
                                    employeeorder.setFromDate(so.getFromDate());
                                    changed = true;
                                }
                                if (employeeorder.getUntilDate() != null && employeeorder.getUntilDate().before(so.getFromDate())) {
                                    employeeorder.setUntilDate(so.getFromDate());
                                    changed = true;
                                }
                                if (so.getUntilDate() != null) {
                                    if (employeeorder.getFromDate().after(so.getUntilDate())) {
                                        employeeorder.setFromDate(so.getUntilDate());
                                        changed = true;
                                    }
                                    if (employeeorder.getUntilDate() == null || employeeorder.getUntilDate().after(so.getUntilDate())) {
                                        employeeorder.setUntilDate(so.getUntilDate());
                                        changed = true;
                                    }
                                }
                                if (changed) {
                                    employeeorderDAO.save(employeeorder, loginEmployee);
                                }
                            }
                        }
                    }
                }
            }

            if (coId != 0 && coId != -1) {
                co = customerorderDAO.getCustomerorderById(coId);
            }
            if (co == null) {
                // new customer order
                co = new Customerorder();
            }

            /* set attributes */
            co.setCurrency(coForm.getCurrency());
            co.setCustomer(customerDAO.getCustomerById(coForm.getCustomerId()));

            co.setUntilDate(untilDate);
            co.setFromDate(fromDate);

            co.setSign(coForm.getSign());
            co.setDescription(coForm.getDescription());
            co.setShortdescription(coForm.getShortdescription());
            co.setHourly_rate(coForm.getHourlyRate());
            co.setOrder_customer(coForm.getOrderCustomer());

            co.setResponsible_customer_contractually(coForm.getResponsibleCustomerContractually());
            co.setResponsible_customer_technical(coForm.getResponsibleCustomerTechnical());
            co.setResponsible_hbt(employeeDAO.getEmployeeById(coForm.getEmployeeId()));
            co.setRespEmpHbtContract(employeeDAO.getEmployeeById(coForm.getRespContrEmployeeId()));

            if (coForm.getDebithours() == null || coForm.getDebithours() == 0.0) {
                co.setDebithours(null);
                co.setDebithoursunit(null);
            } else {
                co.setDebithours(coForm.getDebithours());
                co.setDebithoursunit(coForm.getDebithoursunit());
            }

            co.setStatusreport(coForm.getStatusreport());
            co.setHide(coForm.getHide());

            customerorderDAO.save(co, loginEmployee);

            request.getSession().setAttribute("customerorders", customerorderDAO.getCustomerorders());
            request.getSession().removeAttribute("coId");

            boolean addMoreOrders = Boolean.parseBoolean(request.getParameter("continue"));
            if (!addMoreOrders) {

                String filter = null;
                Boolean show = null;
                Long customerId = null;
                if (request.getSession().getAttribute("customerorderFilter") != null) {
                    filter = (String) request.getSession().getAttribute("customerorderFilter");
                }
                if (request.getSession().getAttribute("customerorderShow") != null) {
                    show = (Boolean) request.getSession().getAttribute("customerorderShow");
                }
                if (request.getSession().getAttribute("customerorderCustomerId") != null) {
                    customerId = (Long) request.getSession().getAttribute("customerorderCustomerId");
                }

                boolean showActualHours = (Boolean) request.getSession().getAttribute("showActualHours");
                if (showActualHours) {
                    /* show actual hours */
                    List<Customerorder> customerOrders = customerorderDAO.getCustomerordersByFilters(show, filter, customerId);
                    List<CustomerOrderViewDecorator> decorators = new LinkedList<>();
                    for (Customerorder customerorder : customerOrders) {
                        CustomerOrderViewDecorator decorator = new CustomerOrderViewDecorator(timereportDAO, customerorder);
                        decorators.add(decorator);
                    }
                    request.getSession().setAttribute("customerorders", decorators);
                } else {
                    request.getSession().setAttribute("customerorders", customerorderDAO.getCustomerordersByFilters(show, filter, customerId));

                }

                return mapping.findForward("success");
            } else {
                // reuse form entries and show add-page
                return mapping.findForward("reset");
            }
        }
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("back")) {
            // go back
            request.getSession().removeAttribute("coId");
            coForm.reset(mapping, request);
            return mapping.findForward("cancel");
        }
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("reset")) {
            // reset form
            doResetActions(mapping, request, coForm);
            return mapping.getInputForward();
        }

        return mapping.findForward("error");

    }

    /**
     * resets the 'add report' form to default values
     */
    private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddCustomerOrderForm coForm) {
        coForm.reset(mapping, request);
    }

    private ActionMessages valiDate(HttpServletRequest request, AddCustomerOrderForm coForm, String which) {
        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        String dateString = "";
        if (which.equals("from")) {
            dateString = coForm.getValidFrom().trim();
        } else {
            dateString = coForm.getValidUntil().trim();
        }

        int minus = 0;
        for (int i = 0; i < dateString.length(); i++) {
            if (dateString.charAt(i) == '-') {
                minus++;
            }
        }
        if (dateString.length() != 10 || minus != 2) {
            if (which.equals("from")) {
                errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
            } else {
                errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
            }
        }

        saveErrors(request, errors);
        return errors;
    }

    /**
     * validates the form data (syntax and logic)
     */
    private ActionMessages validateFormData(HttpServletRequest request, AddCustomerOrderForm coForm) throws IOException {

        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        //	check date formats (must now be 'yyyy-MM-dd')
        String dateFromString = coForm.getValidFrom().trim();
        boolean dateError = DateUtils.validateDate(dateFromString);
        if (dateError) {
            errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
        }

        if (coForm.getValidUntil() != null && !coForm.getValidUntil().trim().equals("")) {
            String dateUntilString = coForm.getValidUntil().trim();
            dateError = DateUtils.validateDate(dateUntilString);
            if (dateError) {
                errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
            }
        }

        Long coId = (Long) request.getSession().getAttribute("coId");
        // for a new customerorder, check if the sign already exists
        if (coId == null) {
            coId = 0L;
            List<Customerorder> allCustomerorders = customerorderDAO.getCustomerorders();
            for (Object element : allCustomerorders) {
                Customerorder co = (Customerorder) element;
                if (co.getSign().equalsIgnoreCase(coForm.getSign())) {
                    errors.add("sign", new ActionMessage("form.customerorder.error.sign.alreadyexists"));
                    break;
                }
            }
        }

        // check length of text fields and if they are filled
        if (coForm.getSign().length() > GlobalConstants.CUSTOMERORDER_SIGN_MAX_LENGTH) {
            errors.add("sign", new ActionMessage("form.customerorder.error.sign.toolong"));
        }
        if (coForm.getSign().length() <= 0) {
            errors.add("sign", new ActionMessage("form.customerorder.error.sign.required"));
        }
        if (coForm.getDescription().length() > GlobalConstants.CUSTOMERORDER_DESCRIPTION_MAX_LENGTH) {
            errors.add("description", new ActionMessage("form.customerorder.error.description.toolong"));
        }
        if ("".equals(coForm.getDescription().trim())) {
            errors.add("description", new ActionMessage("form.error.description.necessary"));
        }
        if (coForm.getShortdescription().length() > GlobalConstants.CUSTOMERORDER_SHORT_DESCRIPTION_MAX_LENGTH) {
            errors.add("shortdescription", new ActionMessage("form.customerorder.error.shortdescription.toolong"));
        }
        if (coForm.getOrderCustomer().length() > GlobalConstants.CUSTOMERORDER_ORDER_CUSTOMER_MAX_LENGTH) {
            errors.add("orderCustomer", new ActionMessage("form.customerorder.error.ordercustomer.toolong"));
        }
        if (coForm.getOrderCustomer().length() <= 0) {
            coForm.setOrderCustomer("-");
        }
        if (coForm.getResponsibleCustomerContractually().length() > GlobalConstants.CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH) {
            errors.add("responsibleCustomerContractually", new ActionMessage("form.customerorder.error.responsiblecustomer.toolong"));
        }
        if (coForm.getResponsibleCustomerContractually().length() <= 0) {
            errors.add("responsibleCustomerContractually", new ActionMessage("form.customerorder.error.responsiblecustomer.required"));
        }
        if (coForm.getResponsibleCustomerTechnical().length() > GlobalConstants.CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH) {
            errors.add("responsibleCustomerTechnical", new ActionMessage("form.customerorder.error.responsiblecustomer.toolong"));
        }
        if (coForm.getResponsibleCustomerTechnical().length() <= 0) {
            errors.add("responsibleCustomerTechnical", new ActionMessage("form.customerorder.error.responsiblecustomer.required"));
        }

        // check hourly rate format		
        if (!GenericValidator.isDouble(coForm.getHourlyRate().toString()) ||
                !GenericValidator.isInRange(coForm.getHourlyRate(),
                        0.0, GlobalConstants.MAX_HOURLY_RATE)) {
            errors.add("hourlyRate", new ActionMessage("form.customerorder.error.hourlyrate.wrongformat"));
        }

        if (!GenericValidator.isDouble(coForm.getDebithours().toString()) ||
                !GenericValidator.isInRange(coForm.getDebithours(),
                        0.0, GlobalConstants.MAX_DEBITHOURS)) {
            errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.wrongformat"));
        } else if (coForm.getDebithours() != null && coForm.getDebithours() != 0.0) {
            double debithours = coForm.getDebithours() * 100000;
            debithours += 0.5;

            int debithours2 = (int) debithours;
            int modulo = debithours2 % 5000;
            coForm.setDebithours(debithours2 / 100000.0);

            if (modulo != 0) {
                errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.wrongformat2"));
            }
        }

        if (coForm.getDebithours() != 0.0) {
            if (coForm.getDebithoursunit() == null || !(coForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_MONTH ||
                    coForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_YEAR || coForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)) {
                errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.nounit"));
            }
        }

        // check, if dates fit to existing timereports
        java.util.Date fromDate = DateUtils.parse(coForm.getValidFrom(), e -> {
            throw new RuntimeException(e);
        });
        java.util.Date untilDate = null;
        if(coForm.getValidUntil() != null && !coForm.getValidUntil().trim().isEmpty()) {
            untilDate = DateUtils.parse(coForm.getValidUntil(), e -> {
                throw new RuntimeException(e);
            });
        }

        List<Timereport> timereportsInvalidForDates = timereportDAO.getTimereportsByCustomerOrderIdInvalidForDates(fromDate, untilDate, coId);
        if (timereportsInvalidForDates != null && !timereportsInvalidForDates.isEmpty()) {
            request.getSession().setAttribute("timereportsOutOfRange", timereportsInvalidForDates);
            errors.add("timereportOutOfRange", new ActionMessage("form.general.error.timereportoutofrange"));
        }

        saveErrors(request, errors);

        return errors;
    }

    private void initializeMultipleEmployeeOrders(Employee loginEmployee, HttpServletRequest request, long cId, long sId) {
        List<Customerorder> customerOrders;
        if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL) || loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_PV)
                || loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
            customerOrders = customerorderDAO.getCustomerorders();
        } else {
            customerOrders = customerorderDAO.getCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
        }
        request.getSession().setAttribute("visibleCustomerOrders", customerOrders);
        List<Employeecontract> employeecontracts = employeecontractDAO.getValidEmployeeContractsOrderedByFirstname();
        request.getSession().setAttribute("employeecontracts", employeecontracts);
        List<Suborder> suborders = suborderDAO.getSubordersByCustomerorderId(cId, false);
        request.getSession().setAttribute("suborders", suborders);
        request.getSession().setAttribute("currentCustomer", cId);
        request.getSession().setAttribute("currentSuborder", sId);
        request.getSession().setAttribute("showAllSuborders", false);
    }
}
