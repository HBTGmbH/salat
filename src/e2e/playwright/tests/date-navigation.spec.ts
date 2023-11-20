import {expect, test} from '@playwright/test';

test('current Date', async ({page}) => {
  await page.goto('/do/ShowDailyReport');
  const date = new Date()
  // Expect the selected date to be the current date
  await expect(page.locator('#calinput1')).toHaveValue(format(date));
});


test('navigate the date on week before', async ({page}) => {
  await page.goto('/do/ShowDailyReport');

  // click on the prevweek button
  await page.getByTestId('skip-prevweek').click();
  const date = addDays(new Date(), -7);
  // Expect the selected date to be one week in the past
  await expect(page.locator('#calinput1')).toHaveValue(format(date));
});

test('navigate the date on day before', async ({page}) => {
  await page.goto('/do/ShowDailyReport');

  // click on the prevweek button
  await page.getByTestId('skip-prevday').click();
  const date = addDays(new Date(), -1);
  // Expect the selected date to be one week in the past
  await expect(page.locator('#calinput1')).toHaveValue(format(date));
});

test('navigate the date on day before and back to today', async ({page}) => {
  await page.goto('/do/ShowDailyReport');

  // click on the prevweek button
  await page.getByTestId('skip-prevday').click();
  await page.getByTestId('skip-today').click();
  const date = new Date();
  // Expect the selected date to be one week in the past
  await expect(page.locator('#calinput1')).toHaveValue(format(date));
});

test('navigate the date on day in the future', async ({page}) => {
  await page.goto('/do/ShowDailyReport');

  // click on the prevweek button
  await page.getByTestId('skip-nextday').click();
  const date = addDays(new Date(), 1);
  // Expect the selected date to be one week in the past
  await expect(page.locator('#calinput1')).toHaveValue(format(date));
});

test('navigate the date on week in the future', async ({page}) => {
  await page.goto('/do/ShowDailyReport');

  // click on the prevweek button
  await page.getByTestId('skip-nextweek').click();
  const date = addDays(new Date(), 7);
  // Expect the selected date to be one week in the past
  await expect(page.locator('#calinput1')).toHaveValue(format(date));
});

function addDays(date: Date, days: number) {
  return new Date(date.getTime() + days * (1000 * 60 * 60 * 24));
}

function format(date) {
  if (!(date instanceof Date)) {
    throw new Error('Invalid "date" argument. You must pass a date instance')
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}