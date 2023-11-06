package org.tb;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;

public class ReportTest {
  public static void main(String[] args) {
    try (Playwright playwright = Playwright.create()) {
      long timestamp = System.currentTimeMillis();
      Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
          .setHeadless(false));
      BrowserContext context = browser.newContext();
      Page page = context.newPage();
      page.navigate("http://localhost:8080/do/ShowDailyReport");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Neue Buchung")).click();
      page.locator("textarea[name=\"comment\"]").click();
      page.locator("textarea[name=\"comment\"]").fill("test: "+timestamp);
      page.locator("select[name=\"selectedHourDuration\"]").selectOption("1");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Speichern").setExact(true)).click();


      assertThat(page.getByText( "test: "+timestamp)).isVisible();
      assertThat(page.locator( "[name=\"comment\"]").last()).containsText( "test: "+timestamp);
      page.onceDialog(dialog -> {
        System.out.println(String.format("Dialog message: %s", dialog.message()));
        dialog.accept();
      });
      page.locator(".timereports tr.timereport .function-delete").last().click();
      assertThat(page.locator( "[name=\"comment\"]").last()).not().containsText( "test: "+timestamp);
    }
  }
}