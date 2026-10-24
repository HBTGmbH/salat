package de.hbt.salat.reporting.service;

import static de.hbt.salat.common.exception.ErrorCode.RP_GROUP_NAME_MISSING;
import static de.hbt.salat.common.exception.ErrorCode.RP_GROUP_NAME_TAKEN;
import static de.hbt.salat.common.exception.ErrorCode.RP_GROUP_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.RP_GROUP_PERSONAL_NOT_EMPTY;
import static de.hbt.salat.common.exception.ErrorCode.RP_GROUP_PERSONAL_NOT_RENAMED;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.reporting.domain.ReportGroup;
import de.hbt.salat.reporting.domain.ReportGroupCount;
import de.hbt.salat.reporting.domain.ReportGroupInfo;
import de.hbt.salat.reporting.persistence.ReportDefinitionRepository;
import de.hbt.salat.reporting.persistence.ReportGroupRepository;

/**
 * Report groups as managers maintain them (#1523): create, rename, delete, and every group with the
 * number of its reports. Which groups somebody else sees follows from the reports they may run
 * ({@link ReportService#getOverview}).
 */
@Service
@Transactional
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class ReportGroupService {

  private final ReportGroupRepository reportGroupRepository;
  private final ReportDefinitionRepository reportDefinitionRepository;
  private final ReportGroupNames reportGroupNames;

  /** A group with the number of all its reports, not only of those the manager would run. */
  public record ManagedGroup(ReportGroupInfo group, long reports) {
  }

  /** Every group in the order the pages list them in, the personal ones with the name of their person. */
  @Transactional(readOnly = true)
  public List<ReportGroupInfo> getGroups() {
    return reportGroupNames.of(reportGroupRepository.findAllGroups()).values().stream()
        .sorted(ReportGroupInfo.ORDER)
        .toList();
  }

  /** {@link #getGroups()} with the number of reports in each, for the page that maintains them. */
  @Transactional(readOnly = true)
  public List<ManagedGroup> getManagedGroups() {
    var counts = reportDefinitionRepository.countByGroup().stream()
        .collect(Collectors.toMap(ReportGroupCount::groupId, ReportGroupCount::reports));
    return getGroups().stream()
        .map(group -> new ManagedGroup(group, counts.getOrDefault(group.id(), 0L)))
        .toList();
  }

  @Transactional(readOnly = true)
  public ManagedGroup getManagedGroup(long id) {
    return getManagedGroups().stream()
        .filter(group -> group.group().id() == id)
        .findFirst()
        .orElseThrow(() -> new InvalidDataException(RP_GROUP_NOT_FOUND, String.valueOf(id)));
  }

  public ReportGroup create(String name) {
    var group = new ReportGroup();
    group.setName(checkedName(name, null));
    return reportGroupRepository.save(group);
  }

  public void rename(long id, String name) {
    var group = find(id);
    if (group.isPersonal()) {
      throw new BusinessRuleException(RP_GROUP_PERSONAL_NOT_RENAMED);
    }
    group.setName(checkedName(name, id));
    reportGroupRepository.save(group);
  }

  /**
   * Deletes the group; its reports stay and stand without a group from then on. A personal group goes
   * only once it is empty: its people lead would get it back with the next report anyway, and the reports
   * it holds would lose the one place that marks them as theirs.
   */
  public void delete(long id) {
    var group = find(id);
    if (group.isPersonal() && reportDefinitionRepository.countByGroup().stream()
        .anyMatch(count -> count.groupId() == id && count.reports() > 0)) {
      throw new BusinessRuleException(RP_GROUP_PERSONAL_NOT_EMPTY);
    }
    reportDefinitionRepository.clearGroup(id);
    reportGroupRepository.delete(group);
  }

  private ReportGroup find(long id) {
    return reportGroupRepository.findById(id)
        .orElseThrow(() -> new InvalidDataException(RP_GROUP_NOT_FOUND, String.valueOf(id)));
  }

  /** Trimmed; unique among the groups managers maintain, case aside. */
  private String checkedName(String name, Long id) {
    var trimmed = name == null ? "" : name.strip();
    if (trimmed.isEmpty()) {
      throw new InvalidDataException(RP_GROUP_NAME_MISSING);
    }
    if (reportGroupRepository.findAllByNameIgnoreCase(trimmed).stream().anyMatch(group -> !group.getId().equals(id))) {
      throw new BusinessRuleException(RP_GROUP_NAME_TAKEN);
    }
    return trimmed;
  }
}
