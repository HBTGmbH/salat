package org.tb.persistence;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.bdom.Employee;
import org.tb.bdom.Overtime;

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

    /**
     * Calls {@link OvertimeDAO#save(Overtime, Employee)} with the given {@link Overtime} and null for the {@link Employee}.
     */
    public void save(Overtime overtime) {
        save(overtime, null);
    }

    /**
     * Saves the given overtime and sets creation-user and creation-date.
     */
    public void save(Overtime overtime, Employee loginEmployee) {
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
