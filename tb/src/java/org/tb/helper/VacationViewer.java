package org.tb.helper;

import org.tb.bdom.Employeecontract;

public class VacationViewer {
	
	private String suborderSign;
	private double budget;
	private int usedVacationHours;
	private int usedVacationMinutes;
	
	private Employeecontract employeecontract;
	
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
		int totalVacationMinutes = usedVacationHours*60 + usedVacationMinutes;
		int hours = totalVacationMinutes/60;
		int minutes = totalVacationMinutes%60;
		Double usedTime = minutes/60.0 + hours;
		usedTime += 0.005;
		usedTime *= 100;
		int temp = usedTime.intValue();
		usedTime = temp / 100.0;
		return usedTime;
	}
	
	public String getVacationString() {
		int totalVacationMinutes = usedVacationHours*60 + usedVacationMinutes;
						
		int dailyWorkingTimeMinutes = getMinutesForHourDouble(employeecontract.getDailyWorkingTime());
		
		int vacationDays = totalVacationMinutes/dailyWorkingTimeMinutes;
		int restMinutes = totalVacationMinutes%dailyWorkingTimeMinutes;
		int vacationHours = restMinutes/60;
		int vacationMinutes = restMinutes%60;
		
		int totalBudgetMinutes = getMinutesForHourDouble(budget);
		
		int budgetDays = totalBudgetMinutes/dailyWorkingTimeMinutes;
		int budgetRestMinutes = totalBudgetMinutes%dailyWorkingTimeMinutes;
		int budgetHours = budgetRestMinutes/60;
		int budgetMinutes = budgetRestMinutes%60;
			
		return vacationDays+":"+vacationHours+":"+vacationMinutes+" / "+budgetDays+":"+budgetHours+":"+budgetMinutes;
	}
	
	
	public String getUsedVacationString() {
		int totalVacationMinutes = usedVacationHours*60 + usedVacationMinutes;
						
		int dailyWorkingTimeMinutes = getMinutesForHourDouble(employeecontract.getDailyWorkingTime());
		
		int vacationDays = totalVacationMinutes/dailyWorkingTimeMinutes;
		int restMinutes = totalVacationMinutes%dailyWorkingTimeMinutes;
		int vacationHours = restMinutes/60;
		int vacationMinutes = restMinutes%60;
		
		StringBuffer vacationString = new StringBuffer();
		if(vacationDays < 10) {
			vacationString.append(0);
		}
		vacationString.append(vacationDays);
		vacationString.append(':');
		if(vacationHours < 10 ) {
			vacationString.append(0);
		}
		vacationString.append(vacationHours);
		vacationString.append(':');
		if(vacationMinutes < 10) {
			vacationString.append(0);
		}
		vacationString.append(vacationMinutes);
			
		return vacationString.toString();
	}
	
	
	public String getBudgetVacationString() {
		int dailyWorkingTimeMinutes = getMinutesForHourDouble(employeecontract.getDailyWorkingTime());
		
		int totalBudgetMinutes = getMinutesForHourDouble(budget);
		
		int budgetDays = totalBudgetMinutes/dailyWorkingTimeMinutes;
		int budgetRestMinutes = totalBudgetMinutes%dailyWorkingTimeMinutes;
		int budgetHours = budgetRestMinutes/60;
		int budgetMinutes = budgetRestMinutes%60;
			
		StringBuffer vacationString = new StringBuffer();
		if(budgetDays < 10) {
			vacationString.append(0);
		}
		vacationString.append(budgetDays);
		vacationString.append(':');
		if(budgetHours < 10 ) {
			vacationString.append(0);
		}
		vacationString.append(budgetHours);
		vacationString.append(':');
		if(budgetMinutes < 10) {
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
			minutes = (doubleValue.intValue()*60)/100;
		}
		minutes += (hours * 60);
		return minutes;
	}

}
