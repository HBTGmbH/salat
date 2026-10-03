package de.hbt.salat.budget.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.budget.auth.BudgetAuthorization;
import de.hbt.salat.budget.domain.BudgetLevel;
import de.hbt.salat.budget.domain.BudgetPlanPresence;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.domain.OrderBudgetAdjustment;
import de.hbt.salat.budget.domain.OrderBudgetAdjustmentData;
import de.hbt.salat.budget.domain.OrderBudgetData;
import de.hbt.salat.budget.domain.OrderBudgetScopeEntry;
import de.hbt.salat.budget.domain.OrderBudgetScopeEntryData;
import de.hbt.salat.budget.domain.OrderPosition;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class OrderBudgetService {

    private final OrderBudgetRepository orderBudgetRepository;
    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final OrderPositions orderPositions;
    private final BudgetAuthorization budgetAuthorization;
    private final TimereportBudgetAssignmentService assignmentService;

    /**
     * Every caller goes through here, so this is where the customer order of the plan is checked —
     * the detail view as well as every write path. Managers pass unconditionally.
     */
    @Transactional(readOnly = true)
    public OrderBudget getById(long id) {
        var budget = orderBudgetRepository.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_BUDGET_NOT_FOUND, id));
        budgetAuthorization.checkAuthorized(budget);
        return budget;
    }

    @Transactional(readOnly = true)
    public List<OrderBudget> getAll() {
        return orderBudgetRepository.findAllByOrderByValidFromAscIdAsc();
    }

    @Transactional(readOnly = true)
    public List<OrderBudget> getAllActive() {
        return orderBudgetRepository.findAllActiveWithAdjustments();
    }

    /**
     * Which of the given customer orders have plans, each with whether an active one is among them
     * — the target "Budget" of the command palette (#1157) opens the list of plans, the inactive ones
     * included where no active one exists. Orders whose plans the current user may not see are left
     * out, like orders without any plan.
     */
    @Transactional(readOnly = true)
    public Map<String, Boolean> getPlanPresence(Collection<String> customerorderSigns) {
        var visible = customerorderSigns.stream()
            .filter(budgetAuthorization::isAuthorizedForCustomerorder)
            .toList();
        if (visible.isEmpty()) {
            return Map.of();
        }
        // the palette asks with the signs it shows; the plans are read by the id behind them (#1205)
        var signById = customerorderIdsBySign(visible).entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        if (signById.isEmpty()) {
            return Map.of();
        }
        return orderBudgetRepository.findPlanPresenceByCustomerorderIds(signById.keySet()).stream()
            .collect(Collectors.toMap(presence -> signById.get(presence.customerorderId()),
                BudgetPlanPresence::hasActivePlan));
    }

    /** All plans the current user may see, ordered like {@link #getAll()}. */
    @Transactional(readOnly = true)
    public List<OrderBudget> getAllVisible() {
        return filterAuthorized(getAll());
    }

    /** The visible plans of one customer order, optionally including the inactive ones. */
    @Transactional(readOnly = true)
    public List<OrderBudget> getVisibleByCustomerorderSign(String customerorderSign, boolean includeInactive) {
        budgetAuthorization.checkAuthorizedForCustomerorder(customerorderSign);
        var customerorder = customerorderService.getCustomerorderBySign(customerorderSign);
        if (customerorder == null) {
            return List.of();
        }
        return includeInactive
            ? orderBudgetRepository.findByCustomerorderId(customerorder.getId())
            : orderBudgetRepository.findByCustomerorderIdAndActive(customerorder.getId(), Boolean.TRUE);
    }

    /** The active plans the current user may see — the basis of the dashboard. */
    @Transactional(readOnly = true)
    public List<OrderBudget> getAllActiveVisible() {
        return filterAuthorized(getAllActive());
    }

    /**
     * The active plans the current user may see, restricted to the given customer order signs.
     * {@code null} means no restriction at all; an empty collection means nothing matches and is
     * answered without a query. The authorization filter still runs on top — a restriction narrows
     * the result, it never widens it.
     */
    @Transactional(readOnly = true)
    public List<OrderBudget> getAllActiveVisible(Collection<String> restrictToCustomerorderSigns) {
        if (restrictToCustomerorderSigns == null) {
            return filterAuthorized(orderBudgetRepository.findAllActiveWithAdjustments());
        }
        var ids = customerorderIdsBySign(restrictToCustomerorderSigns).values();
        if (ids.isEmpty()) {
            return List.of();
        }
        return filterAuthorized(orderBudgetRepository.findAllActiveWithAdjustmentsByCustomerorderIds(ids));
    }

    /** The ids behind the signs a filter or the palette asks with; a sign nobody carries is left out. */
    private Map<String, Long> customerorderIdsBySign(Collection<String> customerorderSigns) {
        if (customerorderSigns.isEmpty()) {
            return Map.of();
        }
        return customerorderService.getCustomerordersBySigns(List.copyOf(customerorderSigns)).stream()
            .collect(Collectors.toMap(Customerorder::getSign, Customerorder::getId, (a, b) -> a));
    }

    private List<OrderBudget> filterAuthorized(List<OrderBudget> budgets) {
        if (budgetAuthorization.seesAllCustomerorders()) {
            return budgets;
        }
        return budgets.stream().filter(budgetAuthorization::isAuthorized).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderBudget> getActiveByCustomerorderId(long customerorderId) {
        return orderBudgetRepository.findByCustomerorderIdAndActive(customerorderId, Boolean.TRUE);
    }

    /** Where the plan sits in the order tree — empty for a suborder that no longer exists (#1205). */
    @Transactional(readOnly = true)
    public Optional<OrderPosition> positionOf(OrderBudget budget) {
        return orderPositions.of(budget);
    }

    @Authorized(requiresManager = true)
    public OrderBudget create(OrderBudgetData data) {
        var scope = scopeOf(data);
        // Checked before apply, which does not know the id that has to be excluded from the search.
        checkLevelNotMixed(scope, data.validFrom(), data.validUntil(), data.active(), null);
        var budget = new OrderBudget();
        apply(budget, data, scope);
        return orderBudgetRepository.save(budget);
    }

    /**
     * Saves the plan and, when the edit changed what it covers, brings the assignments of its
     * bookings back in line (#974).
     *
     * <p>Without that, shortening a plan or moving its scope left assignments pointing at a plan
     * that no longer covers them — the controlling kept counting those bookings against it while the
     * dashboard did not, and the stored state contradicted what {@code BudgetResolver.isAssignable}
     * treats as given. Renaming a plan or moving its alert threshold cannot invalidate an
     * assignment, so those edits are not worth the queries.
     */
    @Authorized(requiresManager = true)
    public void update(long id, OrderBudgetData data) {
        var budget = getById(id);
        var scope = scopeOf(data);
        checkLevelNotMixed(scope, data.validFrom(), data.validUntil(), data.active(), id);
        var coverageBefore = coverageOf(budget);
        apply(budget, data, scope);
        orderBudgetRepository.save(budget);
        if (!coverageOf(budget).equals(coverageBefore)) {
            assignmentService.revalidateAssignmentsOf(id);
        }
    }

    /**
     * What a plan covers: the period it is valid in and the scope it applies to. Deactivating a plan
     * is deliberately not part of it — an inactive plan keeps its assignments, and the controlling
     * reports its bookings under the plan, marked as deactivated (#1217,
     * → {@code BudgetControllingService}).
     */
    private record Coverage(LocalDate validFrom, LocalDate validUntil, Long customerorderId, Long suborderId) {}

    private static Coverage coverageOf(OrderBudget budget) {
        return new Coverage(budget.getValidFrom(), budget.getValidUntil(), budget.getCustomerorderId(),
            budget.getSuborderId());
    }

    @Authorized(requiresManager = true)
    public void setActive(long id, boolean active) {
        var budget = getById(id);
        // Only active plans conflict, so activating one can create a conflict that saving it did not.
        if (active) {
            // a plan whose suborder no longer exists covers nothing and conflicts with nothing
            orderPositions.of(budget).ifPresent(position -> checkLevelNotMixed(
                new Scope(position, null, null), budget.getValidFrom(), budget.getValidUntil(), true, id));
        }
        budget.setActive(active);
        orderBudgetRepository.save(budget);
    }

    @Authorized(requiresManager = true)
    public void addAdjustment(long budgetId, OrderBudgetAdjustmentData data) {
        var budget = getById(budgetId);
        var adjustment = new OrderBudgetAdjustment();
        adjustment.setOrderBudget(budget);
        adjustment.setAmount(data.amount());
        adjustment.setEffective(data.effective());
        adjustment.setComment(data.comment());
        budget.getAdjustments().add(adjustment);
        orderBudgetRepository.save(budget);
    }

    @Authorized(requiresManager = true)
    public void removeAdjustment(long budgetId, long adjustmentId) {
        var budget = getById(budgetId);
        budget.getAdjustments().removeIf(a -> a.getId() != null && a.getId().equals(adjustmentId));
        orderBudgetRepository.save(budget);
    }

    @Authorized(requiresManager = true)
    public void updateAlertSentAt(long id, LocalDate alertSentAt) {
        var budget = getById(id);
        budget.setAlertSentAt(alertSentAt);
        orderBudgetRepository.save(budget);
    }

    @Authorized(requiresManager = true)
    public void addScopeEntry(long budgetId, OrderBudgetScopeEntryData data) {
        var budget = getById(budgetId);
        var entry = new OrderBudgetScopeEntry();
        entry.setOrderBudget(budget);
        entry.setRefdate(data.refdate());
        entry.setPercent(data.percent());
        entry.setComment(data.comment());
        budget.getScopeEntries().add(entry);
        orderBudgetRepository.save(budget);
    }

    @Authorized(requiresManager = true)
    public void removeScopeEntry(long budgetId, long entryId) {
        var budget = getById(budgetId);
        budget.getScopeEntries().removeIf(e -> e.getId() != null && e.getId().equals(entryId));
        orderBudgetRepository.save(budget);
    }

    /**
     * What the plan is about: the customer order and the suborder, read by id (#1205), and its
     * position in the tree. The suborder select lists the suborders of all customer orders, so an id
     * can be submitted that does not lie below the chosen order. Such a plan would cover nothing and
     * would silently behave as if it did not exist, so it is refused here. Any depth is allowed since
     * #1004 — which level a plan may sit on is decided by {@link #checkLevelNotMixed}, against the
     * plans already in force.
     */
    private Scope scopeOf(OrderBudgetData data) {
        var customerorder = data.customerorderId() == null
            ? null
            : customerorderService.getCustomerorderById(data.customerorderId());
        if (customerorder == null) {
            throw new InvalidDataException(ErrorCode.CO_NOT_FOUND, data.customerorderId());
        }
        if (data.suborderId() == null) {
            return new Scope(OrderPosition.orderWide(customerorder.getId()), customerorder, null);
        }
        var suborder = suborderService.getSuborderById(data.suborderId());
        if (suborder == null) {
            throw new InvalidDataException(ErrorCode.SO_NOT_FOUND, data.suborderId());
        }
        if (!customerorder.getId().equals(suborder.getCustomerorder().getId())) {
            throw new BusinessRuleException(ErrorCode.BU_SUBORDER_NOT_IN_ORDER);
        }
        return new Scope(OrderPosition.of(suborder), customerorder, suborder);
    }

    /** The scope of a plan being saved: its position, and the records the sign columns are written from. */
    private record Scope(OrderPosition position, Customerorder customerorder, Suborder suborder) {}

    private void apply(OrderBudget budget, OrderBudgetData data, Scope scope) {
        budget.setName(data.name());
        budget.setCustomerorderId(scope.customerorder().getId());
        // mirrors for the reports, written from the records (#1205)
        budget.setCustomerorderSign(scope.customerorder().getSign());
        budget.setSuborderId(scope.suborder() == null ? null : scope.suborder().getId());
        budget.setSuborderSign(scope.suborder() == null ? null : scope.suborder().getCompleteOrderSign());
        budget.setValidFrom(data.validFrom());
        budget.setValidUntil(data.validUntil());
        budget.setActive(Boolean.TRUE.equals(data.active()));
        budget.setAlertThresholdPercent(data.alertThresholdPercent());
        budget.setProgressMode(data.progressMode());
    }

    /**
     * All active plans of a customer order that are valid at the same time sit on the same level
     * (#1004): either the order as a whole — level 0 — or one and the same suborder level. Two plans
     * on different levels of one branch would cover the same booking, and "how much is budgeted
     * here" would have no defined answer. The rule generalises the one from #905: back then the only
     * levels were 0 and 1, and the check read {@code orderWide} against {@code orderWide}.
     *
     * <p>Overlapping plans of the <em>same</em> level are allowed since #914 — several plans on the
     * same suborder included. The ban existed only because a booking's plan was derived from
     * (suborder, date) and an overlap made that ambiguous; the explicit assignment (#908) and the
     * switched evaluation (#913) decide it instead. Businesswise the overlap is the normal case: a
     * follow-up order starts before the running budget ends. Two plans of the same level in
     * different branches are no overlap at all.
     *
     * <p>Changing the level stays possible as soon as the periods do not overlap — order-wide until
     * the end of the year, per suborder from January.
     *
     * <p>Only active plans take part; an inactive one may stay on as an archive, and the controlling
     * gives it a section of its own (#1217). Activating one therefore has to check again.
     */
    private void checkLevelNotMixed(Scope scope, LocalDate validFrom, LocalDate validUntil,
                                    boolean active, Long excludeId) {
        if (!active || validFrom == null || validUntil == null ) {
            return;
        }
        var level = scope.position().level();
        for (var other : orderBudgetRepository.findActiveOverlapping(
                scope.position().customerorderId(), validFrom, validUntil, excludeId)) {
            var otherPosition = orderPositions.of(other);
            if (otherPosition.isEmpty()) {
                continue; // covers nothing, so it cannot cover anything twice
            }
            var otherLevel = otherPosition.get().level();
            if (level != otherLevel) {
                // Naming the plan that stands in the way is the whole point of the message: the
                // period to move is the one of that plan, not of the one being saved. Both levels
                // are named as well — without them the message says that something is mixed, but
                // not what with what.
                throw new BusinessRuleException(ErrorCode.BU_BUDGET_LEVEL_MIXED,
                    other.getName(), other.getValidFrom(), other.getValidUntil(), level, otherLevel);
            }
        }
    }

    /**
     * Which level the customer order is budgeted on today: as a whole, on a suborder level, or not
     * at all. Well defined because mixing levels is what {@link #checkLevelNotMixed} prevents.
     */
    @Transactional(readOnly = true)
    public BudgetLevel currentLevel(long customerorderId) {
        var today = DateUtils.today();
        return orderBudgetRepository.findByCustomerorderIdAndActive(customerorderId, Boolean.TRUE).stream()
            .filter(b -> !today.isBefore(b.getValidFrom()) && !today.isAfter(b.getValidUntil()))
            .map(orderPositions::of)
            .flatMap(Optional::stream)
            .map(BudgetLevel::of)
            .findFirst()
            .orElse(BudgetLevel.NONE);
    }

}
