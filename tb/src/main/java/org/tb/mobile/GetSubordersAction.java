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
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.SuborderDAO;

import com.google.gson.Gson;

public class GetSubordersAction extends LoginRequiredAction {
    
    private SuborderDAO suborderDAO;
    private EmployeecontractDAO employeecontractDAO;
    
    @Override
    protected ActionForward doSecureExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        Date date = new Date();
        
        Long employeeId = (Long) request.getSession().getAttribute("employeeId");
        Employeecontract ec = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employeeId, date);
        Map<String, Object> subordersMap = new HashMap<String, Object>();
        //The method getSubordersByEmployeeContractIdWithValidEmployeeOrders was added to the SuborderDao class!!!
        ArrayList<Suborder> suborders = (ArrayList<Suborder>) suborderDAO.getSubordersByEmployeeContractIdWithValidEmployeeOrders(ec.getId(), date);

        for (int i = 0, l = suborders.size(); i < l; i++) {
            Suborder suborder = suborders.get(i);

            // Filtering valid suborders with not required description
            if (suborder.getCurrentlyValid()) {
                String suborderLabel = suborder.getCustomerorder().getSign() + "/" + suborder.getSign() + " " + suborder.getShortdescription();
                String suborderCommentRequired = String.valueOf(suborder.getCommentnecessary());
                List<String> suborderList = new ArrayList<String>();
                suborderList.add(suborderLabel);
                suborderList.add(suborderCommentRequired);
                subordersMap.put(String.valueOf(suborder.getId()),suborderList);
            }
        }
        
        request.setAttribute("suborders.json", new Gson().toJson(subordersMap));
        
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
    
}
