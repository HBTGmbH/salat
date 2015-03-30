package org.tb.helper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Timereport;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.TimereportDAO;

public class VacationViewer implements Serializable {
	
	private static final long serialVersionUID = 1L;
    
    private String suborderSign;
    private double budget;
    private int usedVacationHours;
    private int usedVacationMinutes;
    
    private final Employeecontract employeecontract;
    
    public VacationViewer(Employeecontract employeecontract) {
        this.employeecontract = employeecontract;
    }
    
    public double getBudget() {
        return budget;
    }
    
    public void setBudget(double budget) {
        this.budget = budget;
    }
    
    public String getSuborderSign() {
        return suborderSign;
    }
    
    public void setSuborderSign(String suborderSign) {
        this.suborderSign = suborderSign;
    }
    
    public int getUsedVacationHours() {
        return usedVacationHours;
    }
    
    public void setUsedVacationHours(int usedVacationHours) {
        this.usedVacationHours = usedVacationHours;
    }
    
    public int getUsedVacationMinutes() {
        return usedVacationMinutes;
    }
    
    public void setUsedVacationMinutes(int usedVacationMinutes) {
        this.usedVacationMinutes = usedVacationMinutes;
    }
    
    public void addVacationMinutes(int minutes) {
        this.usedVacationMinutes += minutes;
    }
    
    public void addVacationHours(int hours) {
        this.usedVacationHours += hours;
    }
    
    public boolean getExtended() {
        return getTime() > budget;
    }
    
    public double getTime() {
        int totalVacationMinutes = usedVacationHours * 60 + usedVacationMinutes;
        int hours = totalVacationMinutes / 60;
        int minutes = totalVacationMinutes % 60;
        Double usedTime = minutes / 60.0 + hours;
        usedTime += 0.005;
        usedTime *= 100;
        int temp = usedTime.intValue();
        usedTime = temp / 100.0;
        return usedTime;
    }
    
    public String getVacationString() {
        int totalVacationMinutes = usedVacationHours * 60 + usedVacationMinutes;
        
        int dailyWorkingTimeMinutes = getMinutesForHourDouble(employeecontract.getDailyWorkingTime());
        
        int vacationDays = totalVacationMinutes / dailyWorkingTimeMinutes;
        int restMinutes = totalVacationMinutes % dailyWorkingTimeMinutes;
        int vacationHours = restMinutes / 60;
        int vacationMinutes = restMinutes % 60;
        
        int totalBudgetMinutes = getMinutesForHourDouble(budget);
        
        int budgetDays = totalBudgetMinutes / dailyWorkingTimeMinutes;
        int budgetRestMinutes = totalBudgetMinutes % dailyWorkingTimeMinutes;
        int budgetHours = budgetRestMinutes / 60;
        int budgetMinutes = budgetRestMinutes % 60;
        
        return vacationDays + ":" + vacationHours + ":" + vacationMinutes + " / " + budgetDays + ":" + budgetHours + ":" + budgetMinutes;
    }
    
    public String getUsedVacationString() {
        int totalVacationMinutes = usedVacationHours * 60 + usedVacationMinutes;
        
        int dailyWorkingTimeMinutes = getMinutesForHourDouble(employeecontract.getDailyWorkingTime());
        
        int vacationDays = totalVacationMinutes / dailyWorkingTimeMinutes;
        int restMinutes = totalVacationMinutes % dailyWorkingTimeMinutes;
        int vacationHours = restMinutes / 60;
        int vacationMinutes = restMinutes % 60;
        
        StringBuffer vacationString = new StringBuffer();
        if (vacationDays < 10) {
            vacationString.append(0);
        }
        vacationString.append(vacationDays);
        vacationString.append(':');
        if (vacationHours < 10) {
            vacationString.append(0);
        }
        vacationString.append(vacationHours);
        vacationString.append(':');
        if (vacationMinutes < 10) {
            vacationString.append(0);
        }
        vacationString.append(vacationMinutes);
        
        return vacationString.toString();
    }
    
