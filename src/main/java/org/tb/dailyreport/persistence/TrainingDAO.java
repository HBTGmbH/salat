package org.tb.dailyreport.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.dailyreport.domain.TrainingInformation;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;

@Component
@RequiredArgsConstructor
public class TrainingDAO {

    private final TrainingRepository trainingRepository;

    public List<TrainingInformation> getProjectTrainingTimesByDates(LocalDate begin, LocalDate end) {
        return trainingRepository.getProjectTrainingTimesByDates(begin, end);
    }

    public List<TrainingInformation> getCommonTrainingTimesByDates(LocalDate begin, LocalDate end, long customerorderId) {
        return trainingRepository.getCommonTrainingTimesByDates(begin, end, customerorderId);
    }

    public Optional<TrainingInformation> getProjectTrainingTimesByDatesAndEmployeeContractId(Employeecontract employeecontract, LocalDate begin, LocalDate end) {
        long ecId = employeecontract.getId();
        return trainingRepository.getProjectTrainingTimesByDatesAndEmployeeContractId(ecId, begin, end);
    }

    public Optional<TrainingInformation> getCommonTrainingTimesByDatesAndEmployeeContractId(Employeecontract employeecontract, LocalDate begin, LocalDate end, long customerorderId) {
        long ecId = employeecontract.getId();
        return trainingRepository.getCommonTrainingTimesByDatesAndEmployeeContractId(ecId, begin, end, customerorderId);
    }

}
