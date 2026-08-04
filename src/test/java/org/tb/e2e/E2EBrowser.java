package org.tb.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.Playwright;

/**
 * The two real browsers the dailyreport E2E suite must cover: an actually installed local
 * Google Chrome (via Playwright's {@code channel} option) and Playwright's managed Firefox
 * build.
 *
 * <p>Runs headless by default. For local, visible debugging, set
 * {@code -De2e.headless=false} (optionally with {@code -De2e.slowmo=500}, milliseconds of
 * delay Playwright inserts between actions) so you can watch the browser step through each
 * test, e.g.:
 * {@code jenv exec ./mvnw test -Pe2e -De2e.headless=false -De2e.slowmo=500}
 */
public enum E2EBrowser {

  CHROME {
    @Override
    public Browser launch(Playwright playwright) {
      return playwright.chromium().launch(launchOptions().setChannel("chrome"));
    }
  },
  FIREFOX {
    @Override
    public Browser launch(Playwright playwright) {
      return playwright.firefox().launch(launchOptions());
    }
  };

  public abstract Browser launch(Playwright playwright);

  private static LaunchOptions launchOptions() {
    boolean headless = !"false".equalsIgnoreCase(System.getProperty("e2e.headless", "true"));
    double slowMo = Double.parseDouble(System.getProperty("e2e.slowmo", "0"));
    return new LaunchOptions().setHeadless(headless).setSlowMo(slowMo);
  }

}
