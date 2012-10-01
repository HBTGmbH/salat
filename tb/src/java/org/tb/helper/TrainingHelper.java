package org.tb.helper;

import org.tb.bdom.Employeecontract;

public class TrainingHelper {
    
    public static int[] getHoursMin(Object[] time) {
        int[] intTime = { 0, 0 };
        if (time != null && time.length == 2) {
            if (time[0] != null && time[0] instanceof Long) {
                Long hours = (Long)time[0];
                intTime[0] = hours.intValue();
            }
            if (time[1] != null && time[1] instanceof Long) {
                Long min = (Long)time[1];
                intTime[1] = min.intValue();
            }
        }
        return intTime;
    }
    
    public static int getMinutesForHourDouble(Double doubleValue) {
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
    
    public static String fromDBtimeToString(Employeecontract ec, int hours, int minutes) {
        
        int trainingDays = 0;
        int trainingHours = 0;
        int trainingMinutes = 0;
        int restMinutes;
        
        int totalTrainingMinutes = hours * 60 + minutes;
        
        int dailyWorkingTimeMinutes = getMinutesForHourDouble(ec.getDailyWorkingTime());
        
        if (dailyWorkingTimeMinutes != 0) {
            trainingDays = totalTrainingMinutes / dailyWorkingTimeMinutes;
            restMinutes = totalTrainingMinutes % dailyWorkingTimeMinutes;
            trainingHours = restMinutes / 60;
            trainingMinutes = restMinutes % 60;
        }
        
        StringBuffer trainingString = new StringBuffer();
        if (trainingDays < 10) {
            trainingString.append(0);
        }
        trainingString.append(trainingDays);
        trainingString.append(':');
        if (trainingHours < 10) {
            trainingString.append(0);
        }
        trainingString.append(trainingHours);
        trainingString.append(':');
        if (trainingMinutes < 10) {
            trainingString.append(0);
        }
        trainingString.append(trainingMinutes);
        
        return trainingString.toString();
    }
    
    public static String hoursMinToString(int[] time) {
        
        int trainingHours = time[0];
        int trainingMinutes = time[1];
        
        trainingHours += trainingMinutes / 60;
        trainingMinutes = trainingMinutes % 60;
        
        StringBuffer trainingString = new StringBuffer();
        
        if (trainingHours < 10) {
            trainingString.append(0);
        }
        trainingString.append(trainingHours);
        trainingString.append(':');
        if (trainingMinutes < 10) {
            trainingString.append(0);
        }
        trainingString.append(trainingMinutes);
        
        return trainingString.toString();
    }
    
}
