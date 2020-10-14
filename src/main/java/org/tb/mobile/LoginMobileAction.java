package org.tb.mobile;

import com.google.gson.Gson;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employee;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.util.SecureHashUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LoginMobileAction extends Action {
    private EmployeecontractDAO employeecontractDAO;
    private EmployeeDAO employeeDAO;


    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        Map<String, Object> map = new HashMap<String, Object>();
        boolean isValid = false;
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        Employee employee = employeeDAO.getLoginEmployee(username, SecureHashUtils.makeMD5(password));

        if (employee != null) {
            Long employeeId = employee.getId();
            isValid = true;
            Date date = new Date();
            Long employeecontractId = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employeeId, date).getId();
            request.getSession().setAttribute("employeeId", employeeId);
            request.getSession().setAttribute("employeecontractId", employeecontractId);
        }

        map.put("isValid", isValid);

        request.setAttribute("loginMobile.json", new Gson().toJson(map));
        return mapping.findForward("success");
    }

    public EmployeecontractDAO getEmployeecontractDAO() {
        return employeecontractDAO;
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    public EmployeeDAO getEmployeeDAO() {
        return employeeDAO;
    }

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }


}
