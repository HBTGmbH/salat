package org.tb.persistence;

import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.bdom.Workingday;

@Component
@RequiredArgsConstructor
public class WorkingdayDAO {

    private final WorkingdayRepository workingdayRepository;

    public Workingday getWorkingdayByDateAndEmployeeContractId(Date refdate, long employeeContractId) {
        return workingdayRepository.findByRefdayAndEmployeecontractId(refdate, employeeContractId).orElse(null);
    }

    public void save(Workingday wd) {
        workingdayRepository.save(wd);
    }

}
