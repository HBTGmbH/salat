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
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.tb.common.SalatProperties;
import org.tb.common.web.LoginSignProvider;
import org.tb.common.web.SensitiveUiStateKey;
import org.tb.common.web.UiState;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;
import org.tb.common.web.UiStateKeyRegistry;

class UiStateFilterTest {

    private static final UiStateKey KEY = new UiStateKey("contract");
    private static final String PARAM = "contractId";
    private static final SensitiveUiStateKey SENSITIVE_KEY = new SensitiveUiStateKey("sensitiveKey");
    private static final String SIGNING_KEY = "test-signing-key";

    private UiState uiState;
    private UiStateKeyRegistry registry;
    private LoginSignProvider loginSignProvider;
    private UiStateFilter filter;

    @BeforeEach
    void setUp() {
        uiState = new UiState();
        UiStateKeyContributor contributor = new UiStateKeyContributor() {
            @Override
            public Map<String, UiStateKey> getParamToKeyMappings() {
                return Map.of(PARAM, KEY);
            }
            @Override
            public java.util.Collection<UiStateKey> getAllKeys() {
                return List.of(KEY, SENSITIVE_KEY);
            }
        };
        registry = new UiStateKeyRegistry(List.of(contributor));
        loginSignProvider = mock(LoginSignProvider.class);
        SalatProperties props = new SalatProperties();
        props.getUiState().setSigningKey(SIGNING_KEY);
        filter = new UiStateFilter(uiState, registry, loginSignProvider, props);
        filter.initSigningKey();
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
        req.addParameter(PARAM, "99"); // request param overrides cookie → dirty = true
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, mock(FilterChain.class));

