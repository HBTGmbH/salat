package de.hbt.salat.etl.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.etl.service.ETLService;

/**
 * Executing a single definition through the REST interface (#1289): without an ETL right the answer
 * does not depend on whether the definition exists.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class EtlRestEndpointTest {

  private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 9, 30);

  @Mock
  private ETLService etlService;

  @Mock
  private AuthorizedUser authorizedUser;

  @InjectMocks
  private EtlRestEndpoint endpoint;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
  }

  @ParameterizedTest
  @ValueSource(strings = { "worked-hours", "no-such-definition" })
  void a_login_without_an_etl_right_gets_403_whatever_the_name(String etlName) {
    // the service answers the question of existence only after the right - here it refuses
    when(etlService.isETLExisting(etlName)).thenThrow(new AuthorizationException(AA_NOT_ATHORIZED));

    assertThatThrownBy(() -> endpoint.executeEtl(etlName, FROM, UNTIL))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(FORBIDDEN));

    verify(etlService, never()).execute(any(), any(), any());
  }

}
