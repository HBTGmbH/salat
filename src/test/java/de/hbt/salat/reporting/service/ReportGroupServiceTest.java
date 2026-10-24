package de.hbt.salat.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.common.exception.ErrorCode.RP_GROUP_NAME_MISSING;
import static de.hbt.salat.common.exception.ErrorCode.RP_GROUP_NAME_TAKEN;
import static de.hbt.salat.common.exception.ErrorCode.RP_GROUP_PERSONAL_NOT_EMPTY;
import static de.hbt.salat.common.exception.ErrorCode.RP_GROUP_PERSONAL_NOT_RENAMED;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.reporting.domain.ReportGroup;
import de.hbt.salat.reporting.domain.ReportGroupCount;
import de.hbt.salat.reporting.persistence.ReportDefinitionRepository;
import de.hbt.salat.reporting.persistence.ReportGroupRepository;

/** Maintaining report groups (#1523): names unique, personal groups neither renamed nor deleted while in use. */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReportGroupServiceTest {

  @Mock private ReportGroupRepository reportGroupRepository;
  @Mock private ReportDefinitionRepository reportDefinitionRepository;
  @Mock private EmployeeService employeeService;

  private ReportGroupService service;

  @BeforeEach
  void setUp() {
    service = new ReportGroupService(reportGroupRepository, reportDefinitionRepository,
        new ReportGroupNames(employeeService));
  }

  @Test
  void a_new_group_gets_its_name_trimmed() {
    when(reportGroupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    assertThat(service.create("  Controlling ").getName()).isEqualTo("Controlling");
  }

  @Test
  void a_name_another_group_carries_is_refused_case_aside() {
    when(reportGroupRepository.findAllByNameIgnoreCase("controlling")).thenReturn(List.of(group(1L, "Controlling")));

    assertThatThrownBy(() -> service.create("controlling")).satisfies(e -> codeOf(e, RP_GROUP_NAME_TAKEN));
  }

  @Test
  void renaming_a_group_to_its_own_name_is_no_conflict() {
    var group = group(1L, "Controlling");
    when(reportGroupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(reportGroupRepository.findAllByNameIgnoreCase("CONTROLLING")).thenReturn(List.of(group));

    service.rename(1L, "CONTROLLING");

    assertThat(group.getName()).isEqualTo("CONTROLLING");
  }

  @Test
  void a_group_needs_a_name() {
    assertThatThrownBy(() -> service.create(" ")).satisfies(e -> codeOf(e, RP_GROUP_NAME_MISSING));
  }

  @Test
  void a_personal_group_is_not_renamed() {
    when(reportGroupRepository.findById(3L)).thenReturn(Optional.of(personalGroup(3L)));

    assertThatThrownBy(() -> service.rename(3L, "Team")).satisfies(e -> codeOf(e, RP_GROUP_PERSONAL_NOT_RENAMED));
  }

  /** Its reports stay and stand without a group from then on. */
  @Test
  void deleting_a_group_leaves_its_reports_without_one() {
    var group = group(1L, "Controlling");
    when(reportGroupRepository.findById(1L)).thenReturn(Optional.of(group));

    service.delete(1L);

    verify(reportDefinitionRepository).clearGroup(1L);
    verify(reportGroupRepository).delete(group);
  }

  @Test
  void a_personal_group_is_deleted_only_once_empty() {
    var group = personalGroup(3L);
    when(reportGroupRepository.findById(3L)).thenReturn(Optional.of(group));
    when(reportDefinitionRepository.countByGroup()).thenReturn(List.of(new ReportGroupCount(3L, 2L)));

    assertThatThrownBy(() -> service.delete(3L)).satisfies(e -> codeOf(e, RP_GROUP_PERSONAL_NOT_EMPTY));
    verify(reportGroupRepository, never()).delete(any());

    when(reportDefinitionRepository.countByGroup()).thenReturn(List.of());
    service.delete(3L);
    verify(reportGroupRepository).delete(group);
  }

  private static void codeOf(Throwable e, de.hbt.salat.common.exception.ErrorCode code) {
    assertThat(e).isInstanceOf(ErrorCodeException.class);
    assertThat(((ErrorCodeException) e).getMessages().getFirst().getErrorCode()).isEqualTo(code);
  }

  private static ReportGroup group(long id, String name) {
    var group = new ReportGroup();
    ReflectionTestUtils.setField(group, "id", id);
    group.setName(name);
    return group;
  }

  private static ReportGroup personalGroup(long id) {
    var owner = new SalatUser();
    ReflectionTestUtils.setField(owner, "id", 9L);
    var group = new ReportGroup();
    ReflectionTestUtils.setField(group, "id", id);
    group.setOwner(owner);
    return group;
  }
}
