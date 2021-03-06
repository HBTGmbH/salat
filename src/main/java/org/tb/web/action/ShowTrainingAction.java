package org.tb.web.action;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.TrainingOverview;
import org.tb.helper.EmployeeHelper;
import org.tb.helper.TrainingHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.TrainingDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.ShowTrainingForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Action class for trainings to be shown on separate page
 */
public class ShowTrainingAction extends LoginRequiredAction {

    private final static String TRAINING_ID = "i976";
    private EmployeecontractDAO employeecontractDAO;
    private TrainingDAO trainingDAO;
    private EmployeeDAO employeeDAO;
    private CustomerorderDAO customerorderDAO;

    public EmployeecontractDAO getEmployeecontractDAO() {
        return employeecontractDAO;
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    public TrainingDAO getTrainingDAO() {
        return trainingDAO;
    }

    public void setTrainingDAO(TrainingDAO trainingDAO) {
        this.trainingDAO = trainingDAO;
    }

    public EmployeeDAO getEmployeeDAO() {
        return employeeDAO;
    }

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws ParseException {

        ShowTrainingForm trainingForm = (ShowTrainingForm) form;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        Date startdate = simpleDateFormat.parse(trainingForm.getStartdate());
        Date enddate = simpleDateFormat.parse(trainingForm.getEnddate());

        //check for refresh
        if (request.getParameter("task") != null && request.getParameter("task").equals("refresh")) {
            boolean refreshSuccessful = refreshTraining(request, trainingForm,
                    employeecontractDAO, customerorderDAO, startdate, enddate);
            if (refreshSuccessful) {
                if (trainingForm.getEmployeeContractId() == -1) {
                    request.getSession().setAttribute("currentEmployeeContract", null);
                } else {
                    Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(trainingForm.getEmployeeContractId());
                    request.getSession().setAttribute("currentEmployeeContract", employeecontract);
                }
                return mapping.findForward("success");
            } else {
                return mapping.findForward("error");
            }
        } else if (request.getParameter("task") != null) {
            // just go back to main menu
            if (request.getParameter("task").equalsIgnoreCase("back")) {
                return mapping.findForward("backtomenu");
            } else {
                return mapping.findForward("success");
            }
        } else if (request.getParameter("task") == null) {
            //*** initialisation ***
            String forward = init(request, trainingForm, employeecontractDAO, customerorderDAO, startdate, enddate);
            return mapping.findForward(forward);
        }
        request.getSession().setAttribute("showTrainingForm", trainingForm);
        return mapping.findForward("success");
    }

    protected boolean refreshTraining(HttpServletRequest request, ShowTrainingForm trainingForm, EmployeecontractDAO employeecontractDAO, CustomerorderDAO customerorderDAO,
                                      Date startdate, Date enddate) {
        String year = trainingForm.getYear();
        long employeeContractId = trainingForm.getEmployeeContractId();
        request.getSession().setAttribute("showTrainingForm", trainingForm);
        Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(employeeContractId);
        Customerorder trainingOrder = customerorderDAO.getCustomerorderBySign(TRAINING_ID);
        long orderID = trainingOrder.getId();
        List<TrainingOverview> trainingOverviews;

        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();
        employeecontracts.removeIf(c -> c.getFreelancer() || c.getDailyWorkingTime() <= 0 || c.getEmployeeorders() == null);
        request.getSession().setAttribute("employeecontracts", employeecontracts);

        // refresh all relevant attributes
        if (trainingForm.getEmployeeContractId() == -1 || employeecontract.getFreelancer() || employeecontract.getDailyWorkingTime() <= 0
                || employeecontract.getEmployeeorders() == null) {
            // get the training times for specific year, all employees, all orders (project Training) and order i976 (CommonTraining)
            trainingOverviews = getTrainingOverviewsForAll(startdate,
                    enddate, employeecontractDAO, orderID, employeecontracts, year);
            request.getSession().setAttribute("currentEmployeeId", -1L);
            request.getSession().setAttribute("years", DateUtils.getYearsToDisplay());

        } else {
            // get the training times for specific year, specific employee, all orders (project Training) and order i976 (CommonTraining)
            trainingOverviews = getTrainingOverviewByEmployeecontract(startdate,
                    enddate, employeecontract, orderID, year);

            request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
            request.getSession().setAttribute("years", DateUtils.getYearsSinceContractStartToDisplay(employeecontract.getValidFrom()));
        }
        request.getSession().setAttribute("trainingOverview", trainingOverviews);
        request.getSession().setAttribute("year", year);

        return true;

    }

    /**
     * Called if no special task is given, called from menu eg. Prepares everything to show trainings of current year of
     * logged-in user.
     */
    private String init(HttpServletRequest request, ShowTrainingForm trainingForm, EmployeecontractDAO employeecontractDAO, CustomerorderDAO customerorderDAO, Date startdate, Date enddate) {
        String forward = "success";
        String year = trainingForm.getYear();
        long employeeContractId = trainingForm.getEmployeeContractId();
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        Employeecontract ec = new EmployeeHelper().setCurrentEmployee(loginEmployee, request, employeeDAO, employeecontractDAO);
        long orderID = customerorderDAO.getCustomerorderBySign(TRAINING_ID).getId();
        List<TrainingOverview> trainingOverview;

        request.getSession().setAttribute("showTrainingForm", trainingForm);

        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();
        if (employeecontracts == null || employeecontracts.isEmpty()) {
            request.setAttribute("errorMessage", "No employees with valid contracts that have training entitlement found - please call system administrator.");
            forward = "error";
            return forward;
        }

        employeecontracts.removeIf(c -> c.getFreelancer() || c.getDailyWorkingTime() <= 0 || c.getEmployeeorders() == null);

        if (ec == null) {
            request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
            forward = "error";
            return forward;
        }

        // set all relevant attributes
        request.getSession().setAttribute("employeecontracts", employeecontracts);
        request.getSession().setAttribute("year", year);

        // If all Employees are to be shown, get a list of TrainingOverviews with an entry for each Employeecontract.
        // Is the case if All Employees preselected on other page or current logged-in employee has no training, e.g. as admin.
        if (employeeContractId == -1 || ec.getFreelancer() || ec.getDailyWorkingTime() <= 0 || ec.getEmployeeorders() == null) {
            trainingOverview = getTrainingOverviewsForAll(startdate,
                    enddate, employeecontractDAO, orderID, employeecontracts, year);
            request.getSession().setAttribute("currentEmployeeId", -1L);
            request.getSession().setAttribute("years", DateUtils.getYearsToDisplay());
            // get a List of TrainingOverviews with only one entry for the selected Employee
        } else {
            trainingOverview = getTrainingOverviewByEmployeecontract(startdate,
                    enddate, ec, orderID, year);
            request.getSession().setAttribute("currentEmployeeId", employeeContractId);
            request.getSession().setAttribute("years", DateUtils.getYearsSinceContractStartToDisplay(ec.getValidFrom()));
        }
        request.getSession().setAttribute("trainingOverview", trainingOverview);
        return forward;
    }

    private List<TrainingOverview> getTrainingOverviewsForAll(Date startdate,
                                                              Date enddate, EmployeecontractDAO employeecontractDAO, Long orderID, List<Employeecontract> employeecontracts, String year) {
        List<TrainingOverview> trainingOverviews = new LinkedList<>();
        List<Object[]> cTrain = trainingDAO.getCommonTrainingTimesByDates(employeecontractDAO, startdate, enddate, orderID);
        List<Object[]> pTrain = trainingDAO.getProjectTrainingTimesByDates(employeecontractDAO, startdate, enddate);
        Map<Long, Object[]> projTrain = createMap(pTrain);
        Map<Long, Object[]> comTrain = createMap(cTrain);

        for (Employeecontract empCon : employeecontracts) {
            TrainingOverview to;
            Object[] commonTraining = comTrain.get(empCon.getId());
            Object[] projectTraining = projTrain.get(empCon.getId());
            if (commonTraining == null && projectTraining == null) {
                to = new TrainingOverview(year, empCon, GlobalConstants.ZERO_DHM, GlobalConstants.ZERO_DHM, GlobalConstants.ZERO_HM, GlobalConstants.ZERO_HM);
            } else if (commonTraining == null) {
                Object[] t = {projectTraining[1], projectTraining[2]};
                int[] ti = TrainingHelper.getHoursMin(t);
                String time = TrainingHelper.fromDBtimeToString(empCon, ti[0], ti[1]);
                String hoursMin = TrainingHelper.hoursMinToString(ti);
                to = new TrainingOverview(year, empCon, time, GlobalConstants.ZERO_DHM, hoursMin, GlobalConstants.ZERO_HM);
            } else if (projectTraining == null) {
                Object[] t = {commonTraining[1], commonTraining[2]};
                int[] ti = TrainingHelper.getHoursMin(t);
                String time = TrainingHelper.fromDBtimeToString(empCon, ti[0], ti[1]);
                String hoursMin = TrainingHelper.hoursMinToString(ti);
                to = new TrainingOverview(year, empCon, GlobalConstants.ZERO_DHM, time, GlobalConstants.ZERO_HM, hoursMin);
            } else {
                Object[] cT = {commonTraining[1], commonTraining[2]};
                int[] tcT = TrainingHelper.getHoursMin(cT);
                String commonTime = TrainingHelper.fromDBtimeToString(empCon, tcT[0], tcT[1]);
                String cHoursMin = TrainingHelper.hoursMinToString(tcT);
                Object[] pT = {projectTraining[1], projectTraining[2]};
                int[] tpT = TrainingHelper.getHoursMin(pT);
                String projectTime = TrainingHelper.fromDBtimeToString(empCon, tpT[0], tpT[1]);
                String pHoursMin = TrainingHelper.hoursMinToString(tpT);
                to = new TrainingOverview(year, empCon, projectTime, commonTime, pHoursMin, cHoursMin);
            }
            trainingOverviews.add(to);
        }
        return trainingOverviews;
    }

    private Map<Long, Object[]> createMap(List<Object[]> objectList) {
        Map<Long, Object[]> projTrain = new HashMap<>();
        for (Object[] o : objectList) {
            if (o[0] != null && o[0] instanceof Long) {
                projTrain.put((Long) o[0], o);
            }
        }
        return projTrain;
    }

    private List<TrainingOverview> getTrainingOverviewByEmployeecontract(Date startdate,
                                                                         Date enddate, Employeecontract ec, Long orderID, String year) {
        List<TrainingOverview> result = new LinkedList<>();

        Object[] cTT = trainingDAO.getCommonTrainingTimesByDatesAndEmployeeContractId(ec, startdate, enddate, orderID);
        Object[] pTT = trainingDAO.getProjectTrainingTimesByDatesAndEmployeeContractId(ec, startdate, enddate);

        int[] cTime = TrainingHelper.getHoursMin(cTT);
        String commonTrainingTime = TrainingHelper.fromDBtimeToString(ec, cTime[0], cTime[1]);
        String cHoursMin = TrainingHelper.hoursMinToString(cTime);
        int[] pTime = TrainingHelper.getHoursMin(pTT);
        String projectTrainingTime = TrainingHelper.fromDBtimeToString(ec, pTime[0], pTime[1]);
        String pHoursMin = TrainingHelper.hoursMinToString(pTime);

        TrainingOverview to = new TrainingOverview(year, ec, projectTrainingTime, commonTrainingTime, pHoursMin, cHoursMin);
        result.add(to);
        return result;
    }
}
