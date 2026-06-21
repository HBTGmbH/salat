package org.tb.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.tb.common.filter.UiStateFilter.COOKIE_KEY_LOGIN_SIGN;
import static org.tb.common.filter.UiStateFilter.COOKIE_NAME;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.tb.common.web.LoginSignProvider;
import org.tb.common.web.UiState;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;
import org.tb.common.web.UiStateKeyRegistry;

class UiStateFilterTest {

    private static final UiStateKey KEY = new UiStateKey("contract");
    private static final String PARAM = "contractId";

    private UiState uiState;
    private UiStateKeyRegistry registry;
    private LoginSignProvider loginSignProvider;
    private UiStateFilter filter;

    @BeforeEach
    void setUp() {
        uiState = new UiState();
        UiStateKeyContributor contributor = () -> Map.of(PARAM, KEY);
        registry = new UiStateKeyRegistry(List.of(contributor));
        loginSignProvider = mock(LoginSignProvider.class);
        filter = new UiStateFilter(uiState, registry, loginSignProvider);
    }

    @Test
    void cookieValuesLoadedWhenLoginSignMatches() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("alice");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(COOKIE_NAME, encode("_ls=alice&contract=42")));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, mock(FilterChain.class));

        assertThat(uiState.getValue(KEY)).isEqualTo("42");
    }

    @Test
    void cookieValuesDiscardedWhenLoginSignMismatches() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("bob");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(COOKIE_NAME, encode("_ls=alice&contract=42")));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, mock(FilterChain.class));

        assertThat(uiState.getValue(KEY)).isNull();
    }

    @Test
    void cookieValuesDiscardedWhenLoginSignMissingFromCookie() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("alice");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(COOKIE_NAME, encode("contract=42")));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, mock(FilterChain.class));

        assertThat(uiState.getValue(KEY)).isNull();
    }

    @Test
    void cookieValuesDiscardedWhenUserNotAuthenticated() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn(null);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(COOKIE_NAME, encode("_ls=alice&contract=42")));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, mock(FilterChain.class));

        assertThat(uiState.getValue(KEY)).isNull();
    }

    @Test
    void loginSignWrittenToCookieOnResponse() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("alice");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(COOKIE_NAME, encode("_ls=alice&contract=42")));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, mock(FilterChain.class));

        String setCookie = res.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        String cookieValue = setCookie.split(";")[0].split("=", 2)[1];
        String decoded = new String(Base64.getUrlDecoder().decode(cookieValue), StandardCharsets.UTF_8);
        assertThat(decoded).contains(COOKIE_KEY_LOGIN_SIGN + "=alice");
        assertThat(decoded).contains("contract=42");
    }

    @Test
    void requestParamTakesPrecedenceOverCookie() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("alice");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addParameter(PARAM, "99");
        req.setCookies(new Cookie(COOKIE_NAME, encode("_ls=alice&contract=42")));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, mock(FilterChain.class));

        assertThat(uiState.getValue(KEY)).isEqualTo("99");
    }

    private static String encode(String plain) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }
}
