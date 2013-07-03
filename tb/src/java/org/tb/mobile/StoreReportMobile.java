package org.tb.mobile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
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

/**
 * 
 * Servlet for managing requests from the mobile booking page (addDailyReportMobile).
 *
 */


public class StoreReportMobile extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private ApplicationContext appContext;
    private EmployeecontractDAO ecDao;
    private SuborderDAO soDao;
    private TimereportDAO trDao;
    private EmployeeorderDAO eoDao;
    private ReferencedayDAO rdDao;
    private Date date;
    private java.sql.Date datesql;

    public StoreReportMobile() {
        super();
    }

    /**
     * Creates required DAOs on servlet initialisation.
     * 
     * {@inheritDoc}
     */
    @Override
    public void init() throws ServletException {
        appContext = (ApplicationContext) getServletContext().getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
        ecDao = (EmployeecontractDAO) appContext.getBean("employeecontractDAO");
        soDao = (SuborderDAO) appContext.getBean("suborderDAO");
        trDao = (TimereportDAO) appContext.getBean("timereportDAO");
        eoDao = (EmployeeorderDAO) appContext.getBean("employeeorderDAO");
        rdDao = (ReferencedayDAO) appContext.getBean("referencedayDAO");
        date = new Date();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy() {
        appContext = null;
        ecDao = null;
        soDao = null;
        trDao = null;
        eoDao = null;
        rdDao = null;
        date = null;
        super.destroy();
    }

    /**
     * Manages GET-requests from the addReportMobile page and sends back a map
     * of currently valid suborders.
     * 
     * @param request  the Http request
     * @param response the Http response object to write to
     * 
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        Long employeeId = (Long) request.getSession().getAttribute("employeeId");
        Employeecontract ec = ecDao.getEmployeeContractByEmployeeIdAndDate(employeeId, date);
        Map<String, Object> subordersMap = new HashMap<String, Object>();
        //The method getSubordersByEmployeeContractIdWithValidEmployeeOrders was added to the SuborderDao class!!!
        ArrayList<Suborder> suborders = (ArrayList<Suborder>) soDao.getSubordersByEmployeeContractIdWithValidEmployeeOrders(ec.getId(), date);

        for (int i = 0, l = suborders.size(); i < l; i++) {
            Suborder sub = suborders.get(i);

            // Filtering valid suborders with not required description
            if (sub.getCurrentlyValid()) {
                String subLabel = sub.getCustomerorder().getSign() + "/" + sub.getSign() + " " + sub.getShortdescription();
                String subCommentRequired = String.valueOf(sub.getCommentnecessary());
                List<String> subList = new ArrayList<String>();
                subList.add(subLabel);
                subList.add(subCommentRequired);
                subordersMap.put(String.valueOf(sub.getId()),subList);
            }
        }
        write(response, subordersMap);
    }

    /**
     * Writes the response with a map data in json format.
     * 
     * @param response the Http response object to write to
     * @param map data that is encoded to json
     * @throws IOException if writing to the response failed
     */
    private void write(HttpServletResponse response, Map<String, Object> map) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(new Gson().toJson(map));

    }

    /**
     * Manages POST-requests with report data and saves a corresponding
     * timereport in DB.
     * 
     * @param request  the Http request
     * @param response the Http response object to write to
     * 
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        
        Map<String, Object> map = new HashMap<String, Object>();
        boolean isValid = false;
        Timereport tr = new Timereport();
        
        int hours = 0;
        int minutes = 0;
        datesql = new java.sql.Date(date.getTime());
        Long employeeId = (Long) request.getSession().getAttribute("employeeId");
        Employeecontract ec = ecDao.getEmployeeContractByEmployeeIdAndDate(employeeId, date);
        Long selectedOrderId = Long.valueOf(request.getParameter("orderSelect"));
        Suborder sub = soDao.getSuborderById(selectedOrderId);
        Employeeorder eo = eoDao.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(ec.getId(), sub.getId(), date);
        String subLabel = sub.getCustomerorder().getSign() + "/" + sub.getSign() + " " + sub.getShortdescription();
        Double costs = 0.0;
        String description = request.getParameter("comment");
        String createdby = ec.getEmployee().getSign();
        Referenceday rd = rdDao.getReferencedayByDate(date);
        
        
         
        //Check for description existance and if existing  add a preciding space
        if(description == null){
            description ="Mobile booking";
        }
        else {
            description = "Mobile booking " + description;
        }
        
        // Check if the reference day already exists and if not add a new one
        if (rd == null) {
            rdDao.addReferenceday(datesql);
            rd = rdDao.getReferencedayByDate(datesql);
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

        tr.setDurationhours(hours);
        tr.setDurationminutes(minutes);
        tr.setEmployeecontract(ec);
        tr.setSuborder(sub);
        tr.setEmployeeorder(eo);
        tr.setCreatedby(createdby);
        tr.setCreated(date);
        tr.setStatus("open");
        tr.setSortofreport("W");
        tr.setReferenceday(rd);
        tr.setTraining(false);
        tr.setCosts(costs);
        tr.setTaskdescription(description);

        // Saving the report to DB
        try {
            trDao.save(tr, ec.getEmployee(), true);
            isValid = true;
        } catch (Exception e) {
            isValid = false;
            e.printStackTrace();
        }
        
        //Getting the overall booking time for current day
        List<Timereport> trs = trDao.getTimereportsByDateAndEmployeeContractId(ec.getId(), datesql);
        int totalMinutes = 0;
        for(Timereport t : trs){
           totalMinutes = totalMinutes + (t.getDurationhours()*60 +t.getDurationminutes());           
        }
        int summaryHours   = totalMinutes/60;
        int summaryMinutes = totalMinutes%60;

        map.put("isValid", isValid);
        map.put("hours", hours);
        map.put("minutes", minutes);
        map.put("suborder", subLabel);
        map.put("summaryHours", summaryHours);
        map.put("summaryMinutes", summaryMinutes);
        write(response, map);
    }

}
