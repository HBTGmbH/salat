package org.tb.employee.filter;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.filter.RequestPaths;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.employee.domain.EmployeeAccessDenial;

/**
 * Beantwortet eine Anfrage mit {@code 403}, wenn zum Anmeldenamen kein Mitarbeiter oder kein heute
 * gültiger Vertrag gehört. Die Bedingung stellt {@code AuthorizedUserChangedListener} fest, sobald
 * die Authentifizierung gelingt — also in der Filterkette und vor jedem Controller.
 *
 * <p>Der Filter steht hinter der Sicherheitskette und damit an der einzigen Stelle, an der beides
 * zugleich gilt: die Bedingung ist bekannt, und die Antwort kann noch den Weg über die Fehlerseite
 * nehmen. {@code sendError} überlässt dem Servlet-Container die Weiterleitung auf {@code /error};
 * in dieser Weiterleitung greift der Filter nicht mehr (siehe {@link
 * #shouldNotFilterErrorDispatch()}), sonst stünde am Ende wieder die 500-Seite von Tomcat (#1054).
 */
@Component
@RequiredArgsConstructor
@Order(104)
public class EmployeeAccessFilter extends OncePerRequestFilter {

    /**
     * Der Weg zurück aus einer übernommenen Anmeldung. Ohne einen gültigen Vertrag der übernommenen
     * Person wäre sonst auch der Knopf gesperrt, der die Übernahme beendet — und die eigene Anmeldung
     * bliebe nur über das Löschen des Cookies erreichbar.
     */
    private static final String EXIT_IMPERSONATION_PATH = "/auth/exit-impersonation";

    private static final JacksonJsonHttpMessageConverter JSON = new JacksonJsonHttpMessageConverter();

    private final EmployeeAccessDenial employeeAccessDenial;
    private final ErrorCodeViewHelper errorCodeViewHelper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (!employeeAccessDenial.isDenied()) {
            filterChain.doFilter(request, response);
            return;
        }

        ServiceFeedbackMessage reason = employeeAccessDenial.getReason();
        if (RequestPaths.isStateless(request)) {
            writeProblemDetail(response, reason);
        } else {
            // Die Meldung landet über jakarta.servlet.error.message auf der Fehlerseite und nennt
            // dort den Grund - ein blosser Statuscode taete das nicht.
            response.sendError(FORBIDDEN.value(), errorCodeViewHelper.toViewMessage(reason).resolved());
        }
    }

    private void writeProblemDetail(HttpServletResponse response, ServiceFeedbackMessage reason) throws IOException {
        var problemDetail = ProblemDetail.forStatus(FORBIDDEN);
        problemDetail.setTitle(reason.getErrorCode().getCode());
        problemDetail.setDetail(reason.getErrorCode().getMessage());
        response.setStatus(FORBIDDEN.value());
        JSON.write(problemDetail, APPLICATION_PROBLEM_JSON, new ServletServerHttpResponse(response));
    }

    /**
     * Statische Dateien tragen keinen Benutzerzustand und werden weiter ausgeliefert: die
     * Fehlerseite holt sich ihr Stylesheet über dieselbe Kette, und ohne sie wäre die Antwort auf
     * diesen Fehler eine unformatierte Seite.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return RequestPaths.isStaticResource(request)
            || EXIT_IMPERSONATION_PATH.equals(request.getServletPath());
    }

    /**
     * In der Weiterleitung auf {@code /error} gelingt die Authentifizierung erneut, und die Bedingung
     * steht erneut fest. Wer sie dort ein zweites Mal beantwortet, beantwortet die Antwort — Tomcat
     * gibt dann auf und schickt seine eigene 500-Seite. Der Standard von {@link OncePerRequestFilter}
     * tut genau das Richtige; er steht hier, weil er hier die Zusage des Tickets ist.
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

}
