package org.tb.persistence;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.tb.bdom.AuthorizedUser;

@Component
@RequiredArgsConstructor
public class AuthorizedUserAuditorAware implements AuditorAware<String> {

  private final AuthorizedUser authorizedUser;

  @Override
  public Optional<String> getCurrentAuditor() {
    if(authorizedUser.isAuthenticated()) {
      return Optional.of(authorizedUser.getSign());
    }
    return Optional.of("system");
  }

}
