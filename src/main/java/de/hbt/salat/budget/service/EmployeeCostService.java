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
import de.hbt.salat.budget.domain.CostCategory;
import de.hbt.salat.budget.domain.EmployeeCost;
import de.hbt.salat.budget.domain.EmployeeCostAssignment;
import de.hbt.salat.budget.domain.EmployeeCostAssignmentData;
import de.hbt.salat.budget.domain.EmployeeCostCategory;
import de.hbt.salat.budget.domain.EmployeeCostData;
import de.hbt.salat.budget.domain.EmployeeCostLookup;
import de.hbt.salat.budget.persistence.CostCategoryRepository;
import de.hbt.salat.budget.persistence.EmployeeCostAssignmentRepository;
import de.hbt.salat.budget.persistence.EmployeeCostRepository;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.order.domain.CustomerorderOption;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class EmployeeCostService {

    private final EmployeeCostRepository employeeCostRepository;
    private final EmployeeCostAssignmentRepository assignmentRepository;
    private final CostCategoryRepository categoryRepository;
    private final EmployeeService employeeService;
    private final SuborderService suborderService;
    private final CustomerorderService customerorderService;

    @Transactional(readOnly = true)
    public EmployeeCost getById(long id) {
        return employeeCostRepository.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_EMPLOYEE_COST_NOT_FOUND, id));
    }

    /**
     * The cost categories with the employees currently or prospectively assigned to them (#954).
     *
     * <p>Every category is listed, also one without a rate period: an assignment left behind by a
     * deleted rate (#895) keeps its category, otherwise it could no longer be reached through the UI
     * at all. A category neither rates nor assignments refer to goes away with the last of them
     * ({@link #dropIfUnused}).
     *
     * <p>The signs are read off the people (#968), so they follow a rename by themselves. An
     * assignment the migration could not resolve costs nobody and names nobody here; the category
     * page lists and marks it.
     */
    @Transactional(readOnly = true)
    public List<EmployeeCostCategory> getCategories() {
        var assignments = assignmentRepository.findAllByOrderByCategoryNameAscIdAsc();
        var names = new TreeSet<String>();
        categoryRepository.findAllByOrderByNameAsc().forEach(category -> names.add(category.getName()));
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

    /**
     * The rate periods of one category, oldest first. Empty for a category without rate periods and for
     * a name no category carries. The category pages address a category by its name, which is unique
     * (#1209).
     */
    @Transactional(readOnly = true)
    public List<EmployeeCost> getByName(String name) {
        return categoryRepository.findByName(name)
            .map(category -> employeeCostRepository.findByCategoryIdOrderByValidFromAsc(category.getId()))
            .orElse(List.of());
    }

    /** Whether a category carries the name. */
    @Transactional(readOnly = true)
    public boolean categoryExists(String name) {
        return categoryRepository.existsByName(name);
    }

    @Transactional(readOnly = true)
    public List<EmployeeCostAssignment> getAllAssignments() {
        return assignmentRepository.findAllByOrderByCategoryNameAscIdAsc();
    }

    @Transactional(readOnly = true)
    public List<EmployeeCostAssignment> getAssignmentsByName(String employeeCostName) {
        return categoryRepository.findByName(employeeCostName)
            .map(category -> assignmentRepository.findByCategoryId(category.getId()))
            .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public EmployeeCostAssignment getAssignmentById(long id) {
        return assignmentRepository.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_EMPLOYEE_COST_ASSIGNMENT_NOT_FOUND, id));
    }

    /**
     * Category names offered in a select box: every category with a rate period, plus {@code keepName}
     * even if its category has none any more. Without that exception an assignment left behind by a
     * deleted rate would lose its category in the select and silently retarget itself on save.
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
            assignmentRepository.findAllByOrderByCategoryNameAscIdAsc(),
            employeeCostRepository.findAllByOrderByCategoryNameAscValidFromAsc());
    }

    /**
     * Fallback hierarchy: suborder-specific assignment → order-specific assignment → general assignment
     * (#1343), the same as {@link EmployeeCostLookup#findEffectiveCost}. Returns the matching
     * EmployeeCost active on the given date. The order type plays no part: a standby order costs what
     * the assignments say, like any other.
     *
     * @param customerorderId the order of the suborder; {@code null} skips the order step
     * @param suborderId      {@code null} skips the suborder step
     */
    @Transactional(readOnly = true)
    public Optional<EmployeeCost> findEffectiveCost(long employeeId, Long customerorderId, Long suborderId,
                                                    LocalDate date) {
        var assignments = List.<EmployeeCostAssignment>of();
        if (suborderId != null) {
            assignments = assignmentRepository.findEffectiveSuborderSpecific(employeeId, suborderId, date);
        }
        if (assignments.isEmpty() && customerorderId != null) {
            assignments = assignmentRepository.findEffectiveCustomerorderSpecific(employeeId, customerorderId, date);
        }
        if (assignments.isEmpty()) {
            assignments = assignmentRepository.findEffectiveGeneral(employeeId, date);
        }
        if (assignments.isEmpty()) {
            return Optional.empty();
        }
        return employeeCostRepository.findEffectiveByCategoryId(assignments.get(0).getCategory().getId(), date);
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
        categoryRepository.save(new CostCategory(name));
        return create(new EmployeeCostData(name, costCentsPerHour, DateUtils.today(), null));
    }

    /**
     * Renames a category (#954). Rate periods and assignments refer to it by id (#1209), so the rename
     * itself is this one row.
     *
     * <p>Renaming onto a name that already exists merges the two categories: rate periods and
     * assignments move over, and the renamed category goes away. That is intentional and only allowed
     * while the merged rate periods stay free of overlaps.
     */
    @Authorized(requiresManager = true)
    public void renameCategory(String oldName, String newName) {
        if (Objects.equals(oldName, newName)) {
            return;
        }
        var category = categoryRepository.findByName(oldName).orElse(null);
        if (category == null) {
            return;
        }
        var ranges = employeeCostRepository.findByCategoryIdOrderByValidFromAsc(category.getId()).stream()
            .map(EmployeeCostService::rangeOf).toList();
        renameOrMerge(category, newName, ranges);
    }

    /**
     * A new rate period of an existing category. A name no category carries is refused: the rate form
     * is reached from a category, and a stale or mistyped name must not make a new one (#1209).
     */
    @Authorized(requiresManager = true)
    public EmployeeCost create(EmployeeCostData data) {
        var category = categoryNamed(data.name());
        checkNoCostOverlap(category.getId(), data.validFrom(), endOfValidity(data.validUntil()), null);
        var cost = new EmployeeCost();
        cost.setCategory(category);
        apply(cost, data);
        return employeeCostRepository.save(cost);
    }

    /**
     * A rate period whose name changes renames its whole category — merging into an existing one,
     * like {@link #renameCategory(String, String)} — and takes its own new values along.
     */
    @Authorized(requiresManager = true)
    public void update(long id, EmployeeCostData data) {
        var cost = getById(id);
        var category = cost.getCategory();
        var newRange = new LocalDateRange(data.validFrom(), endOfValidity(data.validUntil()));
        if (Objects.equals(category.getName(), data.name())) {
            checkNoCostOverlap(category.getId(), newRange.getFrom(), newRange.getUntil(), id);
            apply(cost, data);
            employeeCostRepository.save(cost);
            return;
        }
        // The edited period contributes its new range, its siblings their stored ones.
        var ranges = employeeCostRepository.findByCategoryIdOrderByValidFromAsc(category.getId()).stream()
            .map(member -> Objects.equals(member.getId(), id) ? newRange : rangeOf(member))
            .toList();
        apply(cost, data);
        renameOrMerge(category, data.name(), ranges);
    }

    /**
     * Gives the category the new name, or — where another category carries it already — moves rate
     * periods and assignments over to that one and drops the renamed category. The merged periods are
     * checked for overlaps first.
     *
     * @param ranges the periods of the renamed category as they will be after the change
     */
    private void renameOrMerge(CostCategory category, String newName, List<LocalDateRange> ranges) {
        var target = categoryRepository.findByName(newName).orElse(null);
        checkNoOverlapAfterMerge(target, ranges);
        var costs = employeeCostRepository.findByCategoryIdOrderByValidFromAsc(category.getId());
        var assignments = assignmentRepository.findByCategoryId(category.getId());
        if (target == null) {
            category.setName(newName);
            categoryRepository.save(category);
        } else {
            costs.forEach(cost -> cost.setCategory(target));
            assignments.forEach(assignment -> assignment.setCategory(target));
        }
        employeeCostRepository.saveAll(costs);
        assignmentRepository.saveAll(assignments);
        if (target != null) {
            categoryRepository.delete(category);
        }
    }

    /**
     * A merge puts the moved periods next to those of the target category, so the merged set has to
     * stay free of overlaps — the same rule {@link #checkNoCostOverlap} enforces for a single record.
     * Without a target the periods are checked among themselves, because an edited one brings a new
     * range along.
     *
     * @param target the category merged into, {@code null} for a plain rename
     */
    private void checkNoOverlapAfterMerge(CostCategory target, List<LocalDateRange> incoming) {
        var ranges = new ArrayList<LocalDateRange>();
        if (target != null) {
            employeeCostRepository.findByCategoryIdOrderByValidFromAsc(target.getId())
                .forEach(cost -> ranges.add(rangeOf(cost)));
        }
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
     * Deleting is refused while any assignment refers to the category — including when a sibling
     * period keeps the category alive. Removing an outdated rate of a category still in use would
     * leave its period uncovered, and the bookings in that period would silently cost 0 EUR (#922).
     * Retarget the assignments first; editing them keeps their audit trail.
     */
    @Authorized(requiresManager = true)
    public void delete(long id) {
        var cost = getById(id);
        var category = cost.getCategory();
        var assignments = assignmentRepository.countByCategoryId(category.getId());
        if (assignments > 0) {
            throw new BusinessRuleException(ErrorCode.BU_EMPLOYEE_COST_HAS_ASSIGNMENTS, category.getName(), assignments);
        }
        employeeCostRepository.deleteById(id);
        dropIfUnused(category);
    }

    /**
     * A category nothing refers to any more goes away, as it did while it was only a name: the
     * overview listed a name as long as a rate period or an assignment carried it.
     */
    private void dropIfUnused(CostCategory category) {
        if (employeeCostRepository.countByCategoryId(category.getId()) == 0
            && assignmentRepository.countByCategoryId(category.getId()) == 0) {
            categoryRepository.delete(category);
        }
    }

    private CostCategory categoryNamed(String name) {
        return categoryRepository.findByName(name)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_EMPLOYEE_COST_NAME_UNKNOWN, name));
    }

    @Authorized(requiresManager = true)
    public EmployeeCostAssignment createAssignment(EmployeeCostAssignmentData data) {
        var employee = employeeOf(data);
        var customerorder = customerorderOf(data);
        var suborder = suborderOf(data);
        var category = categoryOf(data);
        checkNoAssignmentOverlap(employee.getId(), customerorderIdOf(customerorder), data.suborderId(),
            data.validFrom(), endOfValidity(data.validUntil()), null);
        var assignment = new EmployeeCostAssignment();
        applyAssignment(assignment, data, category, employee, customerorder, suborder);
        return assignmentRepository.save(assignment);
    }

    @Authorized(requiresManager = true)
    public void updateAssignment(long id, EmployeeCostAssignmentData data) {
        var employee = employeeOf(data);
        var customerorder = customerorderOf(data);
        var suborder = suborderOf(data);
        var category = categoryOf(data);
        var assignment = getAssignmentById(id);
        var previous = assignment.getCategory();
        checkNoAssignmentOverlap(employee.getId(), customerorderIdOf(customerorder), data.suborderId(),
            data.validFrom(), endOfValidity(data.validUntil()), id);
        applyAssignment(assignment, data, category, employee, customerorder, suborder);
        assignmentRepository.save(assignment);
        if (!previous.equals(category)) {
            dropIfUnused(previous);
        }
    }

    /**
     * The person the assignment is for. The foreign key would refuse an id nobody carries as well,
     * but only as a failed statement.
     */
    private Employee employeeOf(EmployeeCostAssignmentData data) {
        var employee = data.employeeId() == null ? null : employeeService.getEmployeeById(data.employeeId());
        if (employee == null) {
            throw new InvalidDataException(ErrorCode.EM_NOT_FOUND, data.employeeId());
        }
        return employee;
    }

    /**
     * The customer order of the assignment, by id (#1343) — {@code null} unless the assignment is for a
     * whole order. An id nothing answers to is refused, as for the suborder.
     *
     * <p>An assignment is for a suborder, for a whole order, or general. Where the form names both, the
     * suborder applies: the order only narrowed the choice of suborders, and an assignment to the order
     * would cover all its suborders anyway.
     */
    private CustomerorderOption customerorderOf(EmployeeCostAssignmentData data) {
        if (data.customerorderId() == null || data.suborderId() != null) {
            return null;
        }
        return customerorderService.getCustomerorderOptionsByIds(List.of(data.customerorderId())).stream()
            .findFirst()
            .orElseThrow(() -> new InvalidDataException(ErrorCode.CO_NOT_FOUND, data.customerorderId()));
    }

    /**
     * The suborder of the assignment, by id (#1205) — {@code null} unless the assignment is for a suborder. An id
     * nothing answers to is refused: the foreign key would do so too, but only as a failed statement.
     */
    private Suborder suborderOf(EmployeeCostAssignmentData data) {
        if (data.suborderId() == null) {
            return null;
        }
        var suborder = suborderService.getSuborderById(data.suborderId());
        if (suborder == null) {
            throw new InvalidDataException(ErrorCode.SO_NOT_FOUND, data.suborderId());
        }
        return suborder;
    }

    /**
     * The form names the cost category by its unique name, so it can be anything the request sends
     * (#958). The select of the form is no protection: a post with another value, or none at all,
     * reaches the same endpoint. An unknown name is refused here; the assignment itself then refers to
     * the category by id (#1209). Person, order and suborder are referenced by id (#968, #1343, #1205)
     * and checked by {@link #employeeOf}, {@link #customerorderOf} and {@link #suborderOf}.
     *
     * <p>A category without rate periods still counts. An assignment left behind by a deleted cost
     * rate (#895) therefore stays editable, which is the way to move it onto a rate that exists.
     */
    private CostCategory categoryOf(EmployeeCostAssignmentData data) {
        return categoryNamed(data.employeeCostName());
    }

    @Authorized(requiresManager = true)
    public void deleteAssignment(long id) {
        var category = assignmentRepository.findById(id).map(EmployeeCostAssignment::getCategory).orElse(null);
        assignmentRepository.deleteById(id);
        if (category != null) {
            dropIfUnused(category);
        }
    }

    private void checkNoCostOverlap(long categoryId, LocalDate from, LocalDate until, Long excludeId) {
        if (!employeeCostRepository.findOverlapping(categoryId, from, until, excludeId).isEmpty()) {
            throw new BusinessRuleException(ErrorCode.BU_EMPLOYEE_COST_OVERLAP);
        }
    }

    private static Long customerorderIdOf(CustomerorderOption customerorder) {
        return customerorder == null ? null : customerorder.id();
    }

    /** Overlaps count within one step of the resolution only: the same suborder, the same order, or general (#1343). */
    private void checkNoAssignmentOverlap(long employeeId, Long customerorderId, Long suborderId, LocalDate from,
                                          LocalDate until, Long excludeId) {
        if (!assignmentRepository.findOverlapping(employeeId, customerorderId, suborderId, from, until, excludeId)
            .isEmpty()) {
            throw new BusinessRuleException(ErrorCode.BU_EMPLOYEE_COST_ASSIGNMENT_OVERLAP);
        }
    }

    /** The values of a rate period; its category is set by the caller. */
    private void apply(EmployeeCost cost, EmployeeCostData data) {
        cost.setCostCentsPerHour(data.costCentsPerHour());
        cost.setValidFrom(data.validFrom());
        cost.setValidUntil(endOfValidity(data.validUntil()));
    }

    /**
     * @param customerorder the chosen customer order, {@code null} unless the assignment is for a whole order
     * @param suborder      the chosen suborder, {@code null} unless the assignment is for a suborder
     */
    private void applyAssignment(EmployeeCostAssignment assignment, EmployeeCostAssignmentData data,
                                 CostCategory category, Employee employee, CustomerorderOption customerorder,
                                 Suborder suborder) {
        assignment.setCategory(category);
        assignment.setEmployeeId(employee.getId());
        assignment.setCustomerorderId(customerorder == null ? null : customerorder.id());
        assignment.setSuborderId(suborder == null ? null : suborder.getId());
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
