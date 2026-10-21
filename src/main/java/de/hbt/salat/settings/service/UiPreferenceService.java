package de.hbt.salat.settings.service;

import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class UiPreferenceService {

    static final String MODULE_KEY = "ui";
    static final String KEY_LOCALE = "locale";

    private final UserPreferenceService userPreferenceService;

    public static final String BROWSER_DETECTION = "-browser-";

    @Transactional(readOnly = true)
    public String getLocaleForCurrentUser() {
        var val = userPreferenceService.getModuleSettings(MODULE_KEY).get(KEY_LOCALE);
        var stored = val != null ? val.toString() : "";
        return stored.isBlank() ? BROWSER_DETECTION : stored;
    }

    /**
     * The language a login chose, for a mail to it (#828); German where the browser decides, since
     * a mail knows no browser.
     */
    @Transactional(readOnly = true)
    public Locale getLocaleFor(long salatUserId) {
        var val = userPreferenceService.getModuleSettings(salatUserId, MODULE_KEY).get(KEY_LOCALE);
        return "en".equals(val) ? Locale.ENGLISH : Locale.GERMAN;
    }

    public void saveLocaleForCurrentUser(String locale) {
        boolean isBrowser = locale == null || locale.isBlank() || BROWSER_DETECTION.equals(locale);
        var settings = isBrowser
            ? Map.<String, Object>of()
            : Map.<String, Object>of(KEY_LOCALE, locale);
        userPreferenceService.saveModuleSettings(MODULE_KEY, settings);
    }

}
