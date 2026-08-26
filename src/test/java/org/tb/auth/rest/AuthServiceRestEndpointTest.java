package org.tb.auth.rest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizationRuleRepository;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.auth.service.AuthService;
import org.tb.auth.service.AuthorizationAspect;
import org.tb.common.SalatProperties;
import org.tb.common.web.UiState;

/**
 * Wires the endpoint against a real {@link AuthService} behind a real {@link AuthorizationAspect}, so the role check
 * of {@code @Authorized(requiresManager = true)} is actually exercised instead of being mocked away.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class AuthServiceRestEndpointTest {

  @Mock
  private AuthorizedUser authorizedUser;

  @Mock
  private AuthorizationRuleRepository authorizationRuleRepository;

  @Mock
  private SalatUserRepository salatUserRepository;

  @Mock
  private SalatProperties salatProperties;

  @Mock
  private ApplicationEventPublisher applicationEventPublisher;

  @Mock
  private UiState uiState;

  private AuthServiceRestEndpoint authServiceRestEndpoint;

  @BeforeEach
  void setUp() {
    var authServiceProps = new SalatProperties.AuthService();
    authServiceProps.setCacheExpiry(Duration.ofHours(1));
    when(salatProperties.getAuthService()).thenReturn(authServiceProps);
    when(authorizedUser.getLoginSign()).thenReturn("auth-sign");

    var authService = new AuthService(
        authorizedUser,
        authorizationRuleRepository,
        salatUserRepository,
        salatProperties,
        applicationEventPublisher,
        uiState
    );
    authService.init();

    var proxyFactory = new AspectJProxyFactory(authService);
    proxyFactory.addAspect(new AuthorizationAspect(authorizedUser));
    authServiceRestEndpoint = new AuthServiceRestEndpoint(proxyFactory.getProxy(), authorizedUser);
  }

  @Test
  void managerClearsTheCache() {
    // Arrange - status bl or adm maps to ROLE_MANAGER
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(true);

    // Act + Assert
    assertThatCode(() -> authServiceRestEndpoint.clearCache()).doesNotThrowAnyException();
  }

  @Test
  void employeeIsForbidden() {
    // Arrange - status ma is authenticated, but no manager
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(false);

    // Act + Assert - 403, not the 500 an escaping AuthorizationException would produce
    assertThatThrownBy(() -> authServiceRestEndpoint.clearCache())
        .isInstanceOf(ResponseStatusException.class)
        .hasFieldOrPropertyWithValue("statusCode", FORBIDDEN);
  }

  @Test
  void unauthenticatedIsRejected() {
    // Arrange
    when(authorizedUser.isAuthenticated()).thenReturn(false);

    // Act + Assert
    assertThatThrownBy(() -> authServiceRestEndpoint.clearCache())
        .isInstanceOf(ResponseStatusException.class)
        .hasFieldOrPropertyWithValue("statusCode", UNAUTHORIZED);
  }

}
