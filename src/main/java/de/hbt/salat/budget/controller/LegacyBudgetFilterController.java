package de.hbt.salat.budget.controller;

import static org.apache.commons.lang3.StringUtils.trimToNull;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UrlPathHelper;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.order.service.CustomerorderService;

/**
 * Translates the order filter the budget pages carried until #1334, {@code fCustomerOrderSign}, into
 * the id they carry now. Bookmarks, alert mails and in-app notifications already sent still name the
 * order by its sign.
 *
 * <p>A redirect rather than a second parameter in every controller: the address bar then shows the
 * current parameter, and the UiState filter remembers the id on the redirected request like on any
 * other. The mapping only applies where the old parameter is present — Spring prefers it to the
 * mapping of the page itself — and the rest of the query, {@code evaluate} included, is passed on
 * untouched.
 *
 * <p>A sign that no longer names an order — renamed since the link was written — clears the filter
 * instead of leaving it out. Left out, the remembered order would stand in, and a link from an alert
 * mail would evaluate an order nobody asked for.
 */
@Controller
@RequiredArgsConstructor
@Authorized(requireUnrestricted = true)
public class LegacyBudgetFilterController {

    static final String LEGACY_PARAM = "fCustomerOrderSign";
    static final String FILTER_PARAM = "fBudgetCustomerOrderId";

    private final CustomerorderService customerorderService;

    @GetMapping(path = {"/budget", "/budget/pricing", "/budget/flat-rate", "/budget/controlling"},
        params = LEGACY_PARAM)
    public String translate(@RequestParam(LEGACY_PARAM) String customerorderSign, HttpServletRequest request) {
        var customerorderId = customerorderService.getCustomerorderIdBySign(trimToNull(customerorderSign));
        // The query as it came, still encoded: build() leaves it as it is, only the two names change.
        var target = UriComponentsBuilder.fromPath(UrlPathHelper.defaultInstance.getPathWithinApplication(request))
            .query(request.getQueryString())
            .replaceQueryParam(LEGACY_PARAM)
            .replaceQueryParam(FILTER_PARAM, customerorderId == null ? "" : customerorderId)
            .build()
            .toUriString();
        return "redirect:" + target;
    }
}
