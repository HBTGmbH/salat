package org.tb.budget.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.domain.EmployeeCost;
import org.tb.budget.domain.EmployeeCostAssignment;
import org.tb.budget.domain.EmployeeCostAssignmentData;
import org.tb.budget.domain.EmployeeCostData;
import org.tb.budget.persistence.EmployeeCostAssignmentRepository;
import org.tb.budget.persistence.EmployeeCostRepository;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class EmployeeCostService {

    private final EmployeeCostRepository employeeCostRepository;
    private final EmployeeCostAssignmentRepository assignmentRepository;

    @Transactional(readOnly = true)
    public EmployeeCost getById(long id) {
        return employeeCostRepository.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_EMPLOYEE_COST_NOT_FOUND, id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeCost> getAll() {
        return employeeCostRepository.findAllByOrderByNameAscValidFromAsc();
    }

    @Transactional(readOnly = true)
    public List<EmployeeCostAssignment> getAllAssignments() {
        return assignmentRepository.findAllByOrderByEmployeeCostNameAscEmployeeSignAsc();
    }

    @Transactional(readOnly = true)
    public List<EmployeeCostAssignment> getAssignmentsByName(String employeeCostName) {
        return assignmentRepository.findByEmployeeCostName(employeeCostName);
    }

    /**
     * Fallback hierarchy: suborder-specific assignment → general assignment.
     * Returns the matching EmployeeCost active on the given date.
     */
    @Transactional(readOnly = true)
    public Optional<EmployeeCost> findEffectiveCost(String employeeSign, String suborderSign, LocalDate date) {
        if (suborderSign != null) {
            var assignments = assignmentRepository.findEffectiveSuborderSpecific(employeeSign, suborderSign, date);
            if (!assignments.isEmpty()) {
                return employeeCostRepository.findEffectiveByName(assignments.get(0).getEmployeeCostName(), date);
            }
        }
        var assignments = assignmentRepository.findEffectiveGeneral(employeeSign, date);
        if (!assignments.isEmpty()) {
            return employeeCostRepository.findEffectiveByName(assignments.get(0).getEmployeeCostName(), date);
        }
        return Optional.empty();
    }

    @Authorized(requiresManager = true)
    public EmployeeCost create(EmployeeCostData data) {
        var cost = new EmployeeCost();
        apply(cost, data);
        return employeeCostRepository.save(cost);
    }

    @Authorized(requiresManager = true)
    public void update(long id, EmployeeCostData data) {
        var cost = getById(id);
        apply(cost, data);
        employeeCostRepository.save(cost);
    }

    @Authorized(requiresManager = true)
    public void delete(long id) {
        employeeCostRepository.deleteById(id);
    }

    @Authorized(requiresManager = true)
    public EmployeeCostAssignment createAssignment(EmployeeCostAssignmentData data) {
        var assignment = new EmployeeCostAssignment();
        applyAssignment(assignment, data);
        return assignmentRepository.save(assignment);
    }

    @Authorized(requiresManager = true)
    public void updateAssignment(long id, EmployeeCostAssignmentData data) {
        var assignment = assignmentRepository.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_EMPLOYEE_COST_ASSIGNMENT_NOT_FOUND, id));
        applyAssignment(assignment, data);
        assignmentRepository.save(assignment);
    }

    @Authorized(requiresManager = true)
    public void deleteAssignment(long id) {
        assignmentRepository.deleteById(id);
    }

    private void apply(EmployeeCost cost, EmployeeCostData data) {
        cost.setName(data.name());
        cost.setCostCentsPerHour(data.costCentsPerHour());
        cost.setValidFrom(data.validFrom());
        cost.setValidUntil(data.validUntil() != null ? data.validUntil() : LocalDate.of(2999, 12, 31));
    }

    private void applyAssignment(EmployeeCostAssignment assignment, EmployeeCostAssignmentData data) {
        assignment.setEmployeeCostName(data.employeeCostName());
        assignment.setEmployeeSign(data.employeeSign());
        assignment.setSuborderSign(data.suborderSign());
        assignment.setValidFrom(data.validFrom());
        assignment.setValidUntil(data.validUntil() != null ? data.validUntil() : LocalDate.of(2999, 12, 31));
    }

}
