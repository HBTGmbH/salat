package org.tb.bdom;

public class TrainingOverview {
    
    private final String year;
    private final String commonTrainingTime;
    private final String projectTrainingTime;
    private final String cTTHoursMin;
    private final String pTTHoursMin;
    private final Employeecontract employeecontract;
    
    public String getYear() {
        return year;
    }
    
    public String getCommonTrainingTime() {
        return commonTrainingTime;
    }
    
    public String getProjectTrainingTime() {
        return projectTrainingTime;
    }
    
    public String getcTTHoursMin() {
        return cTTHoursMin;
    }
    
    public String getpTTHoursMin() {
        return pTTHoursMin;
    }
    
    public Employeecontract getEmployeecontract() {
        return employeecontract;
    }
    
    public TrainingOverview(String year, Employeecontract employeecontract, String projectTrainingTime,
            String commonTrainingTime, String pTTHoursMin, String cTTHoursMin) {
        this.year = year;
        this.employeecontract = employeecontract;
        this.projectTrainingTime = projectTrainingTime;
        this.commonTrainingTime = commonTrainingTime;
        this.pTTHoursMin = pTTHoursMin;
        this.cTTHoursMin = cTTHoursMin;
        
    }
    
}
