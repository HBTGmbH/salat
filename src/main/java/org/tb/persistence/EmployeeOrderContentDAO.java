package org.tb.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Employeeordercontent;

@Component
@RequiredArgsConstructor
public class EmployeeOrderContentDAO {

    private final EmployeeorderDAO employeeorderDAO;
    private final EmployeeordercontentRepository employeeordercontentRepository;

    /**
     * Gets the {@link Employeeordercontent} for the given id.
     */
    public Employeeordercontent getEmployeeOrderContentById(long id) {
        return employeeordercontentRepository.findById(id).orElse(null);
    }

    /**
     * Calls {@link EmployeeOrderContentDAO#save(Employeeordercontent, Employee)} with {@link Employee} = null.
     */
    public void save(Employeeordercontent eoc) {
        save(eoc, null);
    }

    /**
     * Saves the given {@link Employeeordercontent} and sets creation-/update-user and creation-/update-date.
     */
    public void save(Employeeordercontent eoc, Employee loginEmployee) {
        employeeordercontentRepository.save(eoc);
    }

    /**
     * Deletes the given {@link Employeeordercontent}.
     * Important note: {@link Employeeordercontent}s are deleted, when the associated {@link Employeeorder} is deleted.
     * An {@link Employeeordercontent} must not be deleted without deleting the associated {@link Employeeorder}!
     * The {@link Employeeorder} must be deleted first!
     *
     * @param eocId The id of the {@link Employeeordercontent} to delete
     */
    public boolean deleteEmployeeOrderContentById(long eocId) {
        boolean deleteOk = false;

        // check if related employee order still exists
        Employeeorder employeeorder = employeeorderDAO.getEmployeeOrderByContentId(eocId);
        if (employeeorder == null) {
            deleteOk = true;
        }

        if (deleteOk) {
            employeeordercontentRepository.deleteById(eocId);
        }

        return deleteOk;
    }

}
