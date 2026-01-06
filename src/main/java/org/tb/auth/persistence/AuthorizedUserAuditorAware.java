package org.tb.auth.persistence;

import static org.tb.common.GlobalConstants.SYSTEM_SIGN;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.tb.auth.domain.AuthorizedUser;

@Component
@RequiredArgsConstructor
public class AuthorizedUserAuditorAware implements AuditorAware<String> {

  private final BeanFactory beanFactory;

  @Override
  public Optional<String> getCurrentAuditor() {
    var authorizedUser = getAuthorizedUserIfPresent();
    if(authorizedUser.isPresent() && authorizedUser.get().isAuthenticated()) {
      return Optional.of(authorizedUser.get().getLoginSign());
    }
    return Optional.of(SYSTEM_SIGN);
  }

  public Optional<AuthorizedUser> getAuthorizedUserIfPresent() {
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      return Optional.empty(); // no request → no session
    }

    return Optional.of(beanFactory.getBean(AuthorizedUser.class));
  }

}
