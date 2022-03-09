package org.tb.common.configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tb.auth.AuthorizedUser;
import org.tb.employee.Employee;
import org.tb.employee.EmployeeRepository;
import org.tb.user.UserAccessTokenService;

@Slf4j
@RequiredArgsConstructor
public class UserAccessTokenFilter extends HttpFilter {

    private final AuthorizedUser authorizedUser;
    private final UserAccessTokenService userAccessTokenService;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        final var authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.toLowerCase().startsWith("basic")) {
            // Authorization: Basic base64credentials
            var base64Credentials = authorization.substring("Basic".length()).trim();
            var credDecoded = Base64.getDecoder().decode(base64Credentials);
            var credentials = new String(credDecoded, StandardCharsets.UTF_8);
            // credentials = username:password
            final var tokenIdAndSecret = credentials.split(":", 2);
            userAccessTokenService.authenticate(tokenIdAndSecret[0], tokenIdAndSecret[1]).ifPresent(authorizedUser::init);
        }

        Object oldValue = request.getAttribute("authorizedUser");
        request.setAttribute("authorizedUser", authorizedUser);
        super.doFilter(request, response, chain);
        request.setAttribute("authorizedUser", oldValue);

    }

}
