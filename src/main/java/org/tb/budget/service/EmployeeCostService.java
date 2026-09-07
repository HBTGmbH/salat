package org.tb.budget.service;

import static java.util.Comparator.naturalOrder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.domain.EmployeeCost;
import org.tb.budget.domain.EmployeeCostAssignment;
import org.tb.budget.domain.EmployeeCostAssignmentData;
import org.tb.budget.domain.EmployeeCostData;
import org.tb.budget.domain.EmployeeCostLookup;
import org.tb.budget.persistence.EmployeeCostAssignmentRepository;
import org.tb.budget.persistence.EmployeeCostRepository;
import org.tb.common.LocalDateRange;
import org.tb.common.exception.BusinessRuleException;
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

    @Transactional(readOnly = true)
    public EmployeeCostAssignment getAssignmentById(long id) {
        return assignmentRepository.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_EMPLOYEE_COST_ASSIGNMENT_NOT_FOUND, id));
    }

    /**
     * Category names offered in a select box: every name in use, plus {@code keepName} even if no
     * cost record carries it any more. Without that exception an assignment left behind by a deleted
     * category would lose its name in the select and silently retarget itself on save.
     */
    @Transactional(readOnly = true)
    public List<String> getSelectableCostNames(String keepName) {
        var names = new ArrayList<>(employeeCostRepository.findDistinctNames());
        if (keepName != null && !keepName.isBlank() && !names.contains(keepName)) {
            names.add(keepName);
            names.sort(naturalOrder());
        }
        return names;
    }

    /**
     * Loads all assignments and costs into an in-memory lookup. Use this instead of
     * {@link #findEffectiveCost} whenever costs are resolved for more than a handful of reports —
     * per-report resolution costs two statements per report.
     */
    @Transactional(readOnly = true)
    public EmployeeCostLookup lookup() {
        return EmployeeCostLookup.of(
            assignmentRepository.findAllByOrderByEmployeeCostNameAscEmployeeSignAsc(),
            employeeCostRepository.findAllByOrderByNameAscValidFromAsc());
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
        checkNoCostOverlap(data.name(), data.validFrom(), endOfValidity(data.validUntil()), null);
        var cost = new EmployeeCost();
        apply(cost, data);
        return employeeCostRepository.save(cost);
    }

    @Authorized(requiresManager = true)
    public void update(long id, EmployeeCostData data) {
        var cost = getById(id);
        if (Objects.equals(cost.getName(), data.name())) {
            checkNoCostOverlap(data.name(), data.validFrom(), endOfValidity(data.validUntil()), id);
            apply(cost, data);
            employeeCostRepository.save(cost);
            return;
        }
        renameCategory(cost, data);
    }

    /**
     * Renaming carries the whole name group and every assignment referencing it along.
     *
     * <p>Assignments reference their cost by name, and several cost records share one name to model a
     * rate that changed over time. Renaming the edited record alone would leave both the sibling
     * records and the assignments on the old name, so {@code findEffectiveCost} would stop resolving
     * — the affected bookings would fall back to 0 EUR in controlling without any error (#922).
     */
    private void renameCategory(EmployeeCost edited, EmployeeCostData data) {
        var oldName = edited.getName();
        var newName = data.name();
        var group = employeeCostRepository.findByNameOrderByValidFromAsc(oldName);
        checkRenamedGroupHasNoOverlap(group, edited.getId(), data, newName);

        for (var member : group) {
            if (Objects.equals(member.getId(), edited.getId())) {
                apply(member, data);
            } else {
                member.setName(newName);
            }
        }
        employeeCostRepository.saveAll(group);

        var assignments = assignmentRepository.findByEmployeeCostName(oldName);
        assignments.forEach(assignment -> assignment.setEmployeeCostName(newName));
        assignmentRepository.saveAll(assignments);
    }

    /**
     * A rename merges the group into whatever already carries the target name, so the merged set has
     * to stay free of overlaps — the same rule {@link #checkNoCostOverlap} enforces for a single
     * record. The edited record contributes its new range, its siblings their stored ones.
     */
    private void checkRenamedGroupHasNoOverlap(List<EmployeeCost> group, Long editedId,
                                               EmployeeCostData data, String newName) {
        var ranges = new ArrayList<LocalDateRange>();
        employeeCostRepository.findByNameOrderByValidFromAsc(newName).forEach(cost -> ranges.add(rangeOf(cost)));
        for (var member : group) {
            ranges.add(Objects.equals(member.getId(), editedId)
                ? new LocalDateRange(data.validFrom(), endOfValidity(data.validUntil()))
                : rangeOf(member));
        }
        for (int i = 0; i < ranges.size(); i++) {
            for (int j = i + 1; j < ranges.size(); j++) {
                if (ranges.get(i).overlaps(ranges.get(j))) {
                    throw new BusinessRuleException(ErrorCode.BU_EMPLOYEE_COST_OVERLAP);
                }
            }
        }
    }

    /**
     * Deleting is refused while any assignment references the name — including when a sibling record
     * keeps the name alive. Removing an outdated rate of a category still in use would leave its
     * period uncovered, and the bookings in that period would silently cost 0 EUR (#922). Retarget
     * the assignments first; editing them keeps their audit trail.
     */
    @Authorized(requiresManager = true)
    public void delete(long id) {
        var cost = getById(id);
        var assignments = assignmentRepository.countByEmployeeCostName(cost.getName());
        if (assignments > 0) {
            throw new BusinessRuleException(ErrorCode.BU_EMPLOYEE_COST_HAS_ASSIGNMENTS, cost.getName(), assignments);
        }
        employeeCostRepository.deleteById(id);
    }

    @Authorized(requiresManager = true)
    public EmployeeCostAssignment createAssignment(EmployeeCostAssignmentData data) {
        checkNoAssignmentOverlap(data.employeeSign(), data.suborderSign(), data.validFrom(),
            endOfValidity(data.validUntil()), null);
        var assignment = new EmployeeCostAssignment();
        applyAssignment(assignment, data);
        return assignmentRepository.save(assignment);
    }

    @Authorized(requiresManager = true)
    public void updateAssignment(long id, EmployeeCostAssignmentData data) {
        checkNoAssignmentOverlap(data.employeeSign(), data.suborderSign(), data.validFrom(),
            endOfValidity(data.validUntil()), id);
        var assignment = getAssignmentById(id);
        applyAssignment(assignment, data);
        assignmentRepository.save(assignment);
    }

    @Authorized(requiresManager = true)
    public void deleteAssignment(long id) {
        assignmentRepository.deleteById(id);
    }

    private void checkNoCostOverlap(String name, LocalDate from, LocalDate until, Long excludeId) {
        if (!employeeCostRepository.findOverlapping(name, from, until, excludeId).isEmpty()) {
            throw new BusinessRuleException(ErrorCode.BU_EMPLOYEE_COST_OVERLAP);
        }
    }

    private void checkNoAssignmentOverlap(String employeeSign, String suborderSign, LocalDate from, LocalDate until, Long excludeId) {
        if (!assignmentRepository.findOverlapping(employeeSign, suborderSign, from, until, excludeId).isEmpty()) {
            throw new BusinessRuleException(ErrorCode.BU_EMPLOYEE_COST_ASSIGNMENT_OVERLAP);
        }
    }

    private void apply(EmployeeCost cost, EmployeeCostData data) {
        cost.setName(data.name());
        cost.setCostCentsPerHour(data.costCentsPerHour());
        cost.setValidFrom(data.validFrom());
        cost.setValidUntil(endOfValidity(data.validUntil()));
    }

    private void applyAssignment(EmployeeCostAssignment assignment, EmployeeCostAssignmentData data) {
        assignment.setEmployeeCostName(data.employeeCostName());
        assignment.setEmployeeSign(data.employeeSign());
        assignment.setSuborderSign(data.suborderSign());
        assignment.setValidFrom(data.validFrom());
        assignment.setValidUntil(endOfValidity(data.validUntil()));
    }

    /** An open end is stored as the far future date, so the overlap queries can compare plainly. */
    private static LocalDate endOfValidity(LocalDate validUntil) {
        return validUntil != null ? validUntil : LocalDate.of(2999, 12, 31);
    }

    private static LocalDateRange rangeOf(EmployeeCost cost) {
        return new LocalDateRange(cost.getValidFrom(), cost.getValidUntil());
    }

}
