package org.tb.auth.domain;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public class ImpersonatedAuthentication extends AbstractAuthenticationToken {

    private final Authentication original;

    public ImpersonatedAuthentication(Authentication original,
                                      Collection<? extends GrantedAuthority> impersonatedAuthorities) {
        super(impersonatedAuthorities);
        this.original = original;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() { return original.getCredentials(); }

    @Override
    public Object getPrincipal() { return original.getPrincipal(); }

    @Override
    public String getName() { return original.getName(); }

    public Authentication getOriginal() { return original; }
}
