package org.tb.persistence;

import java.util.Date;
import java.util.List;

import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Employeecontract;

/**
 * DAO class for 'Training'
 * 
 * @author sql
 *
 */
public class TrainingDAO extends HibernateDaoSupport {
    
    @SuppressWarnings("unchecked")
    public List<Object[]> getProjectTrainingTimesByDates(EmployeecontractDAO employeecontractDAO, Date begin, Date end) {
        List<Object[]> results = getSession()
                .createQuery("select t.employeecontract.id, sum(t.durationhours), sum(t.durationminutes) from Timereport t " +
                        "where t.employeecontract.freelancer=false and t.employeecontract.dailyWorkingTime>0 and " +
                        "t.referenceday.refdate >= ? and t.referenceday.refdate <= ?  and t.training = true " +
                        "group by t.employeecontract.id")
                .setDate(0, begin).setDate(1, end).list();
        return results;
    }
    
    @SuppressWarnings("unchecked")
    public List<Object[]> getCommonTrainingTimesByDates(EmployeecontractDAO employeecontractDAO, Date begin, Date end, long orderID) {
        List<Object[]> results = getSession()
                .createQuery("select t.employeecontract.id, sum(t.durationhours), sum(t.durationminutes) from Timereport t " +
                        "where t.employeecontract.freelancer=false and t.employeecontract.dailyWorkingTime>0 and " +
                        "t.referenceday.refdate >= ? and t.referenceday.refdate <= ?  and t.suborder.customerorder.id=?" +
                        " and  t.suborder.sign not like 'x_%'  " +
                        "group by t.employeecontract.id")
                .setDate(0, begin).setDate(1, end).setLong(2, orderID).list();
        return results;
    }
    
    public Object[] getProjectTrainingTimesByDatesAndEmployeeContractId(Employeecontract employeecontract, Date begin, Date end) {
        long ecId = employeecontract.getId();
        Object[] result = (Object[])getSession()
                .createQuery("select sum(t.durationhours), sum(t.durationminutes) from Timereport t where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.employeecontract.id = ? and t.training = true")
                .setDate(0, begin).setDate(1, end).setLong(2, ecId).uniqueResult();
        return result;
    }
    
    public Object[] getCommonTrainingTimesByDatesAndEmployeeContractId(Employeecontract employeecontract, Date begin, Date end, long orderID) {
        long ecId = employeecontract.getId();
        Object[] result = (Object[])getSession()
                .createQuery("select sum(t.durationhours), sum(t.durationminutes) from Timereport t where t.referenceday.refdate >= ? " +
                        "and t.referenceday.refdate <= ? and t.employeecontract.id = ? and t.suborder.customerorder.id=? and  t.suborder.sign not like 'x_%'")
                .setDate(0, begin).setDate(1, end).setLong(2, ecId).setLong(3, orderID).uniqueResult();
        return result;
    }
    
    //    private int[] getHoursMin(Object[] time) {
    //        int[] intTime = { 0, 0 };
    //        if (time != null && time.length == 2) {
    //            if (time[0] != null && time[0] instanceof Long) {
    //                Long hours = (Long)time[0];
    //                intTime[0] = hours.intValue();
    //            }
    //            if (time[1] != null && time[1] instanceof Long) {
    //                Long min = (Long)time[1];
    //                intTime[1] = min.intValue();
    //            }
    //        }
    //        return intTime;
    //    }
    //    
    //    public int getMinutesForHourDouble(Double doubleValue) {
    //        int hours = doubleValue.intValue();
    //        doubleValue = doubleValue - hours;
    //        int minutes = 0;
    //        if (doubleValue != 0.0) {
    //            doubleValue *= 100;
    //            minutes = doubleValue.intValue() * 60 / 100;
    //        }
    //        minutes += hours * 60;
    //        return minutes;
    //    }
    //    
    //    public String fromDBtimeToString(Employeecontract ec, int hours, int minutes) {
    //        
    //        int trainingDays = 0;
    //        int trainingHours = 0;
    //        int trainingMinutes = 0;
    //        int restMinutes;
    //        
    //        int totalTrainingMinutes = hours * 60 + minutes;
    //        
    //        int dailyWorkingTimeMinutes = getMinutesForHourDouble(ec.getDailyWorkingTime());
    //        
    //        if (dailyWorkingTimeMinutes != 0) {
    //            trainingDays = totalTrainingMinutes / dailyWorkingTimeMinutes;
    //            restMinutes = totalTrainingMinutes % dailyWorkingTimeMinutes;
    //            trainingHours = restMinutes / 60;
    //            trainingMinutes = restMinutes % 60;
    //        }
    //        
    //        StringBuffer trainingString = new StringBuffer();
    //        if (trainingDays < 10) {
    //            trainingString.append(0);
    //        }
    //        trainingString.append(trainingDays);
    //        trainingString.append(':');
    //        if (trainingHours < 10) {
    //            trainingString.append(0);
    //        }
    //        trainingString.append(trainingHours);
    //        trainingString.append(':');
    //        if (trainingMinutes < 10) {
    //            trainingString.append(0);
    //        }
    //        trainingString.append(trainingMinutes);
    //        
    //        return trainingString.toString();
    //    }
    //    
    //    public String hoursMinToString(int[] time) {
    //        
    //        int trainingHours = time[0];
    //        int trainingMinutes = time[1];
    //        
    //        trainingHours += trainingMinutes / 60;
    //        trainingMinutes = trainingMinutes % 60;
    //        
    //        StringBuffer trainingString = new StringBuffer();
    //        
    //        if (trainingHours < 10) {
    //            trainingString.append(0);
    //        }
    //        trainingString.append(trainingHours);
    //        trainingString.append(':');
    //        if (trainingMinutes < 10) {
    //            trainingString.append(0);
    //        }
    //        trainingString.append(trainingMinutes);
    //        
    //        return trainingString.toString();
    //    }
}
