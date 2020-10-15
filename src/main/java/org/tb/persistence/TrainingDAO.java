package org.tb.persistence;

import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.Employeecontract;

import java.util.Date;
import java.util.List;

@Component
public class TrainingDAO extends AbstractDAO {

    @Autowired
    public TrainingDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> getProjectTrainingTimesByDates(EmployeecontractDAO employeecontractDAO, Date begin, Date end) {
        return (List<Object[]>) getSession()
                .createQuery("select t.employeecontract.id, sum(t.durationhours), sum(t.durationminutes) from Timereport t " +
                        "where t.employeecontract.freelancer=false and t.employeecontract.dailyWorkingTime>0 and " +
                        "t.referenceday.refdate >= ? and t.referenceday.refdate <= ?  and t.training = true " +
                        "group by t.employeecontract.id")
                .setDate(0, begin).setDate(1, end).list();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> getCommonTrainingTimesByDates(EmployeecontractDAO employeecontractDAO, Date begin, Date end, long orderID) {
        return (List<Object[]>) getSession()
                .createQuery("select t.employeecontract.id, sum(t.durationhours), sum(t.durationminutes) from Timereport t " +
                        "where t.employeecontract.freelancer=false and t.employeecontract.dailyWorkingTime>0 and " +
                        "t.referenceday.refdate >= ? and t.referenceday.refdate <= ?  and t.suborder.customerorder.id=?" +
                        " and  t.suborder.sign not like 'x_%'  " +
                        "group by t.employeecontract.id")
                .setDate(0, begin).setDate(1, end).setLong(2, orderID).list();
    }

    public Object[] getProjectTrainingTimesByDatesAndEmployeeContractId(Employeecontract employeecontract, Date begin, Date end) {
        long ecId = employeecontract.getId();
        return (Object[]) getSession()
                .createQuery("select sum(t.durationhours), sum(t.durationminutes) from Timereport t where t.referenceday.refdate >= ? and t.referenceday.refdate <= ? and t.employeecontract.id = ? and t.training = true")
                .setDate(0, begin).setDate(1, end).setLong(2, ecId).uniqueResult();
    }

    public Object[] getCommonTrainingTimesByDatesAndEmployeeContractId(Employeecontract employeecontract, Date begin, Date end, long orderID) {
        long ecId = employeecontract.getId();
        return (Object[]) getSession()
                .createQuery("select sum(t.durationhours), sum(t.durationminutes) from Timereport t where t.referenceday.refdate >= ? " +
                        "and t.referenceday.refdate <= ? and t.employeecontract.id = ? and t.suborder.customerorder.id=? and  t.suborder.sign not like 'x_%'")
                .setDate(0, begin).setDate(1, end).setLong(2, ecId).setLong(3, orderID).uniqueResult();
    }

}
