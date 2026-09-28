package org.tb.dailyreport.controller;

import static org.tb.common.palette.PaletteKind.PERSON;
import static org.tb.common.palette.PaletteKind.SUBORDER;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.Validity;
import org.tb.common.palette.PaletteCommand;
import org.tb.common.palette.PaletteHit;
import org.tb.common.palette.PaletteKind;
import org.tb.common.palette.PaletteLink;
import org.tb.common.palette.PaletteParameter;
import org.tb.common.palette.PaletteProvider;
import org.tb.common.palette.PaletteQuery;
import org.tb.common.palette.PaletteSuggestion;
import org.tb.common.palette.PaletteSuggestionRequest;
import org.tb.common.palette.PaletteTarget;
import org.tb.common.palette.PaletteText;
import org.tb.common.util.DateUtils;
import org.tb.common.web.UiState;
import org.tb.dailyreport.auth.TimereportAuthorization;
import org.tb.dailyreport.preferences.TimereportPreferenceService;
import org.tb.dailyreport.service.PublicholidayService;
import org.tb.dailyreport.service.ReleaseService;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.employee.service.PersonSearchService;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.EmployeeorderService;
import org.tb.order.service.SuborderService;

/**
 * The booking targets of objects the command palette found (#1157, ADR-0031): "Buchen auf …" for a
 * suborder, the daily and the matrix view for a person.
 *
 * <p><b>Buchen auf</b> is offered where the booking form would accept the booking today, for the
 * contract it would open with: the remembered selection if the user may read it, otherwise their own
 * current contract — the fallback of the dashboard (#1134). Three conditions, the same as the form
 * and its saving together: the contract is readable, it has an employee order on the suborder valid
 * today, and today is open for writing on it ({@link TimereportAuthorization#isWriteAllowedOn}, which
 * turns a people lead away from an open day of a team member). The link names the contract as the
 * form field {@code employeecontractId}, never as the filter — it must not change the selection
 * (ADR-0023).
 *
 * <p><b>Einzel- und Matrixübersicht</b> are offered for every contract the user may read. The views
 * check the same persons in {@code WorkingdayService} — manager, supervising people lead, owner —
 * plus holders of a working-day rule, who see fewer targets here than they could open: the safe
 * direction. Both links change the remembered selection, as the contract selector in the header does.
 *
 * <p><b>The parameters of the commands</b> (#1158) {@code buchen}, {@code tag}, {@code matrix},
 * {@code freigabe} and {@code abnahme} are completed here as well, since their pages are this
 * module's: each value comes from the same source as the page it leads to — the form's list of
 * suborders, the months the release and acceptance pages propose, the contracts the acceptance page
 * lists and {@link ReleaseService} lets accept.
 */
@Component
@RequiredArgsConstructor
public class DailyReportPaletteProvider implements PaletteProvider {

  /** How many persons are checked for acceptance; the palette shows fewer. */
  static final int PERSONS_CHECKED = 12;

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final UiState uiState;
  private final AuthorizedEmployee authorizedEmployee;
  private final EmployeecontractService employeecontractService;
  private final EmployeeorderService employeeorderService;
  private final TimereportAuthorization timereportAuthorization;
  private final AuthorizedUser authorizedUser;
  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;
  private final TimereportPreferenceService timereportPreferenceService;
  private final TimereportService timereportService;
  private final PublicholidayService publicholidayService;
  private final ReleaseService releaseService;
  private final PersonSearchService personSearchService;

  @Override
  public Map<String, List<PaletteTarget>> targetsFor(PaletteKind kind, List<PaletteHit> hits) {
    if (kind == SUBORDER) {
      return bookingTargets(hits);
    }
    if (kind == PERSON) {
      return viewTargets(hits);
    }
    return Map.of();
  }

  private Map<String, List<PaletteTarget>> bookingTargets(List<PaletteHit> hits) {
    var contract = selectedContract();
    var today = DateUtils.today();
    if (contract.isEmpty() || !timereportAuthorization.isWriteAllowedOn(contract.get(), today)) {
      return Map.of();
    }
    var ids = hits.stream().map(hit -> Long.valueOf(hit.key())).toList();
    var bookable = employeeorderService.getBookableSuborderIds(contract.get().getId(), ids, today);
    var targets = new HashMap<String, List<PaletteTarget>>();
    for (var hit : hits) {
      if (bookable.contains(Long.valueOf(hit.key()))) {
        targets.put(hit.key(), List.of(new PaletteTarget(PaletteText.of("main.palette.target.suborder.book", hit.title()),
            PaletteLink.to("/dailyreport/timereports/new")
                .param("suborderId", hit.key())
                .param("employeecontractId", contract.get().getId())
                .build(), 5)));
      }
    }
    return targets;
  }

