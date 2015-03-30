package org.tb.mobile;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Referenceday;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.ReferencedayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;

import com.google.gson.Gson;

public class StoreTimereportAction extends LoginRequiredAction {
    
    private SuborderDAO suborderDAO;
    private EmployeecontractDAO employeecontractDAO;
    private EmployeeorderDAO employeeorderDAO;
    private ReferencedayDAO referencedayDAO;
    private TimereportDAO timereportDAO;
    
    @Override
    protected ActionForward doSecureExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();
        boolean isValid = false;
        Timereport timereport = new Timereport();
        int hours = 0;
        int minutes = 0;
        Date date = new Date();
        java.sql.Date datesql = new java.sql.Date(date .getTime());
        Long selectedOrderId = Long.valueOf(request.getParameter("orderSelect"));
        String description = request.getParameter("comment");
        
        //Check for description existence and if existing  add a preceding space
        if(description == null){
            description ="Mobile booking";
        } else {
        	description = "Mobile booking " + description;
        }
        
        // Setting hours and minutes values
        try {
            hours = Integer.valueOf(request.getParameter("hours"));
        } catch (Exception e) {
            hours = 0;
        }

        try {
            minutes = Integer.valueOf(request.getParameter("minutes"));
        } catch (Exception e) {
            minutes = 0;
        }     
        
        Suborder suborder = suborderDAO.getSuborderById(selectedOrderId);
        Employeecontract employeecontract = new Employeecontract(); 
        
        //Checking if the timereport has to be updated or created 
        System.out.println(request.getParameter("hiddenTimereportId"));
        String timereportIdString  = request.getParameter("hiddenTimereportId");
        
        if (timereportIdString != "") {
            Long timereportId = Long.valueOf(timereportIdString);
            timereport = timereportDAO.getTimereportById(timereportId);
            employeecontract = timereport.getEmployeecontract();
            String lastupdatedby = timereport.getEmployeecontract().getEmployee().getSign();
            timereport.setLastupdate(date);
            timereport.setLastupdatedby(lastupdatedby);
        }
        else {
            Double costs = 0.0;
            Long employeecontractId = (Long) request.getSession().getAttribute("employeecontractId");
            employeecontract= employeecontractDAO.getEmployeeContractById(employeecontractId);            
            String createdby = employeecontract.getEmployee().getSign();
            Employeeorder employeeorder = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(employeecontractId, suborder.getId(), date);
            Referenceday referenceday = referencedayDAO.getReferencedayByDate(date);
            // Check if the reference day already exists and if not add a new one
            if (referenceday == null) {
                referencedayDAO.addReferenceday(datesql);
                referenceday = referencedayDAO.getReferencedayByDate(datesql);
            }
            
            
            timereport.setEmployeeorder(employeeorder);
            timereport.setEmployeecontract(employeecontract);
            timereport.setCreatedby(createdby);
            timereport.setCreated(date);
            timereport.setCosts(costs);
            timereport.setStatus("open");
            timereport.setSortofreport("W");
            timereport.setReferenceday(referenceday);
            timereport.setTraining(false);
        }

        timereport.setDurationhours(hours);
        timereport.setDurationminutes(minutes);
        timereport.setSuborder(suborder);         
        timereport.setTaskdescription(description);

        // Saving the report to DB
        try {
            timereportDAO.save(timereport, employeecontract.getEmployee(), true);
            isValid = true;
        } catch (Exception e) {
            isValid = false;
            e.printStackTrace();
        }
                
        String suborderLabel = suborder.getCustomerorder().getSign() + "/" + suborder.getSign() + " " + suborder.getShortdescription();
        map.put("isValid", isValid);
        map.put("hours", hours);
        map.put("minutes", minutes);
        map.put("suborder", suborderLabel);
        
        request.setAttribute("storeResult.json", new Gson().toJson(map));
        
        return mapping.findForward("success");
    }

    public SuborderDAO getSuborderDAO() {
        return suborderDAO;
    }

    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }

    public EmployeecontractDAO getEmployeecontractDAO() {
        return employeecontractDAO;
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    public EmployeeorderDAO getEmployeeorderDAO() {
        return employeeorderDAO;
    }

    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }

    public ReferencedayDAO getReferencedayDAO() {
        return referencedayDAO;
    }

    public void setReferencedayDAO(ReferencedayDAO referencedayDAO) {
        this.referencedayDAO = referencedayDAO;
    }

    public TimereportDAO getTimereportDAO() {
        return timereportDAO;
    }

    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }
    
}
