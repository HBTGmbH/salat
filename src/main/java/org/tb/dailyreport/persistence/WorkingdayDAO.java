package org.tb.dailyreport.persistence;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.dailyreport.domain.Workingday;

@Component
@RequiredArgsConstructor
public class WorkingdayDAO {

    private final WorkingdayRepository workingdayRepository;

    public Workingday getWorkingdayByDateAndEmployeeContractId(LocalDate refdate, long employeeContractId) {
        return workingdayRepository.findByRefdayAndEmployeecontractId(refdate, employeeContractId).orElse(null);
    }

    public List<Workingday> getWorkingdaysByEmployeeContractId(long employeeContractId, LocalDate begin, LocalDate end) {
        return workingdayRepository.findAllByEmployeecontractIdAndReferencedayBetween(employeeContractId, begin, end);
    }

}
