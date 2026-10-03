package de.hbt.salat.order.service;

import static java.util.Comparator.comparing;
import static java.util.function.Predicate.not;
import static de.hbt.salat.common.exception.ServiceFeedbackMessage.error;
import static de.hbt.salat.order.command.GetTimereportMinutesCommandEvent.OrderType.SUB;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.exception.VetoedException;
import de.hbt.salat.common.palette.PaletteQuery;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.common.util.DurationUtils;
import de.hbt.salat.common.util.SqlLikePattern;
import de.hbt.salat.order.command.GetTimereportMinutesCommandEvent;
import de.hbt.salat.order.domain.SuborderDTO;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SuborderSearchRow;
import de.hbt.salat.order.domain.SuborderSignRow;
import de.hbt.salat.order.event.CustomerorderDeleteEvent;
import de.hbt.salat.order.event.CustomerorderUpdateEvent;
import de.hbt.salat.order.event.SuborderDeleteEvent;
import de.hbt.salat.order.event.SuborderUpdateEvent;
import de.hbt.salat.order.persistence.SuborderDAO;
import de.hbt.salat.order.persistence.SuborderRepository;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class SuborderService {

  /** Deeper than any suborder tree in the data; guards the parent walk against a cycle. */
  private static final int MAX_SUBORDER_DEPTH = 20;

  private final ApplicationEventPublisher eventPublisher;
  private final CommandPublisher commandPublisher;
  private final SuborderDAO suborderDAO;
  private final SuborderRepository suborderRepository;
  private final CustomerorderService customerorderService;

  /**
   * The suborders the command palette considers for a query (#1157), hidden and ended ones last.
   * Whoever is not restricted sees every suborder on the list pages; {@code bookableForEmployeeId}
   * narrows the result to what that person may book today, for a user who sees no list.
   */
  @Transactional(readOnly = true)
  public List<SuborderSearchRow> getPaletteCandidates(PaletteQuery query, Long bookableForEmployeeId) {
    return suborderRepository.findPaletteCandidates(query.likeWord(0), query.likeWord(1),
        query.likeWord(2), bookableForEmployeeId, DateUtils.today(),
        PageRequest.of(0, PaletteQuery.CANDIDATE_LIMIT));
  }

  /**
   * The complete signs ({@link Suborder#getCompleteOrderSign()}, {@code ORDER/SUB/SUBSUB}) of the
   * given rows. The parent chains are read one level at a time for all rows together — the entity
   * would load them lazily, one query per suborder and level.
   */
  @Transactional(readOnly = true)
  public Map<Long, String> getCompleteOrderSigns(Collection<SuborderSearchRow> rows) {
    var known = new HashMap<Long, SuborderSignRow>();
    rows.forEach(row -> known.put(row.id(), new SuborderSignRow(row.id(), row.sign(), row.parentId())));
    for (int level = 0; level < MAX_SUBORDER_DEPTH; level++) {
      var missing = known.values().stream()
          .map(SuborderSignRow::parentId)
          .filter(Objects::nonNull)
          .filter(not(known::containsKey))
          .collect(Collectors.toSet());
      if (missing.isEmpty()) break;
      var found = suborderRepository.findSignRows(missing);
      // a parent the database does not return would be asked for again on every level
      if (found.isEmpty()) break;
      found.forEach(row -> known.put(row.id(), row));
    }
    var signs = new HashMap<Long, String>();
    for (var row : rows) {
      var chain = new ArrayList<String>();
      for (var step = known.get(row.id()); step != null && chain.size() < MAX_SUBORDER_DEPTH;
          step = step.parentId() == null ? null : known.get(step.parentId())) {
        chain.addFirst(step.sign());
      }
      signs.put(row.id(), row.customerorderSign() + "/" + String.join("/", chain));
    }
    return signs;
  }

  public List<Suborder> getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(long employeecontractId, long customerorderId, LocalDate date) {
    return suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(employeecontractId, customerorderId, date);
  }

  @Transactional(readOnly = true)
  public List<SuborderSummary> getSuborderSummaries(long employeecontractId, long customerorderId, LocalDate date) {
    return suborderDAO
        .getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(employeecontractId, customerorderId, date)
        .stream()
        .map(s -> new SuborderSummary(
            s.getId(),
            s.getCompleteOrderSign(),
            s.getShortdescription(),
            Boolean.TRUE.equals(s.getCommentnecessary()),
            s.isTrainingFlag()))
        .toList();
  }

  @Authorized(requiresManager = true)
  public void create(SuborderDTO suborderData, Long customerorder) {
    createOrUpdate(null, suborderData, customerorder);
  }

  @Authorized(requiresManager = true)
  public void update(long suborderId, SuborderDTO suborderData, Long customerorderId) {
    createOrUpdate(suborderId, suborderData, customerorderId);
  }

  public List<Suborder> getStandardSuborders() {
    return suborderDAO.getStandardSuborders();
  }

  @Authorized(requiresManager = true)
  public Suborder toggleHide(long id) {
    Suborder so = suborderDAO.getSuborderById(id);
    if (so == null) throw new InvalidDataException(ErrorCode.SO_NOT_FOUND);
    so.setHide(!so.isHide());
    return suborderRepository.save(so);
  }

  private void createOrUpdate(Long soId, SuborderDTO data, Long customerorderId) {
    var customerorder = customerorderService.getCustomerorderById(customerorderId);
    Suborder so;
    if (soId != null) {
      // edited suborder
      so = suborderDAO.getSuborderById(soId);
    } else {
      // new suborder
      so = new Suborder();
    }
    so.acceptVisitor(suborder -> suborder.setCustomerorder(customerorder));
    so.setSign(data.sign());
    so.setSuborder_customer(data.suborder_customer());
    so.setDescription(data.description());
    so.setShortdescription(data.shortdescription());
    so.setInvoice(data.invoice());
    so.setStandard(data.standard());
    so.setCommentnecessary(data.commentnecessary());
    so.setFixedPrice(data.fixedPrice());
    so.setTrainingFlag(data.trainingFlag());
    so.setOrderType(data.orderType());

    if (data.validFrom() != null && !data.validFrom().trim().isEmpty()) {
      LocalDate fromDate = DateUtils.parseOrNull(data.validFrom());
      so.setFromDate(fromDate);
    } else {
      so.setFromDate(so.getCustomerorder().getFromDate());
    }
    if (data.validUntil() != null && !data.validUntil().trim().isEmpty()) {
      LocalDate untilDate = DateUtils.parseOrNull(data.validUntil());
      so.setUntilDate(untilDate);
    } else {
      so.setUntilDate(null);
    }

    if (data.debithours() == null
        || data.debithours().isEmpty()
        || DurationUtils.parseDuration(data.debithours()).isZero()) {
      so.setDebithours(Duration.ZERO);
      so.setDebithoursunit(null);
    } else {
      so.setDebithours(DurationUtils.parseDuration(data.debithours()));
      so.setDebithoursunit(data.debithoursunit());
    }

    so.setHide(data.hide());
    // parentId null is the top level (#1243); any other value names a suborder of the same order —
    // ids of orders and suborders are counted independently, so it is never read as the order's id
    Suborder parentOrderCandidate = null;
    if (data.parentId() != null) {
      parentOrderCandidate = suborderDAO.getSuborderById(data.parentId());
      if (parentOrderCandidate == null || !Objects.equals(parentOrderCandidate.getCustomerorder(), customerorder)) {
        throw new BusinessRuleException(ErrorCode.SO_PARENTORDER_INVALID);
      }
    }
    so.setParentorder(parentOrderCandidate);

    if (soId != null && parentOrderCandidate != null) {
      Suborder ancestor = parentOrderCandidate;
      while (ancestor != null) {
        if (ancestor.getId().equals(soId)) {
          throw new BusinessRuleException(ErrorCode.SO_PARENTORDER_CYCLE);
        }
        ancestor = ancestor.getParentorder();
      }
    }

    if(!so.isNew()) {
      var event = new SuborderUpdateEvent(so);
      try {
        eventPublisher.publishEvent(event);
      } catch(VetoedException e) {
        // adding context to the veto to make it easier to understand the complete picture
        var allMessages = new ArrayList<ServiceFeedbackMessage>();
        allMessages.add(error(ErrorCode.SO_UPDATE_GOT_VETO, so.getCompleteOrderSign()));
        allMessages.addAll(e.getMessages());
        event.veto(allMessages);
      }
    }

    suborderRepository.save(so);
  }

  @EventListener
  void onCustomerorderUpdate(CustomerorderUpdateEvent event) {
    var customerorder = event.getDomainObject();
    var newValidity = customerorder.getValidity();

    // adjust suborders
    List<Suborder> suborders = suborderDAO.getSubordersByCustomerorderId(customerorder.getId());
    for (Suborder suborder : suborders) {
      var existingValidity = suborder.getValidity();
      var updating = existingValidity.overlaps(newValidity);
      if(updating) {
        adjustValidity(suborder.getId(), newValidity);
      } else {
        deleteSuborderById(suborder.getId());
      }
    }
  }

  @EventListener
  void onCustomerorderDelete(CustomerorderDeleteEvent event) {
    var suborders = suborderDAO.getSubordersByCustomerorderId(event.getId());
    for (Suborder suborder : suborders) {
      deleteSuborderById(suborder.getId());
    }
  }

  public Duration getTotalDuration(List<Long> suborderIds) {
    var command = GetTimereportMinutesCommandEvent.builder()
        .orderType(SUB)
        .orderIds(suborderIds)
        .build();
    commandPublisher.publish(command);
    return command.getResult().values().stream().reduce(Duration::plus).orElse(Duration.ZERO);
  }

  private void adjustValidity(long suborderId, LocalDateRange newValidity) {
    var suborder = suborderDAO.getSuborderById(suborderId);
    var existingValidity = suborder.getValidity();
    var resultingValidity = existingValidity.intersection(newValidity);
    var newFrom = resultingValidity.getFrom();
    var newUntil = resultingValidity.getUntil();
    SuborderDTO data = createSuborderDTO(suborder, newFrom, newUntil);
    createOrUpdate(suborderId, data, suborder.getCustomerorder().getId());
  }

  private SuborderDTO createSuborderDTO(Suborder so, LocalDate newFrom, LocalDate newUntil) {
    Long parentId = so.getParentorder() != null ? so.getParentorder().getId() : null;
    String validUntil = newUntil != null ? DateUtils.format(newUntil) : "";
    String debithours = (so.getDebithours() != null && !so.getDebithours().isZero())
        ? DurationUtils.format(so.getDebithours()) : null;
    Byte debithoursunit = (so.getDebithours() != null && !so.getDebithours().isZero())
        ? so.getDebithoursunit() : null;
    return new SuborderDTO(
        so.getCustomerorder().getId(),
        so.getSign(),
        so.getDescription(),
        so.getShortdescription(),
        so.getSuborder_customer(),
        so.getInvoice(),
        so.getStandard(),
        so.getCommentnecessary(),
        so.getFixedPrice(),
        so.isTrainingFlag(),
        so.getOrderType(),
        DateUtils.format(newFrom),
        validUntil,
        debithours,
        debithoursunit,
        so.isHide(),
        parentId
    );
  }

  public List<Suborder> getSubordersByCustomerorderId(long customerorderId) {
    return suborderDAO.getSubordersByCustomerorderId(customerorderId).stream()
        .filter(
                not(Suborder::isHide))
        .toList();
  }

  /**
   * The suborders of a customer order for a select box, optionally including the inactive ones.
   *
   * <p>With {@code showInactive} off this drops what is inactive — the validity has ended before
   * today (→ {@link de.hbt.salat.common.Validity}). A suborder that only starts in the future is not
   * inactive but merely not yet active and stays in the list, or it gets entered a second time
   * (#1095, ADR-0029). {@code hide} is the other, unrelated criterion and is applied either way.
   */
  public List<Suborder> getSubordersByCustomerorderId(long customerorderId, boolean showInactive) {
    return suborderDAO.getSubordersByCustomerorderId(customerorderId).stream()
        .filter(suborder -> showInactive || suborder.getCurrentlyValid())
        .filter(not(Suborder::isHide))
        .toList();
  }

  public Suborder getSuborderById(long suborderId) {
    return suborderDAO.getSuborderById(suborderId);
  }

  /**
   * The suborders of the given ids in one statement (#964), hidden ones included.
   *
   * <p>Deliberately no {@code hide} filter: callers here start from bookings that already reference
   * the suborder, and dropping a hidden one would leave that booking without a sign and without a
   * rate. The other collective methods filter it out because they feed pickers.
   *
   * <p>An unknown id yields no row rather than an error — the caller knows what it asked for and
   * decides what a missing suborder means.
   */
  public List<Suborder> getSubordersByIds(Collection<Long> suborderIds) {
    if (suborderIds.isEmpty()) {
      return List.of();
    }
    return suborderDAO.getSubordersByIds(suborderIds);
  }

  /**
   * The complete order signs ({@code ORDER/01/02}) of the suborders with these ids, by id — what
   * another module needs to name suborders it refers to by id (#1212, ADR-0021). An id without a
   * suborder is missing.
   */
  @Transactional(readOnly = true)
  public Map<Long, String> getCompleteOrderSignsByIds(Collection<Long> suborderIds) {
    return getSubordersByIds(suborderIds).stream()
        .collect(Collectors.toMap(Suborder::getId, Suborder::getCompleteOrderSign));
  }

  public List<Suborder> getSubordersByEmployeeContractId(long employeeContractId) {
    return suborderDAO.getSubordersByEmployeeContractId(employeeContractId);
  }

  public List<Suborder> getAllSuborders() {
    return suborderDAO.getSuborders();
  }

  /**
   * The suborders of the given customer orders, hidden ones included — for lists whose rows name
   * their suborder by its complete order sign and need its description next to it (#952).
   *
   * <p>The complete order sign is derived rather than stored, so it cannot be queried; the orders
   * are matched over one query instead of one query per row. That query selects the suborders of
   * these orders only (#1222) — it used to read every suborder of the installation and pick the
   * matching ones in Java. Sorted by complete order sign, as before.
   */
  public List<Suborder> getSubordersByCustomerorderSigns(Collection<String> customerorderSigns) {
    if (customerorderSigns.isEmpty()) {
      return List.of();
    }
    return suborderRepository.findAllByCustomerorderSigns(Set.copyOf(customerorderSigns)).stream()
        .sorted(comparing(Suborder::getCompleteOrderSign))
        .toList();
  }

  /** Like {@link #getSubordersByCustomerorderSigns}, by the ids of the orders (#1205). */
  public List<Suborder> getSubordersByCustomerorderIds(Collection<Long> customerorderIds) {
    if (customerorderIds.isEmpty()) {
      return List.of();
    }
    return suborderRepository.findAllByCustomerorderIds(Set.copyOf(customerorderIds)).stream()
        .sorted(comparing(Suborder::getCompleteOrderSign))
        .toList();
  }

  /**
   * The suborders a filter over existing bookings may offer: not hidden, inactive ones included
   * (#1106). Named after what it filters, because that is all it filters — „sichtbar" says the same
   * word as {@code CustomerorderService.getVisibleCustomerorders()} and means one criterion less.
   */
  public List<Suborder> getNotHiddenSuborders() {
    return suborderDAO.getSuborders().stream()
        .filter(not(Suborder::isHide))
        .toList();
  }

  /**
   * All suborders offered in a select box that is not scoped to one customer order: everything not
   * hidden, plus the one whose complete order sign is {@code keepCompleteSign} even if it is hidden
   * (#895).
   */
  public List<Suborder> getAllSelectableSuborders(String keepCompleteSign) {
    return suborderDAO.getSuborders().stream()
        .filter(suborder -> !suborder.isHide()
            || Objects.equals(suborder.getCompleteOrderSign(), keepCompleteSign))
        .toList();
  }

  /**
   * Suborders of the customer order offered in a select box: everything not hidden, plus the one
   * whose complete order sign is {@code keepCompleteSign} even if it is hidden. Without that
   * exception a stored suborder would silently drop off the record the next time it is edited.
   */
  public List<Suborder> getSelectableSubordersByCustomerorderId(long customerorderId, String keepCompleteSign) {
    return suborderDAO.getSubordersByCustomerorderId(customerorderId).stream()
        .filter(suborder -> !suborder.isHide()
            || Objects.equals(suborder.getCompleteOrderSign(), keepCompleteSign))
        .toList();
  }

  /**
   * Same, addressed by id rather than by complete order sign. Budget and cost records reference a
   * suborder by its sign, the order forms by its id (#1005) — the rule behind both is one and the
   * same: a hidden suborder that a record already stores has to stay in the list, otherwise the
   * form drops it and writes back whatever the browser preselected instead.
   */
  public List<Suborder> getSelectableSubordersByCustomerorderId(long customerorderId, Long keepId) {
    return suborderDAO.getSubordersByCustomerorderId(customerorderId).stream()
        .filter(suborder -> !suborder.isHide() || Objects.equals(suborder.getId(), keepId))
        .toList();
  }

  /**
   * Whether the given {@code LIKE} pattern covers at least one suborder below the customer order
   * with the given sign. Pricing records select their suborders by such a pattern rather than by an
   * exact sign, so this applies the same rule as {@code OrderPricingLookup} — including the trailing
   * slash the pattern binds against.
   */
  /**
   * Whether a suborder with exactly this complete order sign exists (#958). Records that reference a
   * suborder by sign rather than by id — the scope of a JIRA replication does — have no customer order
   * to narrow the search by, so the sign is matched against all suborders. That is a full read, but
   * it happens on a manager's write, and the forms of those records load the same list anyway.
   */
  public boolean existsSuborderWithCompleteOrderSign(String completeOrderSign) {
    return getSuborderByCompleteOrderSign(completeOrderSign) != null;
  }

  /**
   * The suborder carrying exactly this complete order sign, or {@code null} (#1025). A record that
   * stores such a sign cannot get back to the customer order behind it by splitting the string: an
   * order sign may contain a slash itself, so the first segment of {@code 0283/03.20/F&E/01} is not
   * the order. Asking here is exact where parsing only guesses.
   */
  public Suborder getSuborderByCompleteOrderSign(String completeOrderSign) {
    return suborderDAO.getSuborders().stream()
        .filter(suborder -> completeOrderSign.equals(suborder.getCompleteOrderSign()))
        .findFirst()
        .orElse(null);
  }

  public boolean existsSuborderMatching(String customerorderSign, String pattern) {
    var likePattern = SqlLikePattern.startingWith(pattern);
    return subordersOf(customerorderSign).stream()
        .anyMatch(suborder -> likePattern.matches(suborder.getCompleteOrderSign() + "/"));
  }

  private List<Suborder> subordersOf(String customerorderSign) {
    var customerorder = customerorderService.getCustomerorderBySign(customerorderSign);
    return customerorder == null ? List.of() : getSubordersByCustomerorderId(customerorder.getId());
  }

  @Authorized(requiresManager = true)
  public void deleteSuborderById(long suborderId) {
    var event = new SuborderDeleteEvent(suborderId);
    var suborder = suborderDAO.getSuborderById(suborderId);
    try {
      eventPublisher.publishEvent(event);
    } catch(VetoedException e) {
      // adding context to the veto to make it easier to understand the complete picture
      var allMessages = new ArrayList<ServiceFeedbackMessage>();
      allMessages.add(error(ErrorCode.SO_DELETE_GOT_VETO, suborder.getCompleteOrderSign()));
      allMessages.addAll(e.getMessages());
      event.veto(allMessages);
    }
    suborderRepository.deleteById(suborderId);
  }

  public List<Suborder> getSubordersByFilters(Boolean showInactive, String filter, Long customerOrderId, Long customerId, Boolean showHidden) {
    return suborderDAO.getSubordersByFilters(showInactive, filter, customerOrderId, customerId, showHidden);
  }

  public List<Suborder> getSuborderChildren(Long parentSuborderId) {
    return suborderDAO.getSuborderChildren(parentSuborderId);
  }

  public List<Suborder> getSubordersByEmployeeContractIdWithValidEmployeeOrders(long employeecontractId,
      LocalDate validAt) {
    return suborderDAO.getSubordersByEmployeeContractIdWithValidEmployeeOrders(employeecontractId, validAt);
  }

  @Authorized(requiresManager = true)
  public void createCopy(long suborederId) {
    var suborder = getSuborderById(suborederId);
    var copy = createCopy(suborder, true);
    suborderRepository.save(copy);
  }

  @Authorized(requiresManager = true)
  public void changeSuborder_customer(long suborderId, String suborder_customer) {
    getSuborderById(suborderId).setSuborder_customer(suborder_customer);
  }

  @Authorized(requiresManager = true)
  public void hideSuborders(List<Long> suborderIds) {
    for (long suborderId : suborderIds) {
      getSuborderById(suborderId).setHide(true);
    }
  }

  private Suborder createCopy(Suborder suborder, boolean copyroot) {
    Suborder copy = new Suborder();

    // set attrib values in copy
    copy.setCommentnecessary(suborder.getCommentnecessary());
    copy.setCustomerorder(suborder.getCustomerorder());
    copy.setDebithours(suborder.getDebithours()); // see #getDebithours
    copy.setDebithoursunit(suborder.getDebithoursunit());
    copy.setDescription(suborder.getDescription());
    copy.setFromDate(suborder.getFromDate());
    copy.setHide(suborder.isHide());
    copy.setInvoice(suborder.getInvoice());
    copy.setShortdescription(suborder.getShortdescription());
    copy.setStandard(suborder.getStandard());
    copy.setUntilDate(suborder.getUntilDate());
    copy.setSign(suborder.getSign());
    copy.setSuborder_customer(suborder.getSuborder_customer());
    copy.setFixedPrice(suborder.getFixedPrice());
    copy.setTrainingFlag(suborder.isTrainingFlag());
    copy.setOrderType(suborder.getOrderType());

    if (copyroot) {
      copy.setSign("copy_of_" + suborder.getSign());
    }

    for (Suborder child : suborder.getSuborders()) {
      Suborder childCopy = createCopy(child, false);
      childCopy.setParentorder(copy);
      copy.addSuborder(childCopy);
    }
    return copy;
  }
}
