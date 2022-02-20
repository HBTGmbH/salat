package org.tb.persistence;

import com.google.common.collect.Lists;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Vacation;

@Component
@RequiredArgsConstructor
public class VacationDAO {

    private final VacationRepository vacationRepository;

    /**
     * Saves the given Vacation
     */
    public void save(Vacation va) {
        vacationRepository.save(va);
    }

    /**
     * Get a list of all Vacations.
     *
     * @return List<Vacation>
     */
    public List<Vacation> getVacations() {
        return Lists.newArrayList(vacationRepository.findAll());
    }

    /**
     * Sets up a new Vacation for given year/ec.
     */
    public Vacation setNewVacation(Employeecontract ec, int year) {
        Vacation va = new Vacation();
        va.setEmployeecontract(ec);
        va.setYear(year);
        va.setEntitlement(GlobalConstants.VACATION_PER_YEAR);
        va.setUsed(0);
        save(va);
        return va;
    }

    /**
     * Deletes the given Vacation.
     */
    public boolean deleteVacationById(long vaId) {
        vacationRepository.deleteById(vaId);
        return true;
    }

}
