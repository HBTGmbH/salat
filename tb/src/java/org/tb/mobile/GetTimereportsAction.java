package org.tb.mobile;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.persistence.TimereportDAO;

import com.google.gson.Gson;

public class GetTimereportsAction extends LoginRequiredAction {
    
    private TimereportDAO timereportDAO;
    
    
    @Override
    protected ActionForward doSecureExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        Date date = new Date();
        java.sql.Date datesql = new java.sql.Date(date .getTime());
        
        Long employeecontractId = (Long) request.getSession().getAttribute("employeecontractId");
        
        Map<String, Object> timereportsMap = new HashMap<String, Object>();
        //The method getSubordersByEmployeeContractIdWithValidEmployeeOrders was added to the SuborderDao class!!!
        ArrayList<Timereport> timereports = (ArrayList<Timereport>) timereportDAO.getTimereportsByDateAndEmployeeContractId(employeecontractId, datesql);
        Timereport timereport = new Timereport();
        Suborder suborder = new Suborder();
        
        //Creating a map with timereportId as a key and a list of timereport details as a value
        for (int i = 0, l = timereports.size(); i < l; i++) {
            timereport = timereports.get(i);
            List<String> timereportDetailsList = new ArrayList<String>();
            suborder =  timereport.getSuborder();
            String timereportLabel = suborder.getCustomerorder().getSign() +"/"+ suborder.getShortdescription();
            String timereportHours  = String.valueOf(timereport.getDurationhours());
            String timereportMinutes  = String.valueOf(timereport.getDurationminutes());
            timereportDetailsList.add(timereportLabel);
            timereportDetailsList.add(timereportHours);
            timereportDetailsList.add(timereportMinutes);
            timereportsMap.put(String.valueOf(timereport.getId()), timereportDetailsList);
        }
        
        request.setAttribute("timereports.json", new Gson().toJson(timereportsMap));
        
        return mapping.findForward("success");
    }


    public TimereportDAO getTimereportDAO() {
        return timereportDAO;
    }


    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }

    

}
