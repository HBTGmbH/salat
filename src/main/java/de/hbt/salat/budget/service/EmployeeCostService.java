package de.hbt.salat.budget.service;

import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toSet;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.budget.domain.EmployeeCost;
import de.hbt.salat.budget.domain.EmployeeCostAssignment;
import de.hbt.salat.budget.domain.EmployeeCostAssignmentData;
import de.hbt.salat.budget.domain.EmployeeCostCategory;
import de.hbt.salat.budget.domain.EmployeeCostData;
import de.hbt.salat.budget.domain.EmployeeCostLookup;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.budget.persistence.EmployeeCostAssignmentRepository;
import de.hbt.salat.budget.persistence.EmployeeCostRepository;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.order.service.SuborderService;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class EmployeeCostService {

    private final EmployeeCostRepository employeeCostRepository;
    private final EmployeeCostAssignmentRepository assignmentRepository;
    private final EmployeeService employeeService;
    private final SuborderService suborderService;

    @Transactional(readOnly = true)
    public EmployeeCost getById(long id) {
        return employeeCostRepository.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_EMPLOYEE_COST_NOT_FOUND, id));
    }

    /**
     * The cost categories with the employees currently or prospectively assigned to them (#954).
     *
     * <p>A category is a name. It shows up here as soon as a cost record or an assignment carries the
     * name — an assignment left behind by a deleted rate (#895) keeps its category listed, otherwise
     * it could no longer be reached through the UI at all.
     *
     * <p>The signs are read off the people (#968), so they follow a rename by themselves. An
     * assignment the migration could not resolve costs nobody and names nobody here; the category
     * page lists and marks it.
     */
    @Transactional(readOnly = true)
    public List<EmployeeCostCategory> getCategories() {
        var assignments = assignmentRepository.findAllByOrderByEmployeeCostNameAscIdAsc();
        var names = new TreeSet<>(employeeCostRepository.findDistinctNames());
        assignments.forEach(assignment -> names.add(assignment.getEmployeeCostName()));
        var signs = employeeService.getSignsByIds(assignments.stream()
            .map(EmployeeCostAssignment::getEmployeeId)
            .filter(Objects::nonNull)
            .collect(toSet()));

        var today = DateUtils.today();
        return names.stream()
            .map(name -> new EmployeeCostCategory(name, assignments.stream()
                .filter(assignment -> assignment.getEmployeeCostName().equals(name))
                .filter(assignment -> !assignment.getValidUntil().isBefore(today))
                .map(assignment -> signs.get(assignment.getEmployeeId()))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList()))
            .toList();
    }

    /** The rate periods of one category, oldest first. Empty for a category only assignments name. */
    @Transactional(readOnly = true)
    public List<EmployeeCost> getByName(String name) {
        return employeeCostRepository.findByNameOrderByValidFromAsc(name);
    }

    /** Whether the name is taken — by a cost record or by an assignment still referencing it. */
    @Transactional(readOnly = true)
    public boolean categoryExists(String name) {
        return employeeCostRepository.findDistinctNames().contains(name)
            || assignmentRepository.countByEmployeeCostName(name) > 0;
    }

    @Transactional(readOnly = true)
    public List<EmployeeCostAssignment> getAllAssignments() {
        return assignmentRepository.findAllByOrderByEmployeeCostNameAscIdAsc();
    }

    @Transactional(readOnly = true)
    public List<EmployeeCostAssignment> getAssignmentsByName(String employeeCostName) {
        return assignmentRepository.findByEmployeeCostName(employeeCostName);
    }

    /**
     * Writes the new sign of a person into the sign column of their assignments (#966, #968). The
     * resolution goes by id and does not need it; the column is kept in step only for the views,
     * ETL definitions and reports that still join on it. Driven by the event of the employee
     * module; changing a sign takes a manager there just as the class-level authorization demands
     * one here.
     */
    public void followSignChange(long employeeId, String newSign) {
        assignmentRepository.updateEmployeeSign(employeeId, newSign);
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
            assignmentRepository.findAllByOrderByEmployeeCostNameAscIdAsc(),
            employeeCostRepository.findAllByOrderByNameAscValidFromAsc());
    }

    /**
     * Fallback hierarchy: suborder-specific assignment → general assignment.
     * Returns the matching EmployeeCost active on the given date.
     *
     * <p>A standby suborder ({@link OrderType#BEREITSCHAFT}) takes no general assignment: standby
     * is paid differently from the work the general rate stands for, so without an assignment of
     * its own it costs nothing (#463).
     */
    @Transactional(readOnly = true)
    public Optional<EmployeeCost> findEffectiveCost(long employeeId, String suborderSign,
                                                    OrderType orderType, LocalDate date) {
        if (suborderSign != null) {
            var assignments = assignmentRepository.findEffectiveSuborderSpecific(employeeId, suborderSign, date);
            if (!assignments.isEmpty()) {
                return employeeCostRepository.findEffectiveByName(assignments.get(0).getEmployeeCostName(), date);
            }
        }
        if (orderType == OrderType.BEREITSCHAFT) {
            return Optional.empty();
        }
        var assignments = assignmentRepository.findEffectiveGeneral(employeeId, date);
        if (!assignments.isEmpty()) {
            return employeeCostRepository.findEffectiveByName(assignments.get(0).getEmployeeCostName(), date);
        }
        return Optional.empty();
    }

    /**
     * Creates a category from its name and its first rate (#954). The validity is not part of the
     * input: the rate runs from today with an open end. Anything else — a rate that started earlier,
     * or a follow-up period — is entered afterwards on the category page, where the periods of the
     * category are visible and an overlap can be judged.
     */
    @Authorized(requiresManager = true)
    public EmployeeCost createCategory(String name, Integer costCentsPerHour) {
        if (categoryExists(name)) {
            throw new BusinessRuleException(ErrorCode.BU_EMPLOYEE_COST_NAME_EXISTS, name);
        }
        return create(new EmployeeCostData(name, costCentsPerHour, DateUtils.today(), null));
    }

    /**
     * Renames a whole category: every rate period carrying the name and every assignment referencing
     * it (#954). Same reasoning as {@link #renameCategory(EmployeeCost, EmployeeCostData)} — a
     * half-moved name resolves to no rate at all, and the affected bookings would silently cost
     * 0 EUR in controlling (#922).
     *
     * <p>Renaming onto a name that already exists merges the two categories. That is intentional and
     * only allowed while the merged rate periods stay free of overlaps.
     */
    @Authorized(requiresManager = true)
    public void renameCategory(String oldName, String newName) {
        if (Objects.equals(oldName, newName)) {
            return;
        }
        var group = employeeCostRepository.findByNameOrderByValidFromAsc(oldName);
        checkNoOverlapAfterMerge(newName, group.stream().map(EmployeeCostService::rangeOf).toList());

        group.forEach(cost -> cost.setName(newName));
        employeeCostRepository.saveAll(group);
        moveAssignments(oldName, newName);
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
        // The edited record contributes its new range, its siblings their stored ones.
        checkNoOverlapAfterMerge(newName, group.stream()
            .map(member -> Objects.equals(member.getId(), edited.getId())
                ? new LocalDateRange(data.validFrom(), endOfValidity(data.validUntil()))
                : rangeOf(member))
            .toList());

        for (var member : group) {
            if (Objects.equals(member.getId(), edited.getId())) {
                apply(member, data);
            } else {
                member.setName(newName);
            }
        }
        employeeCostRepository.saveAll(group);
        moveAssignments(oldName, newName);
    }

    private void moveAssignments(String oldName, String newName) {
        var assignments = assignmentRepository.findByEmployeeCostName(oldName);
        assignments.forEach(assignment -> assignment.setEmployeeCostName(newName));
        assignmentRepository.saveAll(assignments);
    }

    /**
     * A rename merges the moved periods into whatever already carries the target name, so the merged
     * set has to stay free of overlaps — the same rule {@link #checkNoCostOverlap} enforces for a
     * single record.
     */
    private void checkNoOverlapAfterMerge(String newName, List<LocalDateRange> incoming) {
        var ranges = new ArrayList<LocalDateRange>();
        employeeCostRepository.findByNameOrderByValidFromAsc(newName).forEach(cost -> ranges.add(rangeOf(cost)));
        ranges.addAll(incoming);
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
        var employee = employeeOf(data);
        checkReferences(data);
        checkNoAssignmentOverlap(employee.getId(), data.suborderSign(), data.validFrom(),
            endOfValidity(data.validUntil()), null);
        var assignment = new EmployeeCostAssignment();
        applyAssignment(assignment, data, employee);
        return assignmentRepository.save(assignment);
    }

    @Authorized(requiresManager = true)
    public void updateAssignment(long id, EmployeeCostAssignmentData data) {
        var employee = employeeOf(data);
        checkReferences(data);
        checkNoAssignmentOverlap(employee.getId(), data.suborderSign(), data.validFrom(),
            endOfValidity(data.validUntil()), id);
        var assignment = getAssignmentById(id);
        applyAssignment(assignment, data, employee);
        assignmentRepository.save(assignment);
    }

    /**
     * The person the assignment is for. The foreign key would refuse an id nobody carries as well,
     * but only as a failed statement; and the person is needed anyway, for the sign column that is
     * still written alongside the id (#968).
     */
    private Employee employeeOf(EmployeeCostAssignmentData data) {
        var employee = data.employeeId() == null ? null : employeeService.getEmployeeById(data.employeeId());
        if (employee == null) {
            throw new InvalidDataException(ErrorCode.EM_NOT_FOUND, data.employeeId());
        }
        return employee;
    }

    /**
     * An assignment names its suborder and its cost category by sign, so both can be anything the
     * request sends (#958). The select of the form is no protection: a post with other values, or
     * none at all, reaches the same endpoint. An unknown sign makes {@link #findEffectiveCost}
     * resolve nothing, and that shows up as work costing 0 EUR in controlling rather than as an
     * error — so it is refused here. The person is referenced by id (#968) and checked by
     * {@link #employeeOf}.
     *
     * <p>The category is checked through {@link #categoryExists}, which counts a name that only
     * assignments still carry. An assignment left behind by a deleted cost rate (#895) therefore
     * stays editable, which is the way to move it onto a rate that exists.
     */
    private void checkReferences(EmployeeCostAssignmentData data) {
        // No suborder means the assignment applies regardless of suborder — nothing to check.
        if (data.suborderSign() != null
            && !suborderService.existsSuborderWithCompleteOrderSign(data.suborderSign())) {
            throw new InvalidDataException(ErrorCode.BU_SUBORDER_SIGN_UNKNOWN, data.suborderSign());
        }
        if (!categoryExists(data.employeeCostName())) {
            throw new InvalidDataException(ErrorCode.BU_EMPLOYEE_COST_NAME_UNKNOWN, data.employeeCostName());
        }
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

    private void checkNoAssignmentOverlap(long employeeId, String suborderSign, LocalDate from, LocalDate until, Long excludeId) {
        if (!assignmentRepository.findOverlapping(employeeId, suborderSign, from, until, excludeId).isEmpty()) {
            throw new BusinessRuleException(ErrorCode.BU_EMPLOYEE_COST_ASSIGNMENT_OVERLAP);
        }
    }

    private void apply(EmployeeCost cost, EmployeeCostData data) {
        cost.setName(data.name());
        cost.setCostCentsPerHour(data.costCentsPerHour());
        cost.setValidFrom(data.validFrom());
        cost.setValidUntil(endOfValidity(data.validUntil()));
    }

    private void applyAssignment(EmployeeCostAssignment assignment, EmployeeCostAssignmentData data,
                                 Employee employee) {
        assignment.setEmployeeCostName(data.employeeCostName());
        assignment.setEmployeeId(employee.getId());
        assignment.setEmployeeSign(employee.getSign());
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
