package org.tb.mobile;

import com.google.gson.Gson;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.*;
import org.tb.persistence.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class StoreTimereportAction extends LoginRequiredAction {

    private SuborderDAO suborderDAO;
    private EmployeecontractDAO employeecontractDAO;
    private EmployeeorderDAO employeeorderDAO;
    private ReferencedayDAO referencedayDAO;
    private TimereportDAO timereportDAO;

    @Override
    protected ActionForward doSecureExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> map = new HashMap<>();
        boolean isValid;
        Timereport timereport = new Timereport();
        int hours;
        int minutes;
        Date date = new Date();
        java.sql.Date datesql = new java.sql.Date(date.getTime());
        long selectedOrderId = Long.parseLong(request.getParameter("orderSelect"));
        String description = request.getParameter("comment");

        //Check for description existence and if existing  add a preceding space
        // for tests
//        if(description == null){
//            description ="Mobile booking";
//        } else {
//        	description = "Mobile booking " + description;
//        }

        // Setting hours and minutes values
        try {
            hours = Integer.parseInt(request.getParameter("hours"));
        } catch (Exception e) {
            hours = 0;
        }

        try {
            minutes = Integer.parseInt(request.getParameter("minutes"));
        } catch (Exception e) {
            minutes = 0;
        }

        Suborder suborder = suborderDAO.getSuborderById(selectedOrderId);
        Employeecontract employeecontract;

        //Checking if the timereport has to be updated or created 
        String timereportIdString = request.getParameter("hiddenTimereportId");

        if (!timereportIdString.isEmpty()) {
            long timereportId = Long.parseLong(timereportIdString);
            timereport = timereportDAO.getTimereportById(timereportId);
            employeecontract = timereport.getEmployeecontract();
            String lastupdatedby = timereport.getEmployeecontract().getEmployee().getSign();
            timereport.setLastupdate(date);
            timereport.setLastupdatedby(lastupdatedby);
        } else {
            Double costs = 0.0;
            Long employeecontractId = (Long) request.getSession().getAttribute("employeecontractId");
            employeecontract = employeecontractDAO.getEmployeeContractById(employeecontractId);
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
