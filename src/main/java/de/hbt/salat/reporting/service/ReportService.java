package de.hbt.salat.reporting.service;

import static de.hbt.salat.auth.domain.AccessLevel.DELETE;
import static de.hbt.salat.auth.domain.AccessLevel.EXECUTE;
import static de.hbt.salat.auth.domain.AccessLevel.WRITE;
import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static de.hbt.salat.common.exception.ErrorCode.RP_REPORT_EXECUTION_FAILED;
import static de.hbt.salat.common.exception.ErrorCode.RP_REPORT_ID_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.RP_GROUP_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.RP_REPORT_PARAMETERS_MISSING;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.event.SignsRenamedEvent;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.palette.PaletteQuery;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.reporting.auth.ReportAuthorization;
import de.hbt.salat.reporting.domain.ReportDefinition;
import de.hbt.salat.reporting.domain.ReportGroup;
import de.hbt.salat.reporting.domain.ReportGroupInfo;
import de.hbt.salat.reporting.domain.ReportParameter;
import de.hbt.salat.reporting.domain.ReportResult;
import de.hbt.salat.reporting.domain.ReportResultColumnHeader;
import de.hbt.salat.reporting.domain.ReportResultColumnValue;
import de.hbt.salat.reporting.domain.ReportResultRow;
import de.hbt.salat.reporting.persistence.ReportDefinitionRepository;
import de.hbt.salat.reporting.persistence.OwnerReferences;
import de.hbt.salat.reporting.persistence.ReportGroupRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@DependsOnDatabaseInitialization
@Authorized
public class ReportService {

  private final ReportDefinitionRepository reportDefinitionRepository;
  private final DataSource dataSource;
  private final ReportAuthorization reportAuthorization;
  private final AuthorizedUser authorizedUser;
  private final OwnerReferences ownerReferences;
  private final ReportGroupRepository reportGroupRepository;
  private final ReportGroupNames reportGroupNames;

  /** A report with the group it is listed in (#1523). */
  public record GroupedReport(ReportDefinition report, ReportGroupInfo group) {
  }

  /** The reports of one group, by title. */
  public record ReportsOfGroup(ReportGroupInfo group, List<ReportDefinition> reports) {
  }

  /**
   * What the report list shows (#1523).
   *
   * @param visibleGroups the groups the person sees at all — those with a report they may run —, for
   *                      the filter; independent of the filters themselves
   * @param groups        the groups with the reports the filters leave, in the order of
   *                      {@link ReportGroupInfo#ORDER}
   */
  public record ReportOverview(List<ReportGroupInfo> visibleGroups, List<ReportsOfGroup> groups) {
  }

  /** The value of the group filter that stands for the reports without a group. */
  public static final long WITHOUT_GROUP = 0L;

  /** Every report the person may execute, by title. */
  @Transactional(readOnly = true)
  public List<ReportDefinition> getReportDefinitions() {
    return executable();
  }

  /** {@link #getReportDefinitions()} with the group of each (#1523), groups and titles in list order. */
  @Transactional(readOnly = true)
  public List<GroupedReport> getGroupedReportDefinitions() {
    var reports = executable();
    var groups = groupsOf(reports);
    return reports.stream()
        .map(report -> new GroupedReport(report, groupOf(report, groups)))
        .sorted((a, b) -> ReportGroupInfo.ORDER.compare(a.group(), b.group()))
        .toList();
  }

