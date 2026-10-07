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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import de.hbt.salat.common.Validity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.event.SignsRenamedEvent;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.exception.VetoedException;
import de.hbt.salat.common.palette.PaletteQuery;
import de.hbt.salat.common.util.ContainsPattern;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.common.util.DurationUtils;
import de.hbt.salat.common.util.SqlLikePattern;
import de.hbt.salat.order.command.GetTimereportMinutesCommandEvent;
import de.hbt.salat.order.domain.SearchHits;
import de.hbt.salat.order.domain.SuborderDTO;
import de.hbt.salat.order.domain.SuborderTreeRow;
import de.hbt.salat.order.domain.SuborderLocation;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SuborderSearchRow;
import de.hbt.salat.order.domain.SuborderReadModel;
import de.hbt.salat.order.domain.SuborderCompleteSign;
import de.hbt.salat.order.domain.TicketReferenceMode;
import de.hbt.salat.order.domain.TicketReferencePolicy;
import de.hbt.salat.order.domain.TicketReferencePolicySource;
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

  private final ApplicationEventPublisher eventPublisher;
  private final CommandPublisher commandPublisher;
  private final SuborderDAO suborderDAO;
  private final SuborderRepository suborderRepository;
  private final CustomerorderService customerorderService;
  private final SpecialOrders specialOrders;

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
            s.isTrainingFlag(),
            s.getEffectiveTicketReferencePolicy()))
        .toList();
  }

  /**
   * What a suborder below {@code parentId} — at the top of the order where it is {@code null} — would
   * inherit as its ticket reference setting, and from where (#1326).
   */
  @Transactional(readOnly = true)
  public TicketReferencePolicySource getInheritedTicketReferencePolicy(long customerorderId, Long parentId) {
    var customerorder = customerorderService.getCustomerorderById(customerorderId);
    var parent = parentId != null ? suborderDAO.getSuborderById(parentId) : null;
    return TicketReferencePolicySource.inheritedBy(customerorder, parent);
  }

  @Authorized(requiresManager = true)
  public void create(SuborderDTO suborderData, Long customerorder) {
    createOrUpdate(null, suborderData, customerorder, new ArrayList<>());
  }

  /**
   * @return what could not follow a renamed or moved suborder by itself (#1206) and needs a look by
   *     hand — usually nothing
   */
  @Authorized(requiresManager = true)
  public List<ServiceFeedbackMessage> update(long suborderId, SuborderDTO suborderData, Long customerorderId) {
    var notices = new ArrayList<ServiceFeedbackMessage>();
    createOrUpdate(suborderId, suborderData, customerorderId, notices);
    return notices;
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

  private void createOrUpdate(Long soId, SuborderDTO data, Long customerorderId,
                              List<ServiceFeedbackMessage> notices) {
    var customerorder = customerorderService.getCustomerorderById(customerorderId);
    Suborder so;
    if (soId != null) {
      // edited suborder
      so = suborderDAO.getSuborderById(soId);
    } else {
      // new suborder
      so = new Suborder();
    }
    // where the suborder sat before the edit — a new sign, parent or order changes the complete sign
    // of its whole branch, and what names it by sign follows (#1206)
    var oldSign = so.isNew() ? null : so.getCompleteOrderSign();
    var oldCustomerorderId = so.isNew() ? null : so.getCustomerorder().getId();
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
    so.setTicketReferencePolicy(ticketReferencePolicyOf(data.ticketReferenceMode(), data.ticketReferenceLimit()));

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

    // a new sign, parent or order moves the stored complete sign of the whole branch along (#1342)
    so.acceptVisitor(Suborder::deriveCompleteOrderSign);

    // a special order, or one above it, keeps its complete sign: the configuration names it (#1341, ADR-0035)
    var completeSignChanges = oldSign != null && !oldSign.equals(so.getCompleteOrderSign());
    if (completeSignChanges && specialOrders.isLockedSuborder(so.getId())) {
      throw new BusinessRuleException(ErrorCode.SO_SPECIAL_ORDER_LOCKED, oldSign);
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
    var newSign = so.getCompleteOrderSign();
    if (oldSign != null && !oldSign.equals(newSign)) {
      var renamed = new SignsRenamedEvent(oldSign, newSign, oldCustomerorderId, so.getCustomerorder().getId());
      eventPublisher.publishEvent(renamed);
      notices.addAll(renamed.getNotices());
    }
  }

  @EventListener
  void onCustomerorderUpdate(CustomerorderUpdateEvent event) {
    var customerorder = event.getDomainObject();
    var newValidity = customerorder.getValidity();

    List<Suborder> suborders = suborderDAO.getSubordersByCustomerorderId(customerorder.getId());
    // a renamed order moves the stored complete sign of each of its suborders along (#1342) — before
    // the validity is adjusted below, which would otherwise take every suborder as renamed itself
    suborders.forEach(Suborder::deriveCompleteOrderSign);

    // adjust suborders
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
    createOrUpdate(suborderId, data, suborder.getCustomerorder().getId(), new ArrayList<>());
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
        parentId,
        so.getTicketReferencePolicy() != null ? so.getTicketReferencePolicy().mode() : null,
        so.getTicketReferencePolicy() != null ? so.getTicketReferencePolicy().limit() : null
    );
  }

  /**
   * A suborder's own ticket reference setting (#1326): {@code null} for no mode, which inherits from
   * above. "At most" without a number of at least 1 is refused rather than read as "inherit".
   */
  private static TicketReferencePolicy ticketReferencePolicyOf(TicketReferenceMode mode, Integer limit) {
    if (mode == null) {
      return null;
    }
    var policy = TicketReferencePolicy.of(mode, limit);
    if (policy == null) {
      throw new InvalidDataException(ErrorCode.SO_TICKET_REFERENCE_LIMIT_INVALID);
    }
    return policy;
  }

  public List<Suborder> getSubordersByCustomerorderId(long customerorderId) {
    return suborderDAO.getSubordersByCustomerorderId(customerorderId).stream()
        .filter(
                not(Suborder::isHide))
        .toList();
  }

  /**
   * The visible suborders of the order as plain values, sorted by complete order sign like
   * {@link #getSubordersByCustomerorderId} (#1338) — what another module evaluates an order with
   * (→ ADR-0021, Nachtrag #1338). One statement for the order: the path is built from the suborders
   * read, hidden parents included, not by walking the parent chain of each; the complete sign is
   * stored (#1342).
   */
  @Transactional(readOnly = true)
  public List<SuborderReadModel> getSuborderReadModelsByCustomerorderId(long customerorderId) {
    return suborderReadModelsOf(customerorderId, false);
  }

  /**
   * {@link #getSuborderReadModelsByCustomerorderId} with the hidden suborders included (#1339). A
   * suborder is hidden once nobody is to book on it any more, but the bookings made before stay on
   * it — a reader deciding by path which bookings lie in a subtree has to find their suborder too.
   */
  @Transactional(readOnly = true)
  public List<SuborderReadModel> getAllSuborderReadModelsByCustomerorderId(long customerorderId) {
    return suborderReadModelsOf(customerorderId, true);
  }

  private List<SuborderReadModel> suborderReadModelsOf(long customerorderId, boolean includeHidden) {
    var all = suborderDAO.getSubordersByCustomerorderId(customerorderId);
    var byId = new HashMap<Long, Suborder>();
    all.forEach(suborder -> byId.put(suborder.getId(), suborder));
    return all.stream()
        .filter(suborder -> includeHidden || !suborder.isHide())
        .map(suborder -> SuborderReadModel.of(suborder, byId))
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
   * Whether the suborder is offered as a fixed price ({@code fixedPrice}) — as a value for another
   * module (ADR-0021): a budget plan on it is preset to a fixed price (#1404). {@code false} for an
   * id without a suborder.
   */
  @Transactional(readOnly = true)
  public boolean isOfferedAtFixedPrice(long suborderId) {
    var suborder = suborderDAO.getSuborderById(suborderId);
    return suborder != null && Boolean.TRUE.equals(suborder.getFixedPrice());
  }

  /**
   * The complete order signs ({@code ORDER/01/02}) of the suborders with these ids, by id — what
   * another module needs to name suborders it refers to by id (#1212, ADR-0021). An id without a
   * suborder is missing.
   */
  @Transactional(readOnly = true)
  public Map<Long, String> getCompleteOrderSignsByIds(Collection<Long> suborderIds) {
    if (suborderIds.isEmpty()) {
      return Map.of();
    }
    return suborderRepository.findCompleteSigns(Set.copyOf(suborderIds)).stream()
        .collect(Collectors.toMap(SuborderCompleteSign::id, SuborderCompleteSign::completeOrderSign));
  }

  /**
   * Where the suborders with these ids sit in the order tree, by id — for a module that refers to a
   * suborder by id and has to tell which order and which branch it belongs to (#1322, ADR-0021). An
   * id without a suborder is missing.
   */
  @Transactional(readOnly = true)
  public Map<Long, SuborderLocation> getSuborderLocationsByIds(Collection<Long> suborderIds) {
    return getSubordersByIds(suborderIds).stream()
        .collect(Collectors.toMap(Suborder::getId, SuborderLocation::of));
  }

  /**
   * The ids of the suborder and of every suborder below it, hidden and expired ones included (#1322)
   * — empty when there is no such suborder.
   */
  @Transactional(readOnly = true)
  public List<Long> getSubtreeIds(long suborderId) {
    var suborder = suborderDAO.getSuborderById(suborderId);
    return suborder == null ? List.of() : suborder.getAllChildren().stream().map(Suborder::getId).toList();
  }

  /** The ids of every suborder of the order at any depth, hidden and expired ones included (#1322). */
  @Transactional(readOnly = true)
  public List<Long> getSuborderIdsByCustomerorderId(long customerorderId) {
    return suborderDAO.getSubordersByCustomerorderId(customerorderId).stream().map(Suborder::getId).toList();
  }

  public List<Suborder> getSubordersByEmployeeContractId(long employeeContractId) {
    return suborderDAO.getSubordersByEmployeeContractId(employeeContractId);
  }

  public List<Suborder> getAllSuborders() {
    return suborderDAO.getSuborders();
  }

  /**
   * The suborders of the given customer orders, hidden ones included — for lists whose rows name
   * their suborder by its complete order sign and need its description next to it (#952, by the ids
   * of the orders since #1205).
   *
   * <p>The orders are matched over one query instead of one query per row. That query selects the
   * suborders of these orders only (#1222) — it used to read every suborder of the installation and
   * pick the matching ones in Java. Sorted by complete order sign, as before.
   */
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
   * The suborders a search dialog over existing bookings offers (#1331), searched, ordered and cut in the database:
   * not hidden — inactive ones included, as in {@link #getNotHiddenSuborders()} —, ordered by complete sign, the first
   * {@code limit} of them with their place in the tree, and how many there are.
   *
   * @param term        found in the complete sign or the short description as shown, case aside; blank for every
   *                    suborder
   * @param customerIds the customers the suborders belong to, through an order that is not hidden; empty for all
   * @param suborderIds the suborders to search among, {@code null} for all — the caller decides what its reader may
   *                    see
   */
  @Transactional(readOnly = true)
  public SearchHits<SuborderTreeRow> searchNotHiddenSuborders(String term, Collection<Long> customerIds,
      Collection<Long> suborderIds, int limit) {
    var everySuborder = suborderIds == null;
    var noSuborderToSearchAmong = !everySuborder && suborderIds.isEmpty();
    if (noSuborderToSearchAmong || !ContainsPattern.canOccurInUtf8mb3(term)) {
      return SearchHits.none();
    }
    var pattern = ContainsPattern.of(term);
    var everyCustomer = customerIds.isEmpty();
    var amongIds = everySuborder ? List.<Long>of() : suborderIds;
    var rows = limit > 0
        ? suborderRepository.findDialogSuborders(pattern, everyCustomer, customerIds, everySuborder, amongIds,
            Limit.of(limit))
        : List.<SuborderTreeRow>of();
    // fewer rows than the limit are all there are; only a full page needs counting
    var total = rows.size() < limit ? rows.size()
        : suborderRepository.countDialogSuborders(pattern, everyCustomer, customerIds, everySuborder, amongIds);
    return new SearchHits<>(placedInTree(rows), total);
  }

  /**
   * Adds level and descendant count from the edges of the orders the rows belong to — one statement for all of them,
   * hidden suborders included, because a pick takes the whole branch along.
   */
  private List<SuborderTreeRow> placedInTree(List<SuborderTreeRow> rows) {
    if (rows.isEmpty()) {
      return rows;
    }
    var customerorderIds = rows.stream().map(SuborderTreeRow::customerorderId).collect(Collectors.toSet());
    var parents = new HashMap<Long, Long>();
    var children = new HashMap<Long, List<Long>>();
    for (var edge : suborderRepository.findEdgesByCustomerorderIds(customerorderIds)) {
      if (edge.parentId() != null) {
        parents.put(edge.id(), edge.parentId());
        children.computeIfAbsent(edge.parentId(), parent -> new ArrayList<>()).add(edge.id());
      }
    }
    return rows.stream()
        .map(row -> row.placed(levelOf(row.id(), parents), descendantCountOf(row.id(), children)))
        .toList();
  }

  /** How many parents lie above the suborder; a parent met twice would be a cycle, and the walk ends there. */
  private static int levelOf(long suborderId, Map<Long, Long> parents) {
    var above = new HashSet<Long>();
    var parent = parents.get(suborderId);
    while (parent != null && above.add(parent)) {
      parent = parents.get(parent);
    }
    return above.size();
  }

  private static int descendantCountOf(long suborderId, Map<Long, List<Long>> children) {
    var below = new HashSet<Long>();
    var open = new ArrayList<>(children.getOrDefault(suborderId, List.of()));
    while (!open.isEmpty()) {
      var next = open.removeLast();
      if (next != suborderId && below.add(next)) {
        open.addAll(children.getOrDefault(next, List.of()));
      }
    }
    return below.size();
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
   * The suborders of the customer order a select for something new offers, as read models for another
   * module (#1386, ADR-0021, ADR-0029): neither hidden nor inactive today, plus the suborder
   * {@code keepId} whatever it is.
   */
  public List<SuborderReadModel> getCreatableSuborderReadModelsByCustomerorderId(long customerorderId, Long keepId) {
    var all = suborderDAO.getSubordersByCustomerorderId(customerorderId);
    var byId = new HashMap<Long, Suborder>();
    all.forEach(suborder -> byId.put(suborder.getId(), suborder));
    return all.stream()
        .filter(suborder -> isCreatable(suborder) || Objects.equals(suborder.getId(), keepId))
        .map(suborder -> SuborderReadModel.of(suborder, byId))
        .toList();
  }

  private static boolean isCreatable(Suborder suborder) {
    return !suborder.isHide() && !Validity.isInactive(suborder.getUntilDate());
  }

  /**
   * The suborder carrying exactly this complete order sign, or {@code null} (#1025). A record that
   * stores such a sign cannot get back to the customer order behind it by splitting the string: an
   * order sign may contain a slash itself, so the first segment of {@code 0283/03.20/F&E/01} is not
   * the order. Asking here is exact where parsing only guesses — one lookup over the unique key of
   * the stored column (#1342).
   */
  public Suborder getSuborderByCompleteOrderSign(String completeOrderSign) {
    return suborderRepository.findByCompleteOrderSign(completeOrderSign).orElse(null);
  }

  /**
   * Whether the given {@code LIKE} pattern covers at least one suborder below the customer order
   * with the given id. Pricing records select their suborders by such a pattern rather than by an
   * exact sign, so this applies the same rule as {@code OrderPricingLookup} — including the trailing
   * slash the pattern binds against. Hidden suborders cover nothing. The order is asked by id: the
   * caller holds it already, and its sign would only be resolved back into the id here (#1340).
   */
  public boolean existsSuborderMatching(long customerorderId, String pattern) {
    var likePattern = SqlLikePattern.startingWith(pattern);
    return suborderDAO.getSubordersByCustomerorderId(customerorderId).stream()
        .filter(not(Suborder::isHide))
        .anyMatch(suborder -> likePattern.matches(suborder.getCompleteOrderSign() + "/"));
  }

  @Authorized(requiresManager = true)
  public void deleteSuborderById(long suborderId) {
    var event = new SuborderDeleteEvent(suborderId);
    var suborder = suborderDAO.getSuborderById(suborderId);
    if (suborder == null) throw new InvalidDataException(ErrorCode.SO_NOT_FOUND);
    if (specialOrders.isLockedSuborder(suborderId)) {
      throw new BusinessRuleException(ErrorCode.SO_SPECIAL_ORDER_LOCKED, suborder.getCompleteOrderSign());
    }
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
    if (suborder == null) throw new InvalidDataException(ErrorCode.SO_NOT_FOUND);
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
    copy.setTicketReferencePolicy(suborder.getTicketReferencePolicy());

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
