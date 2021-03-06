package org.tb.mobile;

import com.google.gson.Gson;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Timereport;
import org.tb.persistence.TimereportDAO;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

public class GetTimereportAction extends LoginRequiredAction {
    private TimereportDAO timereportDAO;

    @Override
    protected ActionForward doSecureExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        long timereportId = Long.parseLong(request.getParameter("timereportId"));

        Map<String, String> timereportMap = new HashMap<>();
        Timereport timereport = timereportDAO.getTimereportById(timereportId);

        //Creating a map with timereport details for the particular report
        String suborderId = String.valueOf(timereport.getSuborder().getId());
        String timereportComment = timereport.getTaskdescription();
        String timereportHours = String.valueOf(timereport.getDurationhours());
        String timereportMinutes = String.valueOf(timereport.getDurationminutes());
        timereportMap.put("suborderId", suborderId);
        timereportMap.put("timereportComment", timereportComment);
        timereportMap.put("timereportHours", timereportHours);
        timereportMap.put("timereportMinutes", timereportMinutes);

        request.setAttribute("timereport.json", new Gson().toJson(timereportMap));

        return mapping.findForward("success");
    }

    public TimereportDAO getTimereportDAO() {
        return timereportDAO;
    }

    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }


}
