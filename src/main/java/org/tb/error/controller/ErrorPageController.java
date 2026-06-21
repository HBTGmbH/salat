package org.tb.error.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.web.UiState;
import org.tb.employee.domain.AuthorizedEmployee;

@Controller
@RequiredArgsConstructor
public class ErrorPageController implements ErrorController {

    private final ErrorAttributes errorAttributes;
    private final AuthorizedUser authorizedUser;
    private final AuthorizedEmployee authorizedEmployee;
    private final UiState uiState;

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        var opts = ErrorAttributeOptions.of(Include.EXCEPTION, Include.STACK_TRACE, Include.MESSAGE);
        var webRequest = new ServletWebRequest(request);
        Map<String, Object> attrs = errorAttributes.getErrorAttributes(webRequest, opts);

        int status = attrs.get("status") instanceof Integer s ? s : 0;
        String error = (String) attrs.getOrDefault("error", "");
        String message = (String) attrs.getOrDefault("message", "");
        String exception = (String) attrs.getOrDefault("exception", "");
        String trace = (String) attrs.getOrDefault("trace", null);
        Object ts = attrs.get("timestamp");
        Date timestamp = ts instanceof Date d ? d : new Date();

        String requestPath = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object methodAttr = request.getAttribute("javax.servlet.error.request_method");
        String requestMethod = methodAttr != null ? methodAttr.toString() : request.getMethod();
        String queryString = (String) request.getAttribute("javax.servlet.forward.query_string");
        String contentType = request.getContentType();

        Map<String, String> parameters = new TreeMap<>();
        Map<String, String[]> paramMap = request.getParameterMap();
        if (paramMap != null) {
            paramMap.forEach((k, v) -> {
                if (v != null && v.length > 0) {
                    parameters.put(k, v[0]);
                }
            });
        }

        boolean authenticated = authorizedUser.isAuthenticated();
        String loginSign = authorizedUser.getLoginSign();
        String effectiveLoginSign = authorizedUser.getEffectiveLoginSign();
        String loginStatus = authorizedUser.getLoginStatus();

        String employeeName = authorizedEmployee.getName();
        String employeeSign = authorizedEmployee.getSign();
        Long employeeId = authorizedEmployee.getEmployeeId();
        String employeeEmail = authorizedEmployee.getEmailAddress();

        var errorInfo = new ErrorInfo(
            status, error, message, exception, trace, timestamp,
            requestPath, requestMethod, queryString, parameters, contentType,
            authenticated, loginSign, effectiveLoginSign, loginStatus,
            authorizedUser.isAdmin(), authorizedUser.isManager(),
            authorizedUser.isPeopleLead(), authorizedUser.isBackoffice(),
            employeeName, employeeSign, employeeId, employeeEmail,
            uiState.getAll().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    e -> e.getKey().getName(), Map.Entry::getValue, (a, b) -> a, TreeMap::new))
        );

        model.addAttribute("errorInfo", errorInfo);
        model.addAttribute("errorDetailsText", buildDetailsText(errorInfo));
        model.addAttribute("pageTitle", "Error " + (status > 0 ? status : ""));
        model.addAttribute("sectionTitle", "System");
        model.addAttribute("title", "Fehler");
        return "error/error";
    }

    private String buildDetailsText(ErrorInfo e) {
        var fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        var sb = new StringBuilder();
        sb.append("=== SALAT Fehlerdetails ===\n");
        sb.append("Zeitstempel:  ").append(fmt.format(e.timestamp())).append("\n");
        sb.append("HTTP-Status:  ").append(e.status()).append(" ").append(e.error()).append("\n");
        if (notEmpty(e.exception())) sb.append("Ausnahme:     ").append(e.exception()).append("\n");
        if (notEmpty(e.message()))   sb.append("Meldung:      ").append(e.message()).append("\n");
        sb.append("\n--- Anfrage ---\n");
        if (notEmpty(e.requestMethod())) sb.append("Methode: ").append(e.requestMethod()).append("\n");
        if (notEmpty(e.requestPath()))   sb.append("URL:     ").append(e.requestPath()).append("\n");
        if (notEmpty(e.queryString()))   sb.append("Query:   ").append(e.queryString()).append("\n");
        if (notEmpty(e.contentType()))   sb.append("Content: ").append(e.contentType()).append("\n");
        if (e.parameters() != null && !e.parameters().isEmpty()) {
            sb.append("Parameter:\n");
            e.parameters().forEach((k, v) -> sb.append("  ").append(k).append("=").append(v).append("\n"));
        }
        sb.append("\n--- Benutzer ---\n");
        if (e.authenticated()) {
            sb.append("Login:       ").append(e.loginSign()).append("\n");
            if (notEmpty(e.effectiveLoginSign()) && !e.effectiveLoginSign().equals(e.loginSign())) {
                sb.append("Impersoniert: ").append(e.effectiveLoginSign()).append("\n");
            }
            if (notEmpty(e.loginStatus())) sb.append("Rolle:       ").append(e.loginStatus()).append("\n");
            if (notEmpty(e.employeeName())) {
                sb.append("Mitarbeiter: ").append(e.employeeName())
                  .append(" (").append(e.employeeSign()).append(", id: ").append(e.employeeId()).append(")\n");
            }
        } else {
            sb.append("Login: nicht angemeldet\n");
        }
        if (e.uiState() != null && !e.uiState().isEmpty()) {
            sb.append("\n--- UI-Zustand ---\n");
            e.uiState().forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
        }
        if (notEmpty(e.trace())) {
            sb.append("\n--- Stacktrace ---\n").append(e.trace());
        }
        return sb.toString();
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isBlank();
    }

    public record ErrorInfo(
        int status,
        String error,
        String message,
        String exception,
        String trace,
        Date timestamp,
        String requestPath,
        String requestMethod,
        String queryString,
        Map<String, String> parameters,
        String contentType,
        boolean authenticated,
        String loginSign,
        String effectiveLoginSign,
        String loginStatus,
        boolean admin,
        boolean manager,
        boolean peopleLead,
        boolean backoffice,
        String employeeName,
        String employeeSign,
        Long employeeId,
        String employeeEmail,
        Map<String, String> uiState
    ) {}

}
