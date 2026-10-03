package de.hbt.salat.etl.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.exception.InvalidDataException;

/**
 * {@code ETLService#continueRun} is package-visible since #1289, so that nothing outside the package
 * reaches it. The bean is a CGLIB proxy, and a call the proxy did not pass on would run on the proxy
 * instance itself, whose fields are empty. This pins down that the call arrives at the service: an
 * unknown run is reported as such instead of a {@code NullPointerException}.
 */
@SpringBootTest
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLServiceProxyTest {

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @Autowired
  private ETLService etlService;

  @Test
  void the_package_visible_continuation_reaches_the_service_through_its_proxy() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    var range = new LocalDateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    assertThatThrownBy(() -> etlService.continueRun(-1L, range, List.of(999L)))
        .isInstanceOf(InvalidDataException.class);
  }

}