  /**
   * The report list (#1523): the reports the person may execute, by group. A group is there only with a
   * report the person may run, and it counts only those — that is what "seeing a group" means.
   *
   * @param filter  free text; every report whose title or group contains it
   * @param groupId one group only, {@link #WITHOUT_GROUP} for the reports without one; {@code null} for all
   */
  @Transactional(readOnly = true)
  public ReportOverview getOverview(String filter, Long groupId) {
    var reports = executable();
    var groups = groupsOf(reports);
    var visible = new LinkedHashMap<Long, ReportGroupInfo>();
    var byGroup = new LinkedHashMap<ReportGroupInfo, List<ReportDefinition>>();
    var text = filter == null ? "" : filter.strip().toLowerCase(Locale.ROOT);
    for (var report : reports) {
      var group = groupOf(report, groups);
      visible.putIfAbsent(group.id(), group);
      if (matchesGroup(group, groupId) && matchesText(report, group, text)) {
        byGroup.computeIfAbsent(group, ignored -> new ArrayList<>()).add(report);
      }
    }
    return new ReportOverview(
        visible.values().stream().sorted(ReportGroupInfo.ORDER).toList(),
        byGroup.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(ReportGroupInfo.ORDER))
            .map(entry -> new ReportsOfGroup(entry.getKey(), List.copyOf(entry.getValue())))
            .toList());
  }

  private static boolean matchesGroup(ReportGroupInfo group, Long groupId) {
    if (groupId == null) {
      return true;
    }
    return groupId == WITHOUT_GROUP ? group.isNone() : groupId.equals(group.id());
  }

  /** The text stands in the title or in the name of the group — where the prefix of the title used to be. */
  private static boolean matchesText(ReportDefinition report, ReportGroupInfo group, String text) {
    return text.isEmpty()
        || (report.getName() != null && report.getName().toLowerCase(Locale.ROOT).contains(text))
        || (group.name() != null && group.name().toLowerCase(Locale.ROOT).contains(text));
  }

  /**
   * The reports the command palette considers for a query (#1232): every word in the title or in the
   * name of the group (#1523), only those the person may execute — the question the page of the
   * palette's first target asks ({@link #getReportDefinition(long)}). The check comes before the limit:
   * a limit first could cut away exactly the few reports an employee may run.
   */
  @Transactional(readOnly = true)
  public List<GroupedReport> getPaletteCandidates(PaletteQuery query) {
    var reports = executable();
    var groups = groupsOf(reports);
    return reports.stream()
        .map(report -> new GroupedReport(report, groupOf(report, groups)))
        .filter(candidate -> query.isContainedIn(candidate.report().getName(), candidate.group().name()))
        .limit(PaletteQuery.CANDIDATE_LIMIT)
        .toList();
  }

  /**
   * The group a report is listed in (#1523), a personal one with the name of its person. Read again in
   * this transaction: a scheduled job holds the report from a session that is closed by now.
   */
  @Transactional(readOnly = true)
  public ReportGroupInfo getGroupOf(ReportDefinition report) {
    return reportDefinitionRepository.findById(report.getId())
        .map(definition -> reportGroupNames.of(definition.getGroup()))
        .orElse(ReportGroupInfo.NONE);
  }

  private List<ReportDefinition> executable() {
    return reportDefinitionRepository.findAllWithGroup().stream()
        .filter(r -> reportAuthorization.isAuthorized(r, EXECUTE))
        .toList();
  }

  private Map<Long, ReportGroupInfo> groupsOf(List<ReportDefinition> reports) {
    return reportGroupNames.of(reports.stream().map(ReportDefinition::getGroup).filter(Objects::nonNull)
        .distinct().toList());
  }

  private static ReportGroupInfo groupOf(ReportDefinition report, Map<Long, ReportGroupInfo> groups) {
    return report.getGroup() == null ? ReportGroupInfo.NONE : groups.get(report.getGroupId());
  }

  public void deleteReportDefinition(long reportDefinitionId) {
    reportDefinitionRepository.findById(reportDefinitionId).ifPresent(report -> {
      if(reportAuthorization.isAuthorized(report, DELETE)) {
        reportDefinitionRepository.delete(report);
      }
    });
  }

  public ReportDefinition getReportDefinition(long reportDefinitionId) {
    return reportDefinitionRepository.findById(reportDefinitionId)
        .filter(report -> reportAuthorization.isAuthorized(report, EXECUTE))
        .orElse(null);
  }

  /**
   * Liefert den Report mit dieser Id und sagt, warum es keinen gibt: ein API-Aufrufer soll eine
   * unbekannte Id von einer fehlenden Berechtigung unterscheiden können.
   * {@link #getReportDefinition(long)} liefert in beiden Fällen {@code null}.
   */
  public ReportDefinition getReportDefinitionById(long reportDefinitionId) {
    var report = reportDefinitionRepository.findById(reportDefinitionId)
        .orElseThrow(() -> new InvalidDataException(RP_REPORT_ID_NOT_FOUND, String.valueOf(reportDefinitionId)));
    if (!reportAuthorization.isAuthorized(report, EXECUTE)) {
      throw new AuthorizationException(AA_NOT_ATHORIZED);
    }
    return report;
  }

  /**
   * Führt einen Report aus und macht aus jedem Fehlschlag eine Ausnahme. {@link #execute(Long, List)}
   * liefert einen Fehler als Teil des Ergebnisses, weil die Ergebnisansicht ihn anzeigt und das
   * fehlgeschlagene SQL dazu — für einen Aufrufer ohne Bildschirm ist ein Ergebnis mit
   * {@code error = true} dagegen nicht von einem leeren Report zu unterscheiden.
   */
  public ReportResult executeChecked(ReportDefinition reportDefinition, List<ReportParameter> parameters) {
    var missing = ReportParameters.missing(parameters, reportDefinition.getSql());
    if (!missing.isEmpty()) {
      throw new InvalidDataException(RP_REPORT_PARAMETERS_MISSING, String.join(", ", new TreeSet<>(missing)));
    }
    var result = execute(reportDefinition.getId(), parameters);
    if (result.isError()) {
      var errorInfo = result.getErrorInfo();
      log.warn("Report {} could not be executed: {}", reportDefinition.getName(),
          errorInfo != null ? errorInfo.getErrorMessage() : null);
      throw new BusinessRuleException(RP_REPORT_EXECUTION_FAILED, reportDefinition.getName());
    }
    return result;
  }

  /**
   * The names of the report definitions whose SQL names the old sign of a renamed order or suborder
   * as a literal (#1206), sorted — every definition, not only those the caller may run, because the
   * caller is the one who renamed. Each with its group (#1523), titles repeat across groups. Nothing is
   * rewritten: free SQL is safer named than edited.
   */
  @Transactional(readOnly = true)
  @Authorized(requiresManager = true)
  public List<String> getNamesOfDefinitionsMentioning(SignsRenamedEvent renamed) {
    var mentioning = reportDefinitionRepository.findAllWithGroup().stream()
        .filter(definition -> renamed.isMentionedIn(definition.getSql()))
        .toList();
    var groups = groupsOf(mentioning);
    return mentioning.stream()
        .map(definition -> groupOf(definition, groups).qualify(definition.getName()))
        .sorted()
        .toList();
  }

  /**
   * Whether another report definition in the group the report goes into already carries this title
   * (#1523, case aside) — also among the reports without a group, which the unique key of the database
   * does not keep apart. Every definition counts, not only those the caller may run.
   *
   * @param groupId the group the form asks for, see {@link #getTargetGroup}
   * @param id      the definition being edited, {@code null} for a new one
   */
  @Transactional(readOnly = true)
  public boolean isNameTaken(String name, Long groupId, Long id) {
    var target = targetGroup(groupId, id);
    if (target.id() == null && target.personal()) {
      return false;
    }
    return reportDefinitionRepository.findAllByGroupAndName(target.id(), name.strip()).stream()
        .anyMatch(definition -> !definition.getId().equals(id));
  }

  /**
   * The group a report lands in when saved (#1523). A manager chooses it freely, also a personal group
   * or none. A people lead who is not a manager has no choice: what they create or edit goes into their
   * personal group — before their first report it does not exist yet, and is named all the same.
   * Whoever writes through an authorization rule leaves the group as it is.
   *
   * @param groupId the group the form asks for; only a manager's counts
   * @param id      the definition being edited, {@code null} for a new one
   */
  @Transactional(readOnly = true)
  public ReportGroupInfo getTargetGroup(Long groupId, Long id) {
    return targetGroup(groupId, id);
  }

  private ReportGroupInfo targetGroup(Long groupId, Long id) {
    if (authorizedUser.isManager()) {
      return groupId == null ? ReportGroupInfo.NONE : reportGroupNames.of(findGroup(groupId));
    }
    if (authorizedUser.isPeopleLead()) {
      var userId = authorizedUser.getEffectiveUserId();
      return reportGroupRepository.findByOwnerUserId(userId)
          .map(reportGroupNames::of)
          .orElseGet(() -> unsavedPersonalGroup(userId));
    }
    return id == null ? ReportGroupInfo.NONE
        : reportDefinitionRepository.findById(id).map(r -> reportGroupNames.of(r.getGroup())).orElse(ReportGroupInfo.NONE);
  }

  private ReportGroupInfo unsavedPersonalGroup(Long userId) {
    return new ReportGroupInfo(null, reportGroupNames.nameOfOwner(userId), true);
  }

  public ReportDefinition create(String name, String sql, Long groupId) {
    if(!reportAuthorization.isAuthorizedForAnyReportDefinition(WRITE)) {
      return null;
    }
    var reportDefinition = new ReportDefinition();
    reportDefinition.setName(name.strip());
    reportDefinition.setSql(sql);
    reportDefinition.setOwner(ownerReferences.salatUser(authorizedUser.getEffectiveUserId()));
    reportDefinition.setGroup(groupFor(groupId, null));
    reportDefinitionRepository.save(reportDefinition);
    return reportDefinition;
  }

  public void update(long reportDefinitionId, String name, String sql, Long groupId) {
    var reportDefinition = reportDefinitionRepository.findById(reportDefinitionId).orElseThrow();
    if(!reportAuthorization.isAuthorized(reportDefinition, WRITE)) {
      return;
    }
    reportDefinition.setName(name.strip());
    reportDefinition.setSql(sql);
    reportDefinition.setGroup(groupFor(groupId, reportDefinition.getGroup()));
    reportDefinitionRepository.save(reportDefinition);
  }

  /**
   * The group as {@link #getTargetGroup} decides it. A people lead gets their personal group with their
   * first report (#1523).
   */
  private ReportGroup groupFor(Long groupId, ReportGroup current) {
    if (authorizedUser.isManager()) {
      return groupId == null ? null : findGroup(groupId);
    }
    if (authorizedUser.isPeopleLead()) {
      var userId = authorizedUser.getEffectiveUserId();
      return reportGroupRepository.findByOwnerUserId(userId).orElseGet(() -> {
        var group = new ReportGroup();
        group.setOwner(ownerReferences.salatUser(userId));
        return reportGroupRepository.save(group);
      });
    }
    return current;
  }

  private ReportGroup findGroup(long groupId) {
    return reportGroupRepository.findById(groupId)
        .orElseThrow(() -> new InvalidDataException(RP_GROUP_NOT_FOUND, String.valueOf(groupId)));
  }

  public ReportResult execute(Long reportDefinitionId, List<ReportParameter> parameters) {
    var reportDefinition = getReportDefinition(reportDefinitionId);
    if(reportDefinition == null) {
      throw new IllegalArgumentException("No report definition found for " + reportDefinitionId);
    }

    var resultBuilder = ReportResult.builder().parameters(parameters);

    String resolvedSql = reportDefinition.getSql();
    if(resolvedSql != null) {
      if (authorizedUser != null && authorizedUser.getEffectiveLoginSign() != null) {
        resolvedSql = resolvedSql.replace("###-AUTH-USER-SIGN-###", authorizedUser.getEffectiveLoginSign()); // ensure the sign is filled in as requested
      }
      // Resolve reporting placeholders based only on today's date (no FROM/UNTIL)
      LocalDate today = DateUtils.today();
      resolvedSql = ReportParameterResolver.resolve(resolvedSql, today);
    }

    try {
      final var rowset = new NamedParameterJdbcTemplate(dataSource)
          .queryForRowSet(resolvedSql, ReportParameters.toParameterMap(parameters));

      // get and create headers
      var columnCount = rowset.getMetaData().getColumnCount();
      var headers = IntStream.rangeClosed(1, columnCount)
              .mapToObj(index -> rowset.getMetaData().getColumnLabel(index))
              .map(ReportResultColumnHeader::new)
              .collect(Collectors.toList());
      resultBuilder.columnHeaders(headers);

      // get and create rows
      var rowAvailable = rowset.first();
      while(rowAvailable) {
        final var row = new ReportResultRow();
        var values = headers.stream()
            .collect(Collectors.toMap(ReportResultColumnHeader::getName, header -> {
              var value = rowset.getObject(header.getName());
              if(!rowset.wasNull() && value.getClass() == java.sql.Date.class) {
                value = ((java.sql.Date) value).toLocalDate();
              }
              return new ReportResultColumnValue(value);
            }));
        row.getColumnValues().putAll(values);
        resultBuilder.row(row);
        rowAvailable = rowset.next();
      }

      return resultBuilder.error(false).sql(resolvedSql).build();
    } catch (DataAccessException e) {
      // Ein fehlgeschlagener Reportlauf ist ein erwartetes Ergebnis der Reportpflege und wird
      // angezeigt, gleich welche Ausnahme die Datenbank meldet (#1110). Nicht auf Exception
      // verbreitern: ein Fehler in der Auswertung der Ergebnismenge bleibt ein Anwendungsfehler.
      var errorInfo = ReportSqlErrors.describe(e);
      log.warn("SQL error while executing report {}: {}", reportDefinitionId, errorInfo.getErrorMessage());
      return ReportResult.builder()
          .parameters(parameters)
          .columnHeaders(List.of())
          .error(true)
          .errorInfo(errorInfo)
          .sql(resolvedSql) // the view offers "Show failing SQL", so the statement has to come along
          .build();
    }
  }

}
