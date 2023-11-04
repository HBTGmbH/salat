import {expect, test} from '@playwright/test';

test('starts at current Date', async ({ page }) => {
  await page.goto('/do/ShowDailyReport');
const date=    format(new Date())
  // Expect a title "to contain" a substring.
  await expect(page.locator('#calinput1' )).toHaveValue(date);
});


test('get started link', async ({ page }) => {
  await page.goto('https://playwright.dev/');

  // Click the get started link.
  await page.getByRole('link', { name: 'Get started' }).click();

  // Expects page to have a heading with the name of Installation.
  await expect(page.getByRole('heading', { name: 'Installation' })).toBeVisible();
});

function format (date) {
  if (!(date instanceof Date)) {
    throw new Error('Invalid "date" argument. You must pass a date instance')
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}