package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import de.hbt.salat.customer.service.CustomerService;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.auth.TimereportVisibility;
import de.hbt.salat.dailyreport.auth.TimereportVisibilityService;
import de.hbt.salat.dailyreport.domain.TagSelection;
import de.hbt.salat.dailyreport.domain.TimereportListFilter;
import de.hbt.salat.dailyreport.domain.TimereportListFilter.Billable;
import de.hbt.salat.dailyreport.domain.TimereportListFilter.Sort;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO.Totals;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.jira.service.JiraTicketService;
import de.hbt.salat.order.domain.TaggedTrees;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.OrderTagService;
import de.hbt.salat.order.service.SuborderService;

/**
 * The booking list hands the chosen tags to the order module and searches the trees it names (#847) — for the page
 * and for the export alike. Without tags the order module is not asked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportListTagFilterTest {

  @Mock private TimereportListDAO timereportListDAO;
  @Mock private TimereportDAO timereportDAO;
  @Mock private TimereportVisibilityService visibilityService;
  @Mock private TimereportAuthorization timereportAuthorization;
  @Mock private EmployeeService employeeService;
  @Mock private CustomerService customerService;
  @Mock private CustomerorderService customerorderService;
  @Mock private SuborderService suborderService;
  @Mock private JiraTicketService jiraTicketService;
  @Mock private OrderTagService orderTagService;

  @InjectMocks
  private TimereportListService timereportListService;

  @BeforeEach
  void setUp() {
    when(visibilityService.forPeriod(any())).thenReturn(TimereportVisibility.all());
    when(timereportListDAO.findTotals(any(), any()))
        .thenReturn(new Totals(1, Duration.ofHours(1), Duration.ZERO, 1, 1, Duration.ZERO));
    when(orderTagService.getTaggedTrees(List.of(3L, 4L))).thenReturn(new TaggedTrees(List.of(10L), List.of(20L, 21L)));
  }

  @Test
  void the_tags_are_searched_as_the_trees_they_sit_in() {
    timereportListService.search(filter(TagSelection.of(List.of(3L, 4L))));

    assertThat(searched().tags()).isEqualTo(new TagSelection(List.of(3L, 4L), List.of(10L), List.of(20L, 21L)));
  }

  @Test
  void the_export_searches_the_same_trees() {
    timereportListService.searchAll(filter(TagSelection.of(List.of(3L, 4L))));

    var filter = ArgumentCaptor.forClass(TimereportListFilter.class);
    verify(timereportListDAO).findRows(filter.capture(), any());
    assertThat(filter.getValue().tags().suborderIds()).containsExactly(20L, 21L);
  }

  @Test
  void without_tags_the_order_module_is_not_asked() {
    timereportListService.search(filter(TagSelection.NONE));

    verify(orderTagService, never()).getTaggedTrees(any());
    assertThat(searched().tags().active()).isFalse();
  }

  private TimereportListFilter searched() {
    var filter = ArgumentCaptor.forClass(TimereportListFilter.class);
    verify(timereportListDAO).findTotals(filter.capture(), any());
    return filter.getValue();
  }

  private static TimereportListFilter filter(TagSelection tags) {
    return new TimereportListFilter(List.of(), List.of(), List.of(), List.of(), List.of(), false, tags,
        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), Billable.ALL, Sort.DATE, false, 500);
  }
}
