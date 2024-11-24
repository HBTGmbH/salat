package org.tb.employee.persistence;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Vacation;

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
    public void addNewVacation(Employeecontract employeecontract, int year, int vacationEntitlement) {
        var vacation = new Vacation();
        vacation.setEmployeecontract(employeecontract);
        vacation.setYear(year);
        vacation.setEntitlement(vacationEntitlement);
        vacation.setUsed(0);
        save(vacation);
        if(employeecontract.getVacations() == null) {
            employeecontract.setVacations(new ArrayList<>());
        } else {
            employeecontract.getVacations().stream()
                .filter(v -> v.getYear() == year)
                .findAny()
                .ifPresent((v) -> {
                    throw new IllegalStateException("vacation with already exists with year=" + year);
                });
        }
        employeecontract.getVacations().add(vacation);
    }

    /**
     * Deletes the given Vacation.
     */
    public boolean deleteVacationById(long vaId) {
        vacationRepository.deleteById(vaId);
        return true;
    }

}
