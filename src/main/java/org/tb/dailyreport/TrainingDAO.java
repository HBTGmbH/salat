package org.tb.dailyreport;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.employee.Employeecontract;
import org.tb.employee.EmployeecontractDAO;

@Component
@RequiredArgsConstructor
public class TrainingDAO {

    private final TrainingRepository trainingRepository;

    public List<Object[]> getProjectTrainingTimesByDates(EmployeecontractDAO employeecontractDAO, LocalDate begin, LocalDate end) {
        return trainingRepository.getProjectTrainingTimesByDates(begin, end);
    }

    public List<Object[]> getCommonTrainingTimesByDates(EmployeecontractDAO employeecontractDAO, LocalDate begin, LocalDate end, long customerorderId) {
        return trainingRepository.getCommonTrainingTimesByDates(begin, end, customerorderId);
    }

    public Object[] getProjectTrainingTimesByDatesAndEmployeeContractId(Employeecontract employeecontract, LocalDate begin, LocalDate end) {
        long ecId = employeecontract.getId();
        return trainingRepository
            .getProjectTrainingTimesByDatesAndEmployeeContractId(ecId, begin, end)
            .orElse(new Object[] { 0L, 0L });
    }

    public Object[] getCommonTrainingTimesByDatesAndEmployeeContractId(Employeecontract employeecontract, LocalDate begin, LocalDate end, long customerorderId) {
        long ecId = employeecontract.getId();
        return trainingRepository
            .getCommonTrainingTimesByDatesAndEmployeeContractId(ecId, begin, end, customerorderId)
            .orElse(new Object[] { 0L, 0L });
    }

}