        String setCookie = res.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        String cookieValue = setCookie.split(";")[0].split("=", 2)[1];
        String decoded = new String(Base64.getUrlDecoder().decode(cookieValue), StandardCharsets.UTF_8);
        assertThat(decoded).contains(COOKIE_KEY_LOGIN_SIGN + "=alice");
        assertThat(decoded).contains("contract=99");
    }

    @Test
    void noCookieWrittenWhenStateUnchanged() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("alice");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(COOKIE_NAME, encode("_ls=alice&contract=42")));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, mock(FilterChain.class));

        assertThat(res.getHeader("Set-Cookie")).isNull();
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

    @Test
    void uiStateValuesExposedAsFallbackRequestParameters() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("alice");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(COOKIE_NAME, encode("_ls=alice&contract=42")));
        MockHttpServletResponse res = new MockHttpServletResponse();

        var seenParam = new String[1];
        filter.doFilter(req, res, (chainReq, chainRes) ->
            seenParam[0] = ((HttpServletRequest) chainReq).getParameter(PARAM));

        assertThat(seenParam[0]).isEqualTo("42");
    }

    @Test
    void explicitRequestParamNotOverriddenByUiStateFallback() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("alice");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addParameter(PARAM, "77");
        req.setCookies(new Cookie(COOKIE_NAME, encode("_ls=alice&contract=42")));
        MockHttpServletResponse res = new MockHttpServletResponse();

        var seenParam = new String[1];
        filter.doFilter(req, res, (chainReq, chainRes) ->
            seenParam[0] = ((HttpServletRequest) chainReq).getParameter(PARAM));

        assertThat(seenParam[0]).isEqualTo("77");
    }

    @Test
    void cookieReflectsClearStateDuringChain() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("alice");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(COOKIE_NAME, encode("_ls=alice&contract=42")));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (chainReq, chainRes) -> uiState.clearState(KEY));

        // State was cleared — cookie IS written to remove the old value from the browser.
        String setCookie = res.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        String cookieValue = setCookie.split(";")[0].split("=", 2)[1];
        String decoded = new String(Base64.getUrlDecoder().decode(cookieValue), StandardCharsets.UTF_8);
        assertThat(decoded).contains(COOKIE_KEY_LOGIN_SIGN + "=alice");
        assertThat(decoded).doesNotContain("contract=");
        assertThat(uiState.getValue(KEY)).isNull();
    }

    @Test
    void cookieReflectsSetValueDuringChain() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("alice");

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (chainReq, chainRes) -> uiState.setValue(KEY, "77"));

        String setCookie = res.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        String cookieValue = setCookie.split(";")[0].split("=", 2)[1];
        String decoded = new String(Base64.getUrlDecoder().decode(cookieValue), StandardCharsets.UTF_8);
        assertThat(decoded).contains("contract=77");
    }

    @Test
    void cookieWrittenBeforeRedirectIsIssued() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("alice");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addParameter(PARAM, "55"); // dirty via Phase 2
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (chainReq, chainRes) ->
            ((HttpServletResponse) chainRes).sendRedirect("/next"));

        assertThat(res.getRedirectedUrl()).isEqualTo("/next");
        String setCookie = res.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        String cookieValue = setCookie.split(";")[0].split("=", 2)[1];
        String decoded = new String(Base64.getUrlDecoder().decode(cookieValue), StandardCharsets.UTF_8);
        assertThat(decoded).contains("contract=55");
    }

    @Test
    void sensitiveCookieValueLoadedWhenHmacValid() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("alice");

        String value = "bob";
        String hmac = computeHmac("alice", "sensitiveKey", value);
        String cookieContent = "_ls=alice&sensitiveKey=" + value + "&_sig_sensitiveKey=" + hmac;

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(COOKIE_NAME, encode(cookieContent)));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, mock(FilterChain.class));

        assertThat(uiState.getValue(SENSITIVE_KEY)).isEqualTo("bob");
    }

    @Test
    void sensitiveCookieValueRejectedWhenHmacInvalid() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("alice");

        String cookieContent = "_ls=alice&sensitiveKey=bob&_sig_sensitiveKey=invalidsig";

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(COOKIE_NAME, encode(cookieContent)));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, mock(FilterChain.class));

        assertThat(uiState.getValue(SENSITIVE_KEY)).isNull();
    }

    @Test
    void sensitiveCookieValueRejectedWhenHmacCopiedFromDifferentUser() throws Exception {
        // alice's legitimately-issued HMAC for the same (keyName, value)
        String aliceHmac = computeHmac("alice", "sensitiveKey", "bob");
        // charlie constructs a cookie that uses _ls=charlie but alice's signature
        String cookieContent = "_ls=charlie&sensitiveKey=bob&_sig_sensitiveKey=" + aliceHmac;

        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("charlie");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(COOKIE_NAME, encode(cookieContent)));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, mock(FilterChain.class));

        assertThat(uiState.getValue(SENSITIVE_KEY)).isNull();
    }

    @Test
    void sensitiveCookieValueRejectedWhenHmacMissing() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("alice");

        String cookieContent = "_ls=alice&sensitiveKey=bob";

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(COOKIE_NAME, encode(cookieContent)));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, mock(FilterChain.class));

        assertThat(uiState.getValue(SENSITIVE_KEY)).isNull();
    }

    @Test
    void sensitiveCookieValueWrittenWithHmac() throws Exception {
        when(loginSignProvider.getEffectiveLoginSign()).thenReturn("alice");

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (chainReq, chainRes) -> uiState.setValue(SENSITIVE_KEY, "secret"));

        String setCookie = res.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        String cookieValue = setCookie.split(";")[0].split("=", 2)[1];
        String decoded = new String(Base64.getUrlDecoder().decode(cookieValue), StandardCharsets.UTF_8);
        assertThat(decoded).contains("sensitiveKey=secret");
        assertThat(decoded).contains("_sig_sensitiveKey=");

        String expectedHmac = computeHmac("alice", "sensitiveKey", "secret");
        assertThat(decoded).contains("_sig_sensitiveKey=" + expectedHmac);
    }

    private static String encode(String plain) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    private static String computeHmac(String loginSign, String keyName, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SIGNING_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String message = COOKIE_KEY_LOGIN_SIGN + "=" + loginSign + "&" + keyName + "=" + value;
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