  private Map<String, List<PaletteTarget>> viewTargets(List<PaletteHit> hits) {
    var targets = new HashMap<String, List<PaletteTarget>>();
    for (var hit : hits) {
      if (employeecontractService.getReadableEmployeecontract(Long.parseLong(hit.key())).isPresent()) {
        targets.put(hit.key(), List.of(
            new PaletteTarget(PaletteText.of("main.palette.target.person.daily"),
                PaletteLink.to("/dailyreport/daily").param("fEmployeeContractId", hit.key()).build(), 10),
            new PaletteTarget(PaletteText.of("main.palette.target.person.matrix"),
                PaletteLink.to("/dailyreport/matrix").param("fEmployeeContractId", hit.key()).build(), 11)));
      }
    }
    return targets;
  }

  // --- the parameters of the commands (#1158) ---------------------------------------------------

  /**
   * The values for the parameters of {@code buchen}, {@code tag}, {@code matrix}, {@code freigabe} and
   * {@code abnahme} — the commands whose pages this module owns. Month and duration of the others, and
   * every day but the last working day, the browser reads itself.
   */
  @Override
  public List<PaletteSuggestion> suggest(PaletteSuggestionRequest request) {
    if (request.parameter() == PaletteParameter.DAY
        && (request.command() == PaletteCommand.BOOK || request.command() == PaletteCommand.DAY)) {
      return List.of(lastWorkday());
    }
    if (request.is(PaletteCommand.BOOK, PaletteParameter.SUBORDER)) {
      return bookableSuborders(request.query(), request.date());
    }
    if (request.is(PaletteCommand.MATRIX, PaletteParameter.PERSON)) {
      return viewablePersons(request.query());
    }
    if (request.is(PaletteCommand.RELEASE, PaletteParameter.MONTH)) {
      return nextReleaseMonth();
    }
    if (request.is(PaletteCommand.ACCEPT, PaletteParameter.PERSON)) {
      return acceptablePersons(request.query());
    }
    if (request.is(PaletteCommand.ACCEPT, PaletteParameter.MONTH)) {
      return nextAcceptanceMonth(request.contractId());
    }
    return List.of();
  }

  /** The value is the ISO date; the browser names it with its own word and formats the day. */
  private PaletteSuggestion lastWorkday() {
    var day = publicholidayService.getLastWorkdayBefore(DateUtils.today()).toString();
    return PaletteSuggestion.of(day, day, null, null);
  }

  /**
   * The suborders the contract of the booking form can book on the chosen day — the form's own list
   * ({@link SuborderOption#bookable}) —, and after them those it can book today but not on that day,
   * disabled: "am … nicht buchbar" tells why the one looked for is not to be had. Among the bookable
   * ones the favourite comes first, then what was booked lately, then the rest in the form's order.
   */
  private List<PaletteSuggestion> bookableSuborders(PaletteQuery query, LocalDate requestedDate) {
    var contract = selectedContract();
    if (contract.isEmpty()) {
      return List.of();
    }
    long contractId = contract.get().getId();
    var today = DateUtils.today();
    var date = requestedDate != null ? requestedDate : today;
    var bookable = SuborderOption.bookable(customerorderService, suborderService, contractId, date);
    var bookableIds = bookable.stream().map(SuborderOption::id).collect(Collectors.toSet());
    var elsewhere = date.equals(today) ? List.<SuborderOption>of()
        : SuborderOption.bookable(customerorderService, suborderService, contractId, today).stream()
            .filter(option -> !bookableIds.contains(option.id()))
            .toList();

    var favoriteId = timereportPreferenceService.getForCurrentUser().favoriteSuborderId();
    var recent = recentlyBookedSuborderIds(contractId, today);
    var rank = Comparator.comparingInt((SuborderOption option) -> option.id().equals(favoriteId) ? 0
        : recent.contains(option.id()) ? 1 : 2);

    var suggestions = new ArrayList<PaletteSuggestion>();
    bookable.stream()
        .filter(option -> query.isContainedIn(option.label(), option.subtext()))
        .sorted(rank)
        .forEach(option -> suggestions.add(new PaletteSuggestion(String.valueOf(option.id()), option.sign(),
            description(option), option.id().equals(favoriteId) ? PaletteText.of("main.palette.suggestion.favorite")
                : recent.contains(option.id()) ? PaletteText.of("main.palette.suggestion.recent") : null,
            false, option.commentNecessary(), query.isKey(option.sign()))));
    elsewhere.stream()
        .filter(option -> query.isContainedIn(option.label(), option.subtext()))
        .forEach(option -> suggestions.add(new PaletteSuggestion(String.valueOf(option.id()), option.sign(),
            description(option), PaletteText.of("main.palette.suggestion.notbookable", DATE.format(date)),
            true, option.commentNecessary(), query.isKey(option.sign()))));
    return suggestions;
  }

  /** The short description, where the form's label has one after the sign. */
  private static String description(SuborderOption option) {
    return option.label().length() > option.sign().length()
        ? option.label().substring(option.sign().length()).replaceFirst("^ · ", "") : null;
  }

  /** The suborders of the bookings of the last days, the latest first ("zuletzt gebucht"). */
  private Set<Long> recentlyBookedSuborderIds(long contractId, LocalDate today) {
    var ids = new LinkedHashSet<Long>();
    for (var booking : timereportService.getPreviousBookings(contractId, today.plusDays(1))) {
      var employeeorder = employeeorderService.getEmployeeorderById(booking.employeeorderId());
      if (employeeorder != null && employeeorder.getSuborder() != null) {
        ids.add(employeeorder.getSuborder().getId());
      }
    }
    return ids;
  }

