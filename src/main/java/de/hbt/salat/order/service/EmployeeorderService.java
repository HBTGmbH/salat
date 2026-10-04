package de.hbt.salat.order.service;

import static java.lang.Boolean.TRUE;
import static de.hbt.salat.common.exception.ErrorCode.EO_CONFLICT_RESOLUTION_GOT_VETO;
import static de.hbt.salat.common.exception.ServiceFeedbackMessage.error;
import static de.hbt.salat.common.util.DateUtils.today;
import static de.hbt.salat.order.command.GetTimereportMinutesCommandEvent.OrderType.EMPLOYEE;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.exception.VetoedException;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.event.EmployeecontractChangedEvent;
import de.hbt.salat.employee.event.EmployeecontractConflictResolutionEvent;
import de.hbt.salat.employee.event.EmployeecontractDeleteEvent;
import de.hbt.salat.employee.event.EmployeecontractUpdateEvent;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.command.GetTimereportMinutesCommandEvent;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.EmployeeorderListItemDTO;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.event.EmployeeorderConflictResolutionEvent;
import de.hbt.salat.order.event.EmployeeorderDeleteEvent;
import de.hbt.salat.order.event.EmployeeorderUpdateEvent;
import de.hbt.salat.order.event.SuborderDeleteEvent;
import de.hbt.salat.order.event.SuborderUpdateEvent;
import de.hbt.salat.order.persistence.EmployeeorderDAO;
import de.hbt.salat.order.persistence.EmployeeorderRepository;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class EmployeeorderService {

  private final ApplicationEventPublisher eventPublisher;
  private final CommandPublisher commandPublisher;
  private final EmployeeorderDAO employeeorderDAO;
  private final SuborderService suborderService;
  private final EmployeeorderRepository employeeorderRepository;
  private final EmployeecontractService employeecontractService;
  private final SpecialOrders specialOrders;

  /**
   * Which of the given suborders the contract may book on the day: those with an employee order valid
   * then — the condition of the booking form. For the command palette's target "Buchen auf …"
   * (#1157); the caller decides whether the day is still open for writing.
   */
  @Transactional(readOnly = true)
  public Set<Long> getBookableSuborderIds(long employeecontractId, Collection<Long> suborderIds, LocalDate date) {
    if (suborderIds.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(employeeorderRepository.findBookableSuborderIds(employeecontractId, suborderIds, date));
  }

  /**
   * An employee order on a yearly vacation suborder that comes without an entitlement gets the
   * calculated one, as the automatic creation gives it (#1341); one a manager entered stays.
   */
  @Authorized(requiresManager = true)
  public void create(Employeeorder employeeorder) {
    if (isVacationYear(employeeorder.getSuborder()) && hasNoDebit(employeeorder)) {
      applyVacationEntitlement(employeeorder);
    }
    createOrUpdate(employeeorder, employeeorder.getFromDate(), employeeorder.getUntilDate());
  }

  @Authorized(requiresManager = true)
  public void update(Employeeorder employeeorder) {
    createOrUpdate(employeeorder, employeeorder.getFromDate(), employeeorder.getUntilDate());
  }

  private void generateMissingStandardOrders(long employeecontractId) {
    Employeecontract contract = employeecontractService.getEmployeecontractById(employeecontractId);
    List<Employeecontract> futureContracts = employeecontractService.getFutureContracts(employeecontractId);
    var contracts = new HashSet<Employeecontract>(futureContracts);
    contracts.add(contract);

    for(Employeecontract employeecontract : contracts) {
      generateMissingStandardOrders(employeecontract);
    }
  }

  private void generateMissingStandardOrders(Employeecontract employeecontract) {
    if(employeecontract.getFreelancer() == TRUE) return;

    List<Suborder> standardSuborders = suborderService.getStandardSuborders();
    if (standardSuborders != null && !standardSuborders.isEmpty()) {
      // test if employeeorder exists
      for (Suborder suborder : standardSuborders) {

        var contractValidity = employeecontract.getValidity();
        var orderValidity = suborder.getValidity();
        var effectiveValidity = contractValidity.intersection(orderValidity);

        // check if effective validity has at least a single day - otherwise creation makes no sense - skip it!
        if(effectiveValidity == null) {
          continue;
        }

        // check if effective validity is not in the past (before date of accepted time reports) - else SKIP IT!!!
        var acceptanceDate = employeecontract.getReportAcceptanceDate();
        if(acceptanceDate != null && effectiveValidity.isBefore(acceptanceDate)) {
          continue;
        }

        boolean employeeorderPresent = employeeorderDAO.getEmployeeorderCount(employeecontract.getId(), suborder.getId()) > 0;
        if (!employeeorderPresent) {

          // skip vacation orders that do not match the contract
          var vacationYear = isVacationYear(suborder);
          if (vacationYear && !contractValidity.overlaps(vacationYearOf(suborder))) {
            continue; // skip creation
          }

          Employeeorder employeeorder = new Employeeorder();
          employeeorder.setFromDate(effectiveValidity.getFrom());
          employeeorder.setUntilDate(effectiveValidity.getUntil());
          employeeorder.setEmployeecontract(employeecontract);
          employeeorder.setSign(" ");
          employeeorder.setSuborder(suborder);

          if (vacationYear) {
            applyVacationEntitlement(employeeorder);
          }

          createOrUpdate(employeeorder, effectiveValidity.getFrom(), effectiveValidity.getUntil());
          log.info(
              "Created standard order for order {} and employee {} and contract {}.",
              suborder.getCompleteOrderSign(),
              employeecontract.getEmployee().getSign(),
              employeecontract.getId()
          );
        }
      }
    }
  }

  @Authorized(requiresManager = true)
  public void deleteEmployeeorderById(long employeeOrderId) {
    var employeeorder = employeeorderDAO.getEmployeeorderById(employeeOrderId);
    var event = new EmployeeorderDeleteEvent(employeeOrderId);
    try {
      eventPublisher.publishEvent(event);
    } catch(VetoedException e) {
      // adding context to the veto to make it easier to understand the complete picture
      var allMessages = new ArrayList<ServiceFeedbackMessage>();
      allMessages.add(error(
          ErrorCode.EO_DELETE_GOT_VETO,
          employeeorder.getSuborder().getCompleteOrderSign(),
          employeeorder.getEmployeecontract().getEmployee().getSign()
      ));
      allMessages.addAll(e.getMessages());
      event.veto(allMessages);
    }
    employeeorderRepository.deleteById(employeeOrderId);
  }

  @EventListener
  void onEmployeecontractUpdate(EmployeecontractUpdateEvent event) {
    var employeecontract = event.getDomainObject();
    var newValidity = employeecontract.getValidity();

    // adjust employeeorders
    List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrdersByEmployeeContractId(employeecontract.getId());
    for (Employeeorder employeeorder : employeeorders) {
      var existingValidity = employeeorder.getValidity();
      if(!existingValidity.overlaps(newValidity)) {
        deleteEmployeeorderById(employeeorder.getId());
        continue;
      }
      var resultingValidity = resultingValidity(employeeorder, newValidity);
      if(resultingValidity.equals(existingValidity)) {
        continue;
      }
      createOrUpdate(employeeorder, resultingValidity.getFrom(), resultingValidity.getUntil());
      // ensure the vacation budget matches the period the order now covers
      if (isVacationYear(employeeorder.getSuborder())) {
        applyVacationEntitlement(employeeorder);
        createOrUpdate(employeeorder, employeeorder.getFromDate(), employeeorder.getUntilDate());
      }
    }
  }

  /**
   * Ein Standardauftrag (Urlaub, Krankheit, Fortbildung) spannt genau Vertrag &cap; Unterauftrag —
   * sein Ende ist das Minimum aus Vertragsende und Ende des Unterauftrags, sein Beginn der spätere
   * der beiden Anfänge. So legt ihn {@link #generateMissingStandardOrders(Employeecontract)} an,
   * und so folgt er der Gültigkeit des Vertrags in beide Richtungen.
   * <p>
   * Bis #565 wurde nur gekürzt, weil die Bedingung {@code !newValidity.contains(existingValidity)}
   * lautete — die eine Verlängerung nie erfüllt. Der Urlaubsauftrag endete danach weiter am alten
   * Vertragsende und behielt das anteilige Soll des kürzeren Vertrags. Aufgefallen ist das im
   * Urlaubskonto: für einen Auftrag, dessen Gültigkeit vor heute endet, zeigt es statt des
   * Anspruchs den gebuchten Urlaub — also in aller Regel 0.
   * <p>
   * Jeder andere Mitarbeiterauftrag ist eine Zusage auf einen ausgehandelten Zeitraum. Ihn an einer
   * Vertragsänderung mitwachsen zu lassen, erteilte eine Buchungsberechtigung, die niemand vergeben
   * hat — er wird deshalb weiterhin nur gekürzt.
   */
  private LocalDateRange resultingValidity(Employeeorder employeeorder, LocalDateRange contractValidity) {
    if(TRUE.equals(employeeorder.getSuborder().getStandard())) {
      var standardValidity = contractValidity.intersection(employeeorder.getSuborder().getValidity());
      if(standardValidity != null) {
        return standardValidity;
      }
    }
    return employeeorder.getValidity().intersection(contractValidity);
  }

  @EventListener
  void onEmployeecontractConflictResolution(EmployeecontractConflictResolutionEvent event) {
    var updatingEmployeecontract = event.getUpdatingEmployeecontract();
    var conflictingEmployeecontract = event.getConflictingEmployeecontract();

    var conflictingOrders = employeeorderDAO.getEmployeeOrdersByEmployeeContractId(conflictingEmployeecontract.getId())
        .stream()
        .filter(eo -> eo.getValidity().overlaps(updatingEmployeecontract.getValidity()))
        .toList();

    for (Employeeorder conflictingOrder : conflictingOrders) {
      resolveConflict(conflictingOrder, updatingEmployeecontract, conflictingEmployeecontract, event);
    }
  }

  private void resolveConflict(Employeeorder conflictingOrder, Employeecontract updatingEmployeecontract,
      Employeecontract conflictingEmployeecontract, EmployeecontractConflictResolutionEvent originEvent) {

    originEvent.addLog("Alter Mitarbeiterauftrag muss in seiner Gültigkeit verkürzt werden %s (%s). ".formatted(conflictingOrder.getSuborder().getCompleteOrderSign(), conflictingOrder.getValidity()));

    var newOrder = new Employeeorder();
    // start not earlier than the new contract
    newOrder.setFromDate(DateUtils.max(updatingEmployeecontract.getValidFrom(), conflictingOrder.getFromDate()));
    newOrder.setUntilDate(DateUtils.min(updatingEmployeecontract.getValidUntil(), conflictingOrder.getUntilDate()));
    newOrder.setEmployeecontract(updatingEmployeecontract);
    newOrder.setSuborder(conflictingOrder.getSuborder());
    newOrder.setSign(conflictingOrder.getSign());
    newOrder.setDebithours(conflictingOrder.getDebithours());
    newOrder.setDebithoursunit(conflictingOrder.getDebithoursunit());

    var mergedOrder = mergeWithExisting(newOrder);

    // confliciting should end with the old contract
    conflictingOrder.setFromDate(DateUtils.max(conflictingEmployeecontract.getValidFrom(), conflictingOrder.getFromDate()));
    conflictingOrder.setUntilDate(DateUtils.min(conflictingEmployeecontract.getValidUntil(), conflictingOrder.getUntilDate()));

    // save orders to ensure id is set before resolving conflicts (other parts in this software rely on this)
    employeeorderRepository.save(mergedOrder);
    employeeorderRepository.save(conflictingOrder);

    originEvent.addLog("Neuen Mitarbeiterauftrag angelegt %s (%s)".formatted(mergedOrder.getSuborder().getCompleteOrderSign(), mergedOrder.getValidity()));
    originEvent.addLog("Alten Mitarbeiterauftrag angepasst %s (%s)".formatted(conflictingOrder.getSuborder().getCompleteOrderSign(), conflictingOrder.getValidity()));

    var event = new EmployeeorderConflictResolutionEvent(mergedOrder, conflictingOrder);
    try {
      eventPublisher.publishEvent(event);
      event.getEventLog().forEach(originEvent::addLog);
    } catch(VetoedException e) {
      // adding context to the veto to make it easier to understand the complete picture
      var allMessages = new ArrayList<ServiceFeedbackMessage>();
      allMessages.add(error(
          EO_CONFLICT_RESOLUTION_GOT_VETO,
          mergedOrder.getSign()
      ));
      allMessages.addAll(e.getMessages());
      event.veto(allMessages);
    }

    // take care of nonsense
    if(!conflictingOrder.getValidity().isValid()) {
      deleteEmployeeorderById(conflictingOrder.getId());
      log.info(
          "Deleted conflicting order {} for employee {} and contract {} because it is no longer valid.",
          conflictingOrder.getSuborder().getCompleteOrderSign(),
          conflictingOrder.getEmployeecontract().getEmployee().getSign(),
          conflictingOrder.getEmployeecontract().getId()
      );
      originEvent.addLog("Nicht mehr benötigten alten Mitarbeiterauftrag gelöscht %s (%s) ".formatted(conflictingOrder.getSuborder().getCompleteOrderSign(), conflictingOrder.getValidity()));
    }
  }

  private Employeeorder mergeWithExisting(Employeeorder newOrder) {
    var existingOrder = employeeorderRepository.findAllByEmployeecontractId(newOrder.getEmployeecontract().getId())
        .stream().filter(existing -> existing.getSuborder().getId().equals(newOrder.getSuborder().getId()))
        .filter(existing -> existing.getValidity().isConnected(newOrder.getValidity()))
        .findFirst();
    return existingOrder.map(existing -> {
      var newValidity = existing.getValidity().plus(newOrder.getValidity());
      existing.setFromDate(newValidity.getFrom());
      existing.setUntilDate(newValidity.getUntil());
      return existing;
    }).orElse(newOrder);
  }

  /** The form leaves the debit empty as zero (→ {@code EmployeeorderController}). */
  private static boolean hasNoDebit(Employeeorder employeeorder) {
    var debithours = employeeorder.getDebithours();
    return debithours == null || debithours.isZero();
  }

  /** A yearly suborder of the configured vacation order (→ {@link SpecialOrders#isVacationYear}). */
  private boolean isVacationYear(Suborder suborder) {
    return suborder != null && specialOrders.isVacationYear(suborder.getCustomerorder().getId(), suborder.getId());
  }

  /**
   * The year a yearly vacation suborder grants the entitlement of: the year it begins (#1341). Its
   * sign carries no meaning — it used to be parsed as the year, and a suborder named otherwise broke
   * every contract change of the people on it.
   */
  private static Year vacationYearOf(Suborder suborder) {
    return Year.from(suborder.getFromDate());
  }

  /**
   * The one rule bound to the configured vacation order (#1341): the debit of an employee order on a
   * yearly suborder is calculated, not entered. It is the effective vacation entitlement of the
   * contract in the year the suborder begins ({@link Employeecontract#getEffectiveVacationEntitlement}):
   * vacation days times the daily working time, cut pro rata where the contract does not cover the
   * year, as total time.
   *
   * <p>The same rule applies when the order is created automatically, when a manager creates it
   * without a debit, and when a change of the contract moves its validity. A manager may overwrite
   * the debit in the form; the next contract change calculates it again and overwrites that value —
   * there is no marker for a value set by hand.
   */
  private void applyVacationEntitlement(Employeeorder employeeorder) {
    var year = vacationYearOf(employeeorder.getSuborder());
    employeeorder.setDebithours(employeecontractService.getEffectiveVacationEntitlement(
        employeeorder.getEmployeecontract().getId(), year));
    employeeorder.setDebithoursunit(GlobalConstants.DEBITHOURS_UNIT_TOTALTIME);
  }

  /**
   * The entitlement an employee order of the contract on the suborder would get — what the form
   * proposes for a yearly vacation suborder (#1341). Empty for any other suborder.
   */
  @Transactional(readOnly = true)
  public Optional<Duration> getCalculatedVacationEntitlement(long employeecontractId, long suborderId) {
    var suborder = suborderService.getSuborderById(suborderId);
    if (!isVacationYear(suborder)) {
      return Optional.empty();
    }
    return Optional.of(employeecontractService.getEffectiveVacationEntitlement(employeecontractId,
        vacationYearOf(suborder)));
  }

  /** Whether the debit of an employee order on the suborder is calculated (→ {@link #applyVacationEntitlement}). */
  @Transactional(readOnly = true)
  public boolean hasCalculatedVacationEntitlement(long suborderId) {
    return isVacationYear(suborderService.getSuborderById(suborderId));
  }

  public List<Employeeorder> getEmployeeordersByFilters(Boolean showInactive, String filter, Long employeeContractId, Long customerOrderId, Long suborderId, Boolean showHidden) {
    return employeeorderDAO.getEmployeeordersByFilters(showInactive, filter, employeeContractId, null, customerOrderId, suborderId, showHidden);
  }

  public List<EmployeeorderListItemDTO> getEmployeeorderListItemsByFilters(Boolean showInactive, String filter,
      Long employeeContractId, Long customerId, Long customerOrderId, Long suborderId, boolean showActualHours, Boolean showHidden) {
    return employeeorderDAO.getEmployeeordersByFilters(showInactive, filter, employeeContractId, customerId, customerOrderId, suborderId, showHidden)
        .stream()
        .map(eo -> {
          Duration duration = showActualHours ? getTotalDuration(eo.getId()) : null;
          Duration difference = null;
          if (showActualHours && duration != null
              && eo.getDebithours() != null && eo.getDebithours().toMinutes() > 0
              && (eo.getDebithoursunit() == null || eo.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)) {
            difference = eo.getDebithours().minus(duration);
          }
          return new EmployeeorderListItemDTO(
              eo.getId(),
              eo.getCurrentlyValid(),
              eo.getFitsToSuperiorObjects(),
              eo.getSuborder().isHide() || eo.getSuborder().getCustomerorder().getHide(),
              eo.getSuborder().getCustomerorder().getCustomer().getShortname(),
              eo.getSuborder().getCustomerorder().getCustomer().getName(),
              eo.getEmployeecontract().getEmployee().getName(),
              eo.getSuborder().getCompleteOrderSign(),
              eo.getSuborder().getCompleteOrderDescription(true, false),
              eo.getFromDate(),
              eo.getUntilDate(),
              eo.getDebithours(),
              eo.getDebithoursunit(),
              duration,
              difference
          );
        })
        .toList();
  }

  public List<Employeeorder> getEmployeeOrdersByEmployeeContractIdAndSuborderId(long employeeContractId,
      long suborderId) {
    return employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(employeeContractId, suborderId);
  }

  public Employeeorder getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(long employeecontractId,
      long suborderId, LocalDate date) {
    return employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(employeecontractId, suborderId, date);
  }

  public Employeeorder getEmployeeorderForEmployeecontractValidAt(long employeecontractId, long suborderId, LocalDate validAt) {
    return employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(employeecontractId, suborderId, validAt);
  }

  public List<Employeeorder> getEmployeeordersForEmployeecontractAndValidAt(long employeecontractId, LocalDate validAt) {
    return employeeorderDAO.getEmployeeordersByEmployeeContractIdAndValidAt(employeecontractId, validAt);
  }

  public List<Employeeorder> getEmployeeordersByCustomerorderIdAndEmployeeContractId(long customerorderId, long employeeContractId) {
    return employeeorderDAO.getEmployeeordersByOrderIdAndEmployeeContractId(customerorderId, employeeContractId);
  }

  public List<Employeeorder> getEmployeeOrderByEmployeeContractIdAndSuborderIdAndValidAt(long employeeContractId,
      long suborderSignId, LocalDate validAt) {
    return employeeorderDAO
        .getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate2(employeeContractId, suborderSignId, validAt)
        .stream()
        .filter(eo -> eo.isValidAt(validAt))
        .toList();
  }

  public Employeeorder getEmployeeorderById(Long employeeOrderId) {
    return employeeorderDAO.getEmployeeorderById(employeeOrderId);
  }

  /**
   * The contract's employee orders on the configured vacation order valid today, empty while the role
   * is off (#1341).
   */
  public List<Employeeorder> getVacationEmployeeOrders(long employeecontractId) {
    var vacationId = specialOrders.getVacationCustomerorderId();
    return vacationId == null ? List.of()
        : employeeorderDAO.getVacationEmployeeOrdersByEmployeeContractIdAndDate(employeecontractId, vacationId, today());
  }

  /** Like {@link #getVacationEmployeeOrders(long)}, overlapping the range. */
  public List<Employeeorder> getVacationEmployeeOrders(long employeecontractId, final LocalDateRange range) {
    var vacationId = specialOrders.getVacationCustomerorderId();
    return vacationId == null ? List.of()
        : employeeorderDAO.getVacationEmployeeOrders(employeecontractId, vacationId, range);
  }

  public List<Employeeorder> getAllEmployeeOrders() {
    return employeeorderDAO.getEmployeeorders();
  }

  @EventListener
  void onEmployeecontractDelete(EmployeecontractDeleteEvent event) {
    var employeeorders = employeeorderDAO.getEmployeeOrdersByEmployeeContractId(event.getId());
    for (Employeeorder employeeorder : employeeorders) {
      deleteEmployeeorderById(employeeorder.getId());
    }
  }

  @EventListener
  void onSuborderUpdate(SuborderUpdateEvent event) {
    var suborder = event.getDomainObject();
    var newValidity = suborder.getValidity();

    // adjust employeeorders
    List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrdersBySuborderId(suborder.getId());
    for (Employeeorder employeeorder : employeeorders) {
      var existingValidity = employeeorder.getValidity();
      var updating = existingValidity.overlaps(newValidity);
      if(updating) {
        adjustValidity(employeeorder.getId(), newValidity);
      } else {
        deleteEmployeeorderById(employeeorder.getId());
      }
    }
  }

  @EventListener
  void onSuborderDelete(SuborderDeleteEvent event) {
    var employeeorders = employeeorderDAO.getEmployeeOrdersBySuborderId(event.getId());
    for (Employeeorder employeeorder : employeeorders) {
      deleteEmployeeorderById(employeeorder.getId());
    }
  }

  @EventListener
  void onEmployeecontractChanged(EmployeecontractChangedEvent event) {
    generateMissingStandardOrders(event.getEmployeecontractId());
  }

  public Duration getTotalDuration(long employeeorderId) {
    var command = GetTimereportMinutesCommandEvent.builder()
        .orderType(EMPLOYEE)
        .orderIds(List.of(employeeorderId))
        .build();
    commandPublisher.publish(command);
    return command.getResult().getOrDefault(employeeorderId, Duration.ZERO);
  }

  private void adjustValidity(long employeeorderId, LocalDateRange newValidity) {
    var employeeorder = employeeorderDAO.getEmployeeorderById(employeeorderId);
    var existingValidity = employeeorder.getValidity();
    var resultingValidity = existingValidity.intersection(newValidity);
    var newFrom = resultingValidity.getFrom();
    var newUntil = resultingValidity.getUntil();
    createOrUpdate(employeeorder, newFrom, newUntil);
  }

  // TODO improve method arguments to reflect all details of an employee order
  private void createOrUpdate(Employeeorder employeeorder, LocalDate from, LocalDate until) {
    employeeorder.setFromDate(from);
    employeeorder.setUntilDate(until);

    if(!employeeorder.isNew()) {
      EmployeeorderUpdateEvent event = new EmployeeorderUpdateEvent(employeeorder);
      try {
        eventPublisher.publishEvent(event);
      } catch(VetoedException e) {
        // adding context to the veto to make it easier to understand the complete picture
        var allMessages = new ArrayList<ServiceFeedbackMessage>();
        allMessages.add(error(
            ErrorCode.EO_UPDATE_GOT_VETO,
            employeeorder.getSuborder().getCompleteOrderSign(),
            employeeorder.getEmployeecontract().getEmployee().getSign()
        ));
        allMessages.addAll(e.getMessages());
        event.veto(allMessages);
      }
    }

    employeeorderRepository.save(employeeorder);
  }

  /**
   * The employee orders of the contract that are valid on the day and whose suborder carries exactly
   * this complete order sign, e.g. {@code 4711/01} (#1142). Import and REST API name a booking's order
   * that way when they leave out its id.
   *
   * <p>The sign is compared as it is, apart from surrounding blanks: {@code 4711/1} does not find
   * {@code 4711/10}. Neither the database nor the forms keep a complete sign unique, and a contract can
   * hold several orders on the same suborder, so this answers with all matches; the caller decides what
   * none or several mean.
   */
  public List<Employeeorder> getEmployeeordersByCompleteOrderSignValidAt(long employeecontractId, String completeOrderSign,
      LocalDate date) {
    var sign = completeOrderSign.strip();
    return employeeorderDAO.getEmployeeordersByEmployeeContractIdAndValidAt(employeecontractId, date).stream()
        // the DAO leaves out orders that ended before the day, but not those that begin after it
        .filter(order -> order.isValidAt(date))
        .filter(order -> sign.equals(order.getSuborder().getCompleteOrderSign()))
        .toList();
  }

}
