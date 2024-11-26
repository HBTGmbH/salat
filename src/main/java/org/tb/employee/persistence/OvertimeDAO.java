package org.tb.employee.persistence;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.employee.domain.Overtime;

@Component
@RequiredArgsConstructor
public class OvertimeDAO {

    private final OvertimeRepository overtimeRepository;

    /**
     * @return Returns a list with all {@link Overtime}s associated to the given employeeContractId.
     */
    public List<Overtime> getOvertimesByEmployeeContractId(long employeeContractId) {
        return overtimeRepository.findAllByEmployeecontractId(employeeContractId);
    }

    public void save(Overtime overtime) {
        overtimeRepository.save(overtime);
    }

    /**
     * Deletes the {@link Overtime} associated to the given id.
     * @return Returns true, if delete action was succesful.
     */
    public boolean deleteOvertimeById(long overtimeId) {
        overtimeRepository.deleteById(overtimeId);
        return true;
    }


}
