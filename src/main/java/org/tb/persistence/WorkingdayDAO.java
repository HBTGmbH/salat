package org.tb.persistence;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.bdom.Workingday;

@Component
@RequiredArgsConstructor
public class WorkingdayDAO {

    private final WorkingdayRepository workingdayRepository;

    public Workingday getWorkingdayByDateAndEmployeeContractId(LocalDate refdate, long employeeContractId) {
        return workingdayRepository.findByRefdayAndEmployeecontractId(refdate, employeeContractId).orElse(null);
    }

    public void save(Workingday wd) {
        workingdayRepository.save(wd);
    }

}
