package org.tb.persistence;

import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.bdom.Employeecontract;

@Component
@RequiredArgsConstructor
public class TrainingDAO {

    private final TrainingRepository trainingRepository;

    public List<Object[]> getProjectTrainingTimesByDates(EmployeecontractDAO employeecontractDAO, Date begin, Date end) {
        return trainingRepository.getProjectTrainingTimesByDates(begin, end);
    }

    public List<Object[]> getCommonTrainingTimesByDates(EmployeecontractDAO employeecontractDAO, Date begin, Date end, long customerorderId) {
        return trainingRepository.getCommonTrainingTimesByDates(begin, end, customerorderId);
    }

    public Object[] getProjectTrainingTimesByDatesAndEmployeeContractId(Employeecontract employeecontract, Date begin, Date end) {
        long ecId = employeecontract.getId();
        return trainingRepository
            .getProjectTrainingTimesByDatesAndEmployeeContractId(ecId, begin, end)
            .orElse(new Object[] { 0L, 0L });
    }

    public Object[] getCommonTrainingTimesByDatesAndEmployeeContractId(Employeecontract employeecontract, Date begin, Date end, long customerorderId) {
        long ecId = employeecontract.getId();
        return trainingRepository
            .getCommonTrainingTimesByDatesAndEmployeeContractId(ecId, begin, end, customerorderId)
            .orElse(new Object[] { 0L, 0L });
    }

}