  /** The persons whose matrix view the user may open: those whose contract they may read. */
  private List<PaletteSuggestion> viewablePersons(PaletteQuery query) {
    return personSearchService.getPalettePersons(query).stream()
        .map(person -> new PaletteSuggestion(String.valueOf(person.contractId()), person.name(), person.sign(),
            null, false, false, query.isKey(person.sign())))
        .toList();
  }

  /**
   * The next open month of the contract the release page shows, where the user may release it — the
   * page proposes the same month ({@link ReviewMonths#nextRelease}).
   */
  private List<PaletteSuggestion> nextReleaseMonth() {
    var contract = selectedContract();
    if (contract.isEmpty() || !releaseService.isReleaseAllowed(contract.get().getId())) {
      return List.of();
    }
    var releasedUntil = contract.get().getReportReleaseDate();
    var month = ReviewMonths.nextRelease(contract.get()).toString();
    return List.of(PaletteSuggestion.of(month, month, null, releasedUntil == null
        ? PaletteText.of("main.palette.suggestion.month.release")
        : PaletteText.of("main.palette.suggestion.month.release.since", DATE.format(releasedUntil))));
  }

  /**
   * The persons a people lead may accept for: the contracts the acceptance page lists — all visible
   * ones for a manager, the own team's otherwise —, each where {@link ReleaseService#isAcceptAllowed}
   * says so. Without the role there is no {@code abnahme} to complete.
   */
  private List<PaletteSuggestion> acceptablePersons(PaletteQuery query) {
    if (!authorizedUser.isPeopleLead()) {
      return List.of();
    }
    List<Employeecontract> contracts;
    if (authorizedUser.isManager()) {
      contracts = employeecontractService.getVisibleEmployeeContractsForAuthorizedUser();
    } else {
      var employeeId = authorizedEmployee.getEmployeeId();
      if (employeeId == null) {
        return List.of();
      }
      contracts = employeecontractService.getTeamContractsIncludingExpired(employeeId);
    }
    return contracts.stream()
        .filter(contract -> query.isContainedIn(contract.getEmployee().getName(), contract.getEmployee().getSign()))
        .sorted(Comparator.comparing((Employeecontract contract) -> Validity.isInactive(contract.getValidUntil()))
            .thenComparing(Comparator.comparingInt((Employeecontract contract) -> query.matchWithKey(
                contract.getEmployee().getSign(), contract.getEmployee().getName())).reversed())
            .thenComparing(contract -> contract.getEmployee().getName()))
        .limit(PERSONS_CHECKED)
        .filter(contract -> releaseService.isAcceptAllowed(contract.getId()))
        .map(contract -> new PaletteSuggestion(String.valueOf(contract.getId()), contract.getEmployee().getName(),
            contract.getEmployee().getSign(), Validity.isInactive(contract.getValidUntil())
                ? PaletteText.of("main.palette.context.contract.until", DATE.format(contract.getValidUntil())) : null,
            false, false, query.isKey(contract.getEmployee().getSign())))
        .toList();
  }

  /** The month the acceptance page would propose for the person ({@link ReviewMonths#nextAcceptance}). */
  private List<PaletteSuggestion> nextAcceptanceMonth(Long contractId) {
    if (contractId == null || !authorizedUser.isPeopleLead() || !releaseService.isAcceptAllowed(contractId)) {
      return List.of();
    }
    var contract = employeecontractService.getEmployeecontractById(contractId);
    if (contract == null) {
      return List.of();
    }
    var acceptedUntil = contract.getReportAcceptanceDate();
    var month = ReviewMonths.nextAcceptance(contract);
    // with nothing released beyond the last acceptance, the page proposes an accepted month; the
    // palette proposes the same, but does not call it the next one to accept
    PaletteText note;
    if (acceptedUntil == null) {
      note = PaletteText.of("main.palette.suggestion.month.accept");
    } else if (!acceptedUntil.isBefore(month.atEndOfMonth())) {
      note = PaletteText.of("main.palette.suggestion.month.accept.done", DATE.format(acceptedUntil));
    } else {
      note = PaletteText.of("main.palette.suggestion.month.accept.since", DATE.format(acceptedUntil));
    }
    return List.of(PaletteSuggestion.of(month.toString(), month.toString(), null, note));
  }

  private Optional<Employeecontract> selectedContract() {
    var selected = uiState.getLongValue(DailyReportUiStateKeyContributor.EMPLOYEE_CONTRACT_ID);
    if (selected != null && selected > 0) {
      var readable = employeecontractService.getReadableEmployeecontract(selected);
      if (readable.isPresent()) {
        return readable;
      }
    }
    var employeeId = authorizedEmployee.getEmployeeId();
    if (employeeId == null) {
      return Optional.empty();
    }
    try {
      return employeecontractService.getCurrentContract(employeeId);
    } catch (IncorrectResultSizeDataAccessException e) {
      // no running contract and more than one to come: the form would not know either
      return Optional.empty();
    }
  }
}