    public String getBudgetVacationString() {
        int dailyWorkingTimeMinutes = getMinutesForHourDouble(employeecontract.getDailyWorkingTime());
        
        int totalBudgetMinutes = getMinutesForHourDouble(budget);
        
        int budgetDays = totalBudgetMinutes / dailyWorkingTimeMinutes;
        int budgetRestMinutes = totalBudgetMinutes % dailyWorkingTimeMinutes;
        int budgetHours = budgetRestMinutes / 60;
        int budgetMinutes = budgetRestMinutes % 60;
        
        StringBuffer vacationString = new StringBuffer();
        if (budgetDays < 10) {
            vacationString.append(0);
        }
        vacationString.append(budgetDays);
        vacationString.append(':');
        if (budgetHours < 10) {
            vacationString.append(0);
        }
        vacationString.append(budgetHours);
        vacationString.append(':');
        if (budgetMinutes < 10) {
            vacationString.append(0);
        }
        vacationString.append(budgetMinutes);
        
        return vacationString.toString();
    }
    
    private int getMinutesForHourDouble(Double doubleValue) {
        int hours = doubleValue.intValue();
        doubleValue = doubleValue - hours;
        int minutes = 0;
        if (doubleValue != 0.0) {
            doubleValue *= 100;
            minutes = doubleValue.intValue() * 60 / 100;
        }
        minutes += hours * 60;
        return minutes;
    }
    
    /**
     * vacation v2
     * 
     * computes a list of VacationViews. Every VacationView contains one vacation-based suborder (Urlaub <Jahr>, Sonderurlaub, Resturlaub)
     * that is valid at the date of request with a sum of all durations booked for this suborder for this employee.
     * Saves the Vacations-List as an attribute in the Request.
     * 
     * @param request
     * @param employeecontract
     * @param employeeorderDAO
     * @param timereportDAO
     */
    public void computeVacations(HttpServletRequest request, Employeecontract employeecontract, EmployeeorderDAO employeeorderDAO, TimereportDAO timereportDAO) {
        
        java.sql.Date today = new java.sql.Date(new java.util.Date().getTime());
        
        List<Employeeorder> orders = new ArrayList<Employeeorder>();
        
        List<Employeeorder> specialVacationOrders = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndCustomerOrderSignAndDate(employeecontract.getId(),
                GlobalConstants.CUSTOMERORDER_SIGN_REMAINING_VACATION,
                today);
        List<Employeeorder> vacationOrders = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndCustomerOrderSignAndDate(employeecontract.getId(),
                GlobalConstants.CUSTOMERORDER_SIGN_VACATION,
                today);
        List<Employeeorder> extraVacationOrders = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndCustomerOrderSignAndDate(employeecontract.getId(),
                GlobalConstants.CUSTOMERORDER_SIGN_EXTRA_VACATION,
                today);
        
        orders.addAll(specialVacationOrders);
        for (Employeeorder vacation : vacationOrders) {
            if (!vacation.getSuborder().getSign().equals(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                orders.add(vacation);
            }
        }
        orders.addAll(extraVacationOrders);
        
        List<VacationViewer> vacations = new ArrayList<VacationViewer>();
        
        for (Employeeorder employeeorder : orders) {
            VacationViewer vacationView = new VacationViewer(employeecontract);
            vacationView.setSuborderSign(employeeorder.getSuborder().getDescription());
            if (employeeorder.getDebithours() != null) {
                vacationView.setBudget(employeeorder.getDebithours());
            }
            
            List<Timereport> timereports = timereportDAO.getTimereportsBySuborderIdAndEmployeeContractId(employeeorder.getSuborder().getId(), employeecontract.getId());
            for (Timereport timereport : timereports) {
                vacationView.addVacationHours(timereport.getDurationhours());
                vacationView.addVacationMinutes(timereport.getDurationminutes());
            }
            vacations.add(vacationView);
        }
        request.getSession().setAttribute("vacations", vacations);
    }
    
}
