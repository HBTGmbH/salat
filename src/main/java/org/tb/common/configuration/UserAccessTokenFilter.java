package org.tb.common.configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tb.auth.AuthorizedUser;
import org.tb.user.UserAccessTokenService;

@Slf4j
@RequiredArgsConstructor
public class UserAccessTokenFilter extends HttpFilter {

    private final AuthorizedUser authorizedUser;
    private final UserAccessTokenService userAccessTokenService;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        final var apiKeyValue = request.getHeader("x-api-key");
        if (apiKeyValue != null && !apiKeyValue.isBlank()) {
            // apiKeyValue = username:password
            final var tokenIdAndSecret = apiKeyValue.split(":", 2);
            userAccessTokenService.authenticate(tokenIdAndSecret[0], tokenIdAndSecret[1]).ifPresent(authorizedUser::init);
        }

        Object oldValue = request.getAttribute("authorizedUser");
        request.setAttribute("authorizedUser", authorizedUser);
        super.doFilter(request, response, chain);
        request.setAttribute("authorizedUser", oldValue);

    }

}
