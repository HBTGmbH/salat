package org.tb.web.action.admin;

import java.text.SimpleDateFormat;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Suborder;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddSuborderForm;

/**
 * action class for editing a suborder
 * 
 * @author oda
 *
 */
public class EditSuborderAction extends LoginRequiredAction {
    
    private SuborderDAO suborderDAO;
    private CustomerorderDAO customerorderDAO;
    
    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }
    
    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }
    
    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        
        //		 remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");
        
        AddSuborderForm soForm = (AddSuborderForm)form;
        long soId = Long.parseLong(request.getParameter("soId"));
        Suborder so = suborderDAO.getSuborderById(soId);
        request.getSession().setAttribute("soId", so.getId());
        
        // fill the form with properties of suborder to be edited
        setFormEntries(mapping, request, soForm, so);
        
        // make sure all customer orders are available in form
        Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
        List<Customerorder> customerorders;
        if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL) ||
                loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_PV) ||
                loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
            customerorders = customerorderDAO.getVisibleCustomerorders();
        } else {
            customerorders = customerorderDAO.getVisibleCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
        }
        request.getSession().setAttribute("customerorders", customerorders);
        
        // forward to suborder add/edit form
        return mapping.findForward("success");
    }
    
    /**
     * fills suborder form with properties of given suborder
     * 
     * @param mapping
     * @param request
     * @param soForm
     * @param so - the suborder
     */
    private void setFormEntries(ActionMapping mapping, HttpServletRequest request,
            AddSuborderForm soForm, Suborder so) {
        soForm.setCurrency(so.getCurrency());
        soForm.setCustomerorderId(so.getCustomerorder().getId());
        soForm.setHourlyRate(so.getHourly_rate());
        soForm.setSign(so.getSign());
        soForm.setDescription(so.getDescription());
        soForm.setShortdescription(so.getShortdescription());
        soForm.setInvoice(Character.toString(so.getInvoice()));
        soForm.setStandard(so.getStandard());
        soForm.setCommentnecessary(so.getCommentnecessary());
        soForm.setTrainingFlag(so.getTrainingFlag());
        soForm.setFixedPrice(so.getFixedPrice());
        soForm.setSuborder_customer(so.getSuborder_customer());
        if (so.getParentorder() != null) {
            soForm.setParentId(so.getParentorder().getId());
        } else {
            soForm.setParentId(so.getCustomerorder().getId());
        }
        try {
            Suborder tempSubOrder = suborderDAO.getSuborderById(soForm.getParentId());
            if (tempSubOrder != null) {
                soForm.setParentDescriptionAndSign(tempSubOrder.getSignAndDescription());
                request.getSession().setAttribute("suborderParent", tempSubOrder);
            } else {
                Customerorder tempOrder = customerorderDAO.getCustomerorderById(soForm.getParentId());
                soForm.setParentDescriptionAndSign(tempOrder.getSignAndDescription());
                request.getSession().setAttribute("suborderParent", tempOrder);
            }
            request.getSession().setAttribute("parentDescriptionAndSign", soForm.getParentDescriptionAndSign());
        } catch (Throwable th) {}
        
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        soForm.setValidFrom(simpleDateFormat.format(so.getFromDate()));
        if (so.getUntilDate() != null) {
            soForm.setValidUntil(simpleDateFormat.format(so.getUntilDate()));
        } else {
            soForm.setValidUntil("");
        }
        
        if (so.getDebithours() != null) {
            soForm.setDebithours(so.getDebithours());
            soForm.setDebithoursunit(so.getDebithoursunit());
        } else {
            soForm.setDebithours(null);
            soForm.setDebithoursunit(null);
        }
        soForm.setHide(so.getHide());
        soForm.setNoEmployeeOrderContent(so.getNoEmployeeOrderContent());
        
        //request.getSession().setAttribute("currentSuborderID", new Long(so.getId()));
        request.getSession().setAttribute("currentOrderId", new Long(so.getCustomerorder().getId()));
        request.getSession().setAttribute("currentOrder", so.getCustomerorder());
        request.getSession().setAttribute("invoice", Character.toString(so.getInvoice()));
        request.getSession().setAttribute("currency", so.getCurrency());
        request.getSession().setAttribute("hourlyRate", so.getHourly_rate());
    }
    
}
