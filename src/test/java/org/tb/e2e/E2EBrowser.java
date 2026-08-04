package org.tb.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.Playwright;

/**
 * The two real browsers the dailyreport E2E suite must cover: an actually installed local
 * Google Chrome (via Playwright's {@code channel} option) and Playwright's managed Firefox
 * build.
 */
public enum E2EBrowser {

  CHROME {
    @Override
    public Browser launch(Playwright playwright) {
      return playwright.chromium().launch(new LaunchOptions().setChannel("chrome").setHeadless(true));
    }
  },
  FIREFOX {
    @Override
    public Browser launch(Playwright playwright) {
      return playwright.firefox().launch(new LaunchOptions().setHeadless(true));
    }
  };

  public abstract Browser launch(Playwright playwright);

}
