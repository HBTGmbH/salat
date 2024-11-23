package org.tb.dailyreport.action;

import static org.tb.common.util.DateTimeUtils.getYearsToDisplay;
import static org.tb.common.util.DateUtils.getCurrentYear;
import static org.tb.common.util.DateUtils.getYear;
import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.common.GlobalConstants;
import org.tb.common.OptionItem;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.TrainingInformation;
import org.tb.dailyreport.domain.TrainingOverview;
import org.tb.dailyreport.service.TrainingService;
import org.tb.dailyreport.viewhelper.TrainingHelper;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.employee.viewhelper.EmployeeViewHelper;
import org.tb.order.domain.Customerorder;
import org.tb.order.service.CustomerorderService;

/**
 * Action class for trainings to be shown on separate page
 */
@Component
@RequiredArgsConstructor
public class ShowTrainingAction extends LoginRequiredAction<ShowTrainingForm> {

    private final static String TRAINING_ID = "i976";
    private final EmployeecontractService employeecontractService;
    private final TrainingService trainingService;
    private final EmployeeService employeeService;
    private final CustomerorderService customerorderService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ShowTrainingForm trainingForm, HttpServletRequest request, HttpServletResponse response) throws ParseException {

        LocalDate startdate = DateUtils.parse(trainingForm.getStartdate());
        LocalDate enddate = DateUtils.parse(trainingForm.getEnddate());

        //check for refresh
        if (request.getParameter("task") != null && request.getParameter("task").equals("refresh")) {
            boolean refreshSuccessful = refreshTraining(request, trainingForm, startdate, enddate);
            if (refreshSuccessful) {
                if (trainingForm.getEmployeeContractId() == -1) {
                    request.getSession().setAttribute("currentEmployeeContract", null);
                } else {
                    Employeecontract employeecontract = employeecontractService.getEmployeeContractById(trainingForm.getEmployeeContractId());
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
            String forward = init(request, trainingForm, startdate, enddate);
            return mapping.findForward(forward);
        }
        request.getSession().setAttribute("showTrainingForm", trainingForm);
        return mapping.findForward("success");
    }

    protected boolean refreshTraining(HttpServletRequest request, ShowTrainingForm trainingForm, LocalDate startdate, LocalDate enddate) {
        String year = trainingForm.getYear();
        long employeeContractId = trainingForm.getEmployeeContractId();
        request.getSession().setAttribute("showTrainingForm", trainingForm);
        Employeecontract employeecontract = employeecontractService.getEmployeeContractById(employeeContractId);
        Customerorder trainingOrder = customerorderService.getCustomerorderBySign(TRAINING_ID);
        long orderID = trainingOrder.getId();
        List<TrainingOverview> trainingOverviews;

        List<Employeecontract> employeecontracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today());
        employeecontracts.removeIf(c -> c.getFreelancer()
                                        || c.getDailyWorkingTime().toMinutes() <= 0
                                        || c.getEmployeeorders() == null
                                        || c.getEmployeeorders().isEmpty());
        request.getSession().setAttribute("employeecontracts", employeecontracts);

        // refresh all relevant attributes
        if (trainingForm.getEmployeeContractId() == -1
            || employeecontract.getFreelancer()
            || employeecontract.getDailyWorkingTime().toMinutes() <= 0
            || employeecontract.getEmployeeorders() == null) {
            // get the training times for specific year, all employees, all orders (project Training) and order i976 (CommonTraining)
            trainingOverviews = getTrainingOverviewsForAll(startdate, enddate, orderID, employeecontracts, year);
            request.getSession().setAttribute("currentEmployeeId", -1L);
            request.getSession().setAttribute("years", getYearsToDisplay());

        } else {
            // get the training times for specific year, specific employee, all orders (project Training) and order i976 (CommonTraining)
            trainingOverviews = getTrainingOverviewByEmployeecontract(startdate, enddate, employeecontract, orderID, year);

            request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
            request.getSession().setAttribute("years", getYearsSinceContractStartToDisplay(employeecontract.getValidFrom()));
        }
        request.getSession().setAttribute("trainingOverview", trainingOverviews);
        request.getSession().setAttribute("year", year);

        return true;

    }

    /**
     * Called if no special task is given, called from menu eg. Prepares everything to show trainings of current year of
     * logged-in user.
     */
    private String init(HttpServletRequest request, ShowTrainingForm trainingForm, LocalDate startdate, LocalDate enddate) {
        String forward = "success";
        String year = trainingForm.getYear();
        long employeeContractId = trainingForm.getEmployeeContractId();
        Employeecontract ec = new EmployeeViewHelper().getAndInitCurrentEmployee(request, employeeService, employeecontractService);
        Customerorder trainingOrder = customerorderService.getCustomerorderBySign(TRAINING_ID);
        if (trainingOrder == null) {
            request.setAttribute("errorMessage", "No training customer order has been found matching " + TRAINING_ID + " - please call system administrator.");
            forward = "error";
            return forward;
        }
        long trainingCustomerorderId = trainingOrder.getId();
        List<TrainingOverview> trainingOverview;

        request.getSession().setAttribute("showTrainingForm", trainingForm);

        List<Employeecontract> employeecontracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today());
        if (employeecontracts == null || employeecontracts.isEmpty()) {
            request.setAttribute("errorMessage", "No employees with valid contracts that have training entitlement found - please call system administrator.");
            forward = "error";
            return forward;
        }

        employeecontracts.removeIf(c -> c.getFreelancer()
                                        || c.getDailyWorkingTime().toMinutes() <= 0
                                        || c.getEmployeeorders() == null
                                        || !authorizedUser.isManager() && !c.getEmployee().getId().equals(authorizedUser.getEmployeeId()));

        // set all relevant attributes
        request.getSession().setAttribute("employeecontracts", employeecontracts);
        request.getSession().setAttribute("year", year);

        // If all Employees are to be shown, get a list of TrainingOverviews with an entry for each Employeecontract.
        // Is the case if All Employees preselected on other page or current logged-in employee has no training, e.g. as admin.
        if (employeeContractId == -1
            || ec.getFreelancer()
            || ec.getDailyWorkingTime().toMinutes() <= 0 || ec.getEmployeeorders() == null) {
            trainingOverview = getTrainingOverviewsForAll(startdate, enddate, trainingCustomerorderId, employeecontracts, year);
            request.getSession().setAttribute("currentEmployeeId", -1L);
            request.getSession().setAttribute("years", getYearsToDisplay());
            // get a List of TrainingOverviews with only one entry for the selected Employee
        } else {
            trainingOverview = getTrainingOverviewByEmployeecontract(startdate,
                    enddate, ec, trainingCustomerorderId, year);
            request.getSession().setAttribute("currentEmployeeId", employeeContractId);
            request.getSession().setAttribute("years", getYearsSinceContractStartToDisplay(ec.getValidFrom()));
        }
        request.getSession().setAttribute("trainingOverview", trainingOverview);
        return forward;
    }

    private List<TrainingOverview> getTrainingOverviewsForAll(
        LocalDate startdate,
        LocalDate enddate,
        long trainingCustomerorderId,
        List<Employeecontract> employeecontracts,
        String year
    ) {
        List<TrainingOverview> trainingOverviews = new LinkedList<>();
        List<TrainingInformation> cTrain = trainingService.getCommonTrainingTimesByDates(startdate, enddate, trainingCustomerorderId);
        List<TrainingInformation> pTrain = trainingService.getProjectTrainingTimesByDates(startdate, enddate);
        Map<Long, TrainingInformation> projTrain = createMap(pTrain);
        Map<Long, TrainingInformation> comTrain = createMap(cTrain);

        for (Employeecontract empCon : employeecontracts) {
            TrainingOverview to;
            TrainingInformation commonTraining = comTrain.get(empCon.getId());
            TrainingInformation projectTraining = projTrain.get(empCon.getId());
            if (commonTraining == null && projectTraining == null) {
                to = new TrainingOverview(year, empCon, GlobalConstants.ZERO_DHM, GlobalConstants.ZERO_DHM, GlobalConstants.ZERO_HM, GlobalConstants.ZERO_HM);
            } else if (commonTraining == null) {
                int[] ti = TrainingHelper.getHoursMin(projectTraining);
                String time = TrainingHelper.fromDBtimeToString(empCon, ti[0], ti[1]);
                String hoursMin = TrainingHelper.hoursMinToString(ti);
                to = new TrainingOverview(year, empCon, time, GlobalConstants.ZERO_DHM, hoursMin, GlobalConstants.ZERO_HM);
            } else if (projectTraining == null) {
                int[] ti = TrainingHelper.getHoursMin(commonTraining);
                String time = TrainingHelper.fromDBtimeToString(empCon, ti[0], ti[1]);
                String hoursMin = TrainingHelper.hoursMinToString(ti);
                to = new TrainingOverview(year, empCon, GlobalConstants.ZERO_DHM, time, GlobalConstants.ZERO_HM, hoursMin);
            } else {
                int[] tcT = TrainingHelper.getHoursMin(commonTraining);
                String commonTime = TrainingHelper.fromDBtimeToString(empCon, tcT[0], tcT[1]);
                String cHoursMin = TrainingHelper.hoursMinToString(tcT);
                int[] tpT = TrainingHelper.getHoursMin(projectTraining);
                String projectTime = TrainingHelper.fromDBtimeToString(empCon, tpT[0], tpT[1]);
                String pHoursMin = TrainingHelper.hoursMinToString(tpT);
                to = new TrainingOverview(year, empCon, projectTime, commonTime, pHoursMin, cHoursMin);
            }
            trainingOverviews.add(to);
        }
        return trainingOverviews;
    }

    private Map<Long, TrainingInformation> createMap(List<TrainingInformation> objectList) {
        Map<Long, TrainingInformation> projTrain = new HashMap<>();
        for (TrainingInformation o : objectList) {
            projTrain.put(o.getEmployeecontractId(), o);
        }
        return projTrain;
    }

    private List<TrainingOverview> getTrainingOverviewByEmployeecontract(LocalDate startdate, LocalDate enddate,
        Employeecontract ec, long trainingCustomerorderId, String year) {
        List<TrainingOverview> result = new LinkedList<>();

        TrainingInformation cTT = trainingService.getCommonTrainingTimesByDatesAndEmployeeContractId(ec, startdate, enddate, trainingCustomerorderId)
            .orElse(new TrainingInformation(ec.getId(), 0, 0));
        TrainingInformation pTT = trainingService.getProjectTrainingTimesByDatesAndEmployeeContractId(ec, startdate, enddate)
            .orElse(new TrainingInformation(ec.getId(), 0, 0));

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

    /*
     * builds up a list of string with current and previous years since startyear of contract
     */
    private static List<OptionItem> getYearsSinceContractStartToDisplay(LocalDate validFrom) {
        List<OptionItem> theList = new ArrayList<>();

        int startyear = getYear(validFrom).getValue();

        for (int i = startyear; i <= getCurrentYear() + 1; i++) {
            String yearString = "" + i;
            theList.add(new OptionItem(yearString, yearString));
        }

        return theList;
    }

}
