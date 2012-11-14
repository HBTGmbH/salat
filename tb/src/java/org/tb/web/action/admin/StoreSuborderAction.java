package org.tb.web.action.admin;

import java.sql.Date;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.SuborderViewDecorator;
import org.tb.bdom.Timereport;
import org.tb.logging.TbLogger;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddSuborderForm;

/**
 * action class for storing a suborder permanently
 * 
 * @author oda
 * 
 */
public class StoreSuborderAction extends LoginRequiredAction {
    
    private CustomerorderDAO customerorderDAO;
    
    private SuborderDAO suborderDAO;
    
    private TimereportDAO timereportDAO;
    
    private EmployeeorderDAO employeeorderDAO;
    
    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }
    
    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }
    
    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }
    
    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }
    
    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping,
            ActionForm form, HttpServletRequest request,
            HttpServletResponse response) {
        AddSuborderForm addSuborderForm = (AddSuborderForm)form;
        SimpleDateFormat format = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        
        // remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");
        
        // Task for setting the date, previous, next and to-day for both, until and from date
        if (request.getParameter("task") != null && request.getParameter("task").equals("setDate")) {
            String which = request.getParameter("which").toLowerCase();
            Integer howMuch = Integer.parseInt(request.getParameter("howMuch"));
            
            String datum = which.equals("until") ? addSuborderForm.getValidUntil() : addSuborderForm.getValidFrom();
            Integer day, month, year;
            Calendar cal = Calendar.getInstance();
            
            if (howMuch != 0) {
                ActionMessages errorMessages = valiDate(request, addSuborderForm, which);
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
                addSuborderForm.setValidUntil(datum);
            } else {
                addSuborderForm.setValidFrom(datum);
            }
            
            return mapping.findForward("reset");
        }
        
        if (request.getParameter("task") != null
                && request.getParameter("task").equals("copy")) {
            long soId = Long.parseLong(request.getSession().getAttribute("soId")
                    .toString());
            Suborder so = suborderDAO.getSuborderById(soId);
            
            if (so != null) {
                Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
                Suborder copy = so.copy(true, loginEmployee.getSign());
                
                suborderDAO.save(copy, loginEmployee);
                
                request.getSession().removeAttribute("soId");
                
                // store used customer order id for the next creation of a suborder
                request.getSession().setAttribute("lastCoId",
                        so.getCustomerorder().getId());
            }
            
            refreshForOverview(request);
            
            return mapping.findForward("success");
        }
        
        if (request.getParameter("task") != null
                && request.getParameter("task").equals("fitDates")) {
            if (request.getSession().getAttribute("soId") != null) {
                long soId = Long.parseLong(request.getSession().getAttribute("soId")
                        .toString());
                Suborder suborder = suborderDAO.getSuborderById(soId);
                if (suborder != null) {
                    Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
                    for (Suborder tempSuborder : suborder.getAllChildren()) {
                        tempSuborder.setFromDate(suborder.getFromDate());
                        tempSuborder.setUntilDate(suborder.getUntilDate());
                    }
                    suborderDAO.save(suborder, loginEmployee);
                    
                }
            }
            return mapping.findForward("success");
        }
        
        if (request.getParameter("task") != null && request.getParameter("task").equals("generateSign")) {
            //*** task for generating new suborder's sign
            Suborder tempSubOrder = suborderDAO.getSuborderById(addSuborderForm.getParentId());
            Customerorder tempOrder = customerorderDAO.getCustomerorderById(addSuborderForm.getParentId());
            List<Suborder> suborders = suborderDAO.getSuborders();
            TbLogger.debug(StoreSuborderAction.class.toString(),
                    " StoreSuborderAction.executeAuthenticated() - three Values: " + tempSubOrder + " / " + tempOrder + " / " + suborders);
            Long soId;
            try {
                soId = new Long(request.getSession().getAttribute("soId").toString());
            } catch (Throwable th) {
                soId = -1l;
            }
            if (suborders != null) {
                if (tempSubOrder != null) {
                    int version = 1;
                    DecimalFormat df = new DecimalFormat("00");
                    for (Suborder suborder : suborders) {
                        if (suborder.getParentorder() != null && suborder.getParentorder().getId() == tempSubOrder.getId()) {
                            if (suborder.getSign().equals(tempSubOrder.getSign() + "." + df.format(version)) && !soId.equals(suborder.getId())) {
                                version++;
                            }
                        }
                    }
                    addSuborderForm.setSign(tempSubOrder.getSign() + "." + df.format(version));
                } else if (tempOrder != null) {
                    int version = 1;
                    DecimalFormat df = new DecimalFormat("00");
                    for (Suborder suborder : suborders) {
                        if (suborder.getParentorder() == null && suborder.getCustomerorder().getId() == tempOrder.getId()) {
                            if (suborder.getSign().equals(tempOrder.getSign() + "." + df.format(version)) && !soId.equals(suborder.getId())) {
                                version++;
                            }
                        }
                    }
                    addSuborderForm.setSign(tempOrder.getSign() + "." + df.format(version));
                }
            }
            request.getSession().setAttribute("hourlyRate", addSuborderForm.getHourlyRate());
            request.getSession().setAttribute("currency", addSuborderForm.getCurrency());
            request.getSession().setAttribute("invoice", addSuborderForm.getInvoice());
            return mapping.getInputForward();
        }
        
        if (request.getParameter("task") != null
                && request.getParameter("task").equals("refreshParentProject")) {
            
            Customerorder parentOrder = customerorderDAO.getCustomerorderById(addSuborderForm.getCustomerorderId());
            
            addSuborderForm.setParentDescriptionAndSign(parentOrder.getSignAndDescription());
            addSuborderForm.setParentId(addSuborderForm.getCustomerorderId());
            
            request.getSession().setAttribute("parentDescriptionAndSign", addSuborderForm.getParentDescriptionAndSign());
            request.getSession().setAttribute("suborderParent", parentOrder);
            
            if (request.getParameter("continue") != null) {
                try {
                    addSuborderForm.setParentId(Long.parseLong(request.getParameter("continue")));
                    Suborder tempSubOrder = suborderDAO.getSuborderById(addSuborderForm.getParentId());
                    if (tempSubOrder != null) {
                        addSuborderForm.setParentDescriptionAndSign(tempSubOrder.getSignAndDescription());
                        
                        String parentOrderFromDate = "";
                        String parentOrderUntilDate = "";
                        if (tempSubOrder.getFromDate() != null) {
                            parentOrderFromDate = tempSubOrder.getFromDate().toString();
                        }
                        if (tempSubOrder.getUntilDate() != null) {
                            parentOrderUntilDate = tempSubOrder.getUntilDate().toString();
                        }
                        addSuborderForm.setValidFrom(parentOrderFromDate);
                        addSuborderForm.setValidUntil(parentOrderUntilDate);
                        
                        request.getSession().setAttribute("suborderParent", tempSubOrder);
                    } else {
                        Customerorder tempOrder = customerorderDAO.getCustomerorderById(addSuborderForm.getParentId());
                        addSuborderForm.setParentDescriptionAndSign(tempOrder.getSignAndDescription());
                        request.getSession().setAttribute("suborderParent", tempOrder);
                    }
                    request.getSession().setAttribute("parentDescriptionAndSign", addSuborderForm.getParentDescriptionAndSign());
                } catch (Throwable th) {
                    return mapping.findForward("error");
                }
            }
            
            return mapping.getInputForward();
        }
        
        if (request.getParameter("task") != null
                && request.getParameter("task").equals("refreshHourlyRate")) {
            //first refresh the treestructure-content
            Customerorder customerorder = customerorderDAO.getCustomerorderById(addSuborderForm.getCustomerorderId());
            addSuborderForm.setParentDescriptionAndSign(customerorder.getSignAndDescription());
            addSuborderForm.setParentId(addSuborderForm.getCustomerorderId());
            request.getSession().setAttribute("parentDescriptionAndSign", addSuborderForm.getParentDescriptionAndSign());
            request.getSession().setAttribute("suborderParent", customerorder);
            
            // refresh suborder default hourly rate after change of order
            // (same rate as for order itself)
            if (refreshHourlyRate(mapping, request, addSuborderForm) != true) {
                return mapping.findForward("error");
            } else {
                return mapping.getInputForward();
            }
        }
        
        if (request.getParameter("task") != null && request.getParameter("task").equals("save") || request.getParameter("soId") != null) {
            //*** task for saving new suborder
            ActionMessages errorMessages = validateFormData(request, addSuborderForm);
            if (errorMessages.size() > 0) {
                return mapping.getInputForward();
            }
            
            Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
            
            // 'main' task - prepare everything to store the suborder.
            // I.e., copy properties from the form into the suborder before
            // saving.
            long soId = -1;
            Suborder so = null;
            Customerorder customerorder = customerorderDAO.getCustomerorderById(addSuborderForm.getCustomerorderId());
            if (request.getSession().getAttribute("soId") != null) {
                // edited suborder
                soId = Long.parseLong(request.getSession().getAttribute("soId")
                        .toString());
                so = suborderDAO.getSuborderById(soId);
                
                if (so.getSuborders() != null
                        && !so.getSuborders().isEmpty()
                        && so.getCustomerorder().getId() != customerorder.getId()) {
                    // set customerorder in all descendants					
                    so.setCustomerOrderForAllDescendants(customerorder, suborderDAO, loginEmployee, so);
                }
                so = suborderDAO.getSuborderById(soId);
            } else {
                // new report
                so = new Suborder();
            }
            so.setCurrency(addSuborderForm.getCurrency());
            so.setCustomerorder(customerorder);
            so.setSign(addSuborderForm.getSign());
            so.setSuborder_customer(addSuborderForm.getSuborder_customer());
            so.setDescription(addSuborderForm.getDescription());
            so.setShortdescription(addSuborderForm.getShortdescription());
            so.setHourly_rate(addSuborderForm.getHourlyRate());
            so.setInvoice(addSuborderForm.getInvoice().charAt(0));
            so.setStandard(addSuborderForm.getStandard());
            so.setCommentnecessary(addSuborderForm.getCommentnecessary());
            so.setTrainingFlag(addSuborderForm.getTrainingFlag());
            
            if (addSuborderForm.getValidFrom() != null && !addSuborderForm.getValidFrom().trim().equals("")) {
                Date fromDate = Date.valueOf(addSuborderForm.getValidFrom());
                so.setFromDate(fromDate);
            } else {
                so.setFromDate(so.getCustomerorder().getFromDate());
            }
            if (addSuborderForm.getValidUntil() != null && !addSuborderForm.getValidUntil().trim().equals("")) {
                Date untilDate = Date.valueOf(addSuborderForm.getValidUntil());
                so.setUntilDate(untilDate);
            } else {
                so.setUntilDate(null);
            }
            
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
            
            if (addSuborderForm.getDebithours() == null || addSuborderForm.getDebithours() == 0.0) {
                so.setDebithours(null);
                so.setDebithoursunit(null);
            } else {
                so.setDebithours(addSuborderForm.getDebithours());
                so.setDebithoursunit(addSuborderForm.getDebithoursunit());
            }
            so.setHide(addSuborderForm.getHide());
            so.setNoEmployeeOrderContent(addSuborderForm.getNoEmployeeOrderContent());
            
            so.setParentorder(suborderDAO.getSuborderById(addSuborderForm.getParentId()));
            
            suborderDAO.save(so, loginEmployee);
            
            //			String filter = (String) request.getSession().getAttribute(
            //					"suborderFilter");
            //			if (filter != null && !filter.equalsIgnoreCase("")) {
            //				request.getSession().setAttribute("suborders",
            //						suborderDAO.getSubordersByFilter(filter));
            //			} else {
            //				request.getSession().setAttribute("suborders",
            //						suborderDAO.getSubordersOrderedByCustomerorder());
            //			}
            request.getSession().removeAttribute("soId");
            
            // store used customer order id for the next creation of a suborder
            request.getSession().setAttribute("lastCoId",
                    so.getCustomerorder().getId());
            
            boolean addMoreSuborders = Boolean.parseBoolean(request
                    .getParameter("continue"));
            if (!addMoreSuborders) {
                refreshForOverview(request);
                
                return mapping.findForward("success");
            } else {
                request.getSession().setAttribute("suborders", suborderDAO.getSuborders());
                // reuse form entries and show add-page
                addSuborderForm.setDescription("");
                addSuborderForm.setSign("");
                addSuborderForm.setSuborder_customer("");
                addSuborderForm.setInvoice("J");
                addSuborderForm.setCurrency(GlobalConstants.DEFAULT_CURRENCY);
                return mapping.findForward("reset");
            }
        }
        if (request.getParameter("task") != null
                && request.getParameter("task").equals("back")) {
            // go back
            request.getSession().removeAttribute("soId");
            addSuborderForm.reset(mapping, request);
            return mapping.findForward("cancel");
        }
        if (request.getParameter("task") != null
                && request.getParameter("task").equals("reset")) {
            // reset form
            doResetActions(mapping, request, addSuborderForm);
            return mapping.getInputForward();
        }
        
        return mapping.findForward("error");
        
    }
    
    /**
     * resets the 'add report' form to default values
     * 
     * @param mapping
     * @param request
     * @param reportForm
     */
    private void doResetActions(ActionMapping mapping,
            HttpServletRequest request, AddSuborderForm soForm) {
        soForm.reset(mapping, request);
    }
    
    private ActionMessages valiDate(HttpServletRequest request, AddSuborderForm soForm, String which) {
        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }
        
        String dateString = "";
        if (which.equals("from")) {
            dateString = soForm.getValidFrom().trim();
        } else {
            dateString = soForm.getValidUntil().trim();
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
     * 
     * @param request
     * @param cuForm
     * @return
     */
    private ActionMessages validateFormData(HttpServletRequest request, AddSuborderForm addSuborderForm) {
        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }
        Long suborderId;
        if (request.getSession().getAttribute("soId") != null) {
            // edited suborder
            suborderId = (Long)request.getSession().getAttribute("soId");
        } else {
            // new suborder
            suborderId = 0l;
        }
        
        // for a new suborder, check if the sign already exists
        if (suborderId == 0l) {
            // Liste aller Children der übergeordneten Suborder
            // ggf. gibt es keine übergeordnete Suborder (=null?)
            // dann die untergeordneten Suboders der Customerorder.
            List<Suborder> suborders;
            if (addSuborderForm.getParentId() == null) {
                suborders = suborderDAO.getSubordersByCustomerorderId(addSuborderForm.getCustomerorderId());
                for (Suborder suborder : suborders) {
                    if (suborder.getCurrentlyValid()
                            && suborder.getParentorder() == null // vergleiche nur Suborder direkt unter der Customerorder
                            && suborder.getSign().equalsIgnoreCase(addSuborderForm.getSign())) {
                        errors.add("sign", new ActionMessage("form.suborder.error.sign.alreadyexists"));
                        break;
                    }
                }
            } else {
                suborders = suborderDAO.getSuborderChildren(addSuborderForm.getParentId());
                for (Suborder suborder : suborders) {
                    if (suborder.getCurrentlyValid()
                            && suborder.getSign().equalsIgnoreCase(addSuborderForm.getSign())) {
                        errors.add("sign", new ActionMessage("form.suborder.error.sign.alreadyexists"));
                        break;
                    }
                }
            }
        }
        
        // check length of text fields
        if (addSuborderForm.getSign().length() > GlobalConstants.CUSTOMERORDER_SIGN_MAX_LENGTH) {
            errors.add("sign", new ActionMessage(
                    "form.suborder.error.sign.toolong"));
        }
        if (addSuborderForm.getSign().length() <= 0) {
            errors.add("sign", new ActionMessage(
                    "form.suborder.error.sign.required"));
        }
        if (addSuborderForm.getDescription().length() > GlobalConstants.CUSTOMERORDER_DESCRIPTION_MAX_LENGTH) {
            errors.add("description", new ActionMessage(
                    "form.suborder.error.description.toolong"));
        }
        if ("".equals(addSuborderForm.getDescription().trim())) {
            errors.add("description", new ActionMessage("form.error.description.necessary"));
        }
        if (addSuborderForm.getShortdescription().length() > GlobalConstants.CUSTOMERORDER_SHORT_DESCRIPTION_MAX_LENGTH) {
            errors.add("shortdescription", new ActionMessage(
                    "form.suborder.error.shortdescription.toolong"));
        }
        if (addSuborderForm.getCurrency().length() > GlobalConstants.CUSTOMERORDER_CURRENCY_MAX_LENGTH) {
            errors.add("currency", new ActionMessage(
                    "form.suborder.error.currency.toolong"));
        }
        if (addSuborderForm.getCurrency().length() <= 0) {
            errors.add("currency", new ActionMessage(
                    "form.suborder.error.currency.required"));
        }
        if (addSuborderForm.getSuborder_customer().length() > GlobalConstants.SUBORDER_SUBORDER_CUSTOMER_MAX_LENGTH) {
            errors.add("suborder_customer", new ActionMessage("form.suborder.error.suborder_customer.toolong"));
        }
        // check invoice character
        if (addSuborderForm.getInvoice().charAt(0) != GlobalConstants.SUBORDER_INVOICE_YES
                && addSuborderForm.getInvoice().charAt(0) != GlobalConstants.SUBORDER_INVOICE_NO
                && addSuborderForm.getInvoice().charAt(0) != GlobalConstants.SUBORDER_INVOICE_UNDEFINED) {
            errors.add("invoice", new ActionMessage("form.suborder.error.invoice.invalid"));
        }
        // check hourly rate format
        if (!GenericValidator.isDouble(addSuborderForm.getHourlyRate().toString())
                || !GenericValidator.isInRange(addSuborderForm.getHourlyRate(), 0.0, GlobalConstants.MAX_HOURLY_RATE)) {
            errors.add("hourlyRate", new ActionMessage("form.suborder.error.hourlyrate.wrongformat"));
        }
        // check date formats
        Date suborderFromDate = null;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        try {
            suborderFromDate = new Date(simpleDateFormat.parse(addSuborderForm.getValidFrom()).getTime());
        } catch (ParseException e) {
            errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
        }
        Date suborderUntilDate = null;
        if (addSuborderForm.getValidUntil() != null && !addSuborderForm.getValidUntil().trim().equals("")) {
            try {
                suborderUntilDate = new Date(simpleDateFormat.parse(addSuborderForm.getValidUntil()).getTime());
            } catch (ParseException e) {
                errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
            }
        }
        if (suborderFromDate != null && suborderUntilDate != null) {
            if (suborderUntilDate.before(suborderFromDate)) {
                errors.add("validUntil", new ActionMessage("form.suborder.error.date.untilbeforefrom"));
            }
        }
        // check debit hours
        if (!GenericValidator.isDouble(addSuborderForm.getDebithours().toString()) ||
                !GenericValidator.isInRange(addSuborderForm.getDebithours(),
                        0.0, GlobalConstants.MAX_DEBITHOURS)) {
            errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.wrongformat"));
        } else if (addSuborderForm.getDebithours() != null && addSuborderForm.getDebithours() != 0.0) {
            Double debithours = addSuborderForm.getDebithours() * 100000;
            debithours += 0.5;
            int debithours2 = debithours.intValue();
            int modulo = debithours2 % 5000;
            addSuborderForm.setDebithours(debithours2 / 100000.0);
            
            if (modulo != 0) {
                errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.wrongformat2"));
            }
        }
        
        if (addSuborderForm.getDebithours() != 0.0) {
            if (addSuborderForm.getDebithoursunit() == null || !(addSuborderForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_MONTH ||
                    addSuborderForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_YEAR || addSuborderForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)) {
                errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.nounit"));
            }
        }
        
        // check customer order
        Customerorder customerorder = customerorderDAO.getCustomerorderById(addSuborderForm.getCustomerorderId());
        if (customerorder == null) {
            errors.add("customerorder", new ActionMessage("form.suborder.error.customerorder.notfound"));
        } else {
            // check validity period
            Date coFromDate = customerorder.getFromDate();
            Date coUntilDate = customerorder.getUntilDate();
            if (suborderFromDate != null && coFromDate != null) {
                if (suborderFromDate.before(coFromDate)) {
                    errors.add("validFrom", new ActionMessage("form.suborder.error.date.outofrange.order"));
                }
                if (!(coUntilDate == null || suborderUntilDate != null && !suborderUntilDate.after(coUntilDate))) {
                    errors.add("validUntil", new ActionMessage("form.suborder.error.date.outofrange.order"));
                }
            }
        }
        
        // check time period for hierachical higher suborders
        Suborder parentSuborder = null;
        if (addSuborderForm.getParentId() != null && !addSuborderForm.getParentId().equals(0) && !addSuborderForm.getParentId().equals(-1)) {
            parentSuborder = suborderDAO.getSuborderById(addSuborderForm.getParentId());
            if (parentSuborder != null) {
                // check validity period
                Date parentFromDate = parentSuborder.getFromDate();
                Date parentUntilDate = parentSuborder.getUntilDate();
                if (suborderFromDate != null && parentFromDate != null) {
                    if (suborderFromDate.before(parentFromDate)) {
                        errors.add("validFrom", new ActionMessage("form.suborder.error.date.outofrange.suborder"));
                    }
                    if (!(parentUntilDate == null || suborderUntilDate != null && !suborderUntilDate.after(parentUntilDate))) {
                        errors.add("validUntil", new ActionMessage("form.suborder.error.date.outofrange.suborder"));
                    }
                }
            }
        }
        
        // check if billable suborder has assigned hourly rate
        if (addSuborderForm.getInvoice().equals(GlobalConstants.INVOICE_YES.toString()) && (addSuborderForm.getHourlyRate() == null || addSuborderForm.getHourlyRate() == 0.0)) {
            errors.add("hourlyRate", new ActionMessage("form.suborder.error.hourlyrate.unavailable"));
        }
        
        // check, if dates fit to existing timereports
        List<Timereport> timereportsInvalidForDates;
        if (suborderId != 0l) {
            Suborder suborder = suborderDAO.getSuborderById(suborderId);
            timereportsInvalidForDates = suborder.getAllTimeReportsInvalidForDates(suborderFromDate, suborderUntilDate, timereportDAO);
        } else {
            timereportsInvalidForDates = timereportDAO.getTimereportsBySuborderIdInvalidForDates(suborderFromDate, suborderUntilDate, suborderId);
        }
        if (timereportsInvalidForDates != null && !timereportsInvalidForDates.isEmpty()) {
            request.getSession().setAttribute("timereportsOutOfRange", timereportsInvalidForDates);
            errors.add("timereportOutOfRange", new ActionMessage("form.general.error.timereportoutofrange"));
        }
        
        saveErrors(request, errors);
        return errors;
    }
    
    /**
     * refreshes suborder default hourly rate after change of order (same rate
     * as for order itself)
     * 
     * @param mapping
     * @param request
     * @param soForm
     * @return
     */
    private boolean refreshHourlyRate(ActionMapping mapping,
            HttpServletRequest request, AddSuborderForm soForm) {
        
        Customerorder co = customerorderDAO.getCustomerorderById(soForm
                .getCustomerorderId());
        
        if (co != null) {
            request.getSession().setAttribute("currentOrderId",
                    new Long(co.getId()));
            request.getSession().setAttribute("currentOrder", co);
            request.getSession()
                    .setAttribute("hourlyRate", co.getHourly_rate());
            request.getSession().setAttribute("currency", co.getCurrency());
            soForm.setHourlyRate(co.getHourly_rate());
            soForm.setCurrency(co.getCurrency());
            
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
            soForm.setValidFrom(simpleDateFormat.format(co.getFromDate()));
            if (co.getUntilDate() != null) {
                soForm
                        .setValidUntil(simpleDateFormat.format(co
                                .getUntilDate()));
            } else {
                soForm.setValidUntil("");
            }
            soForm.setHide(co.getHide());
            
        } else {
            soForm.setHourlyRate(0.0);
            soForm.setCurrency(GlobalConstants.DEFAULT_CURRENCY);
        }
        
        return true;
        
    }
    
    private void refreshForOverview(HttpServletRequest request) {
        String filter = null;
        Boolean show = null;
        Long customerOrderId = null;
        if (request.getSession().getAttribute("suborderFilter") != null) {
            filter = (String)request.getSession().getAttribute("suborderFilter");
        }
        if (request.getSession().getAttribute("suborderShow") != null) {
            show = (Boolean)request.getSession().getAttribute("suborderShow");
        }
        if (request.getSession().getAttribute("suborderCustomerOrderId") != null) {
            customerOrderId = (Long)request.getSession().getAttribute("suborderCustomerOrderId");
        }
        
        boolean showActualHours = (Boolean)request.getSession().getAttribute("showActualHours");
        if (showActualHours) {
            /* show actual hours */
            List<Suborder> suborders = suborderDAO.getSubordersByFilters(show, filter, customerOrderId);
            List<SuborderViewDecorator> suborderViewDecorators = new LinkedList<SuborderViewDecorator>();
            for (Suborder suborder : suborders) {
                SuborderViewDecorator decorator = new SuborderViewDecorator(timereportDAO, suborder);
                suborderViewDecorators.add(decorator);
            }
            request.getSession().setAttribute("suborders", suborderViewDecorators);
        } else {
            request.getSession().setAttribute("suborders", suborderDAO.getSubordersByFilters(show, filter, customerOrderId));
        }
    }
}
