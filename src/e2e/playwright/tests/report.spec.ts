import {expect, test} from '@playwright/test';

test('current Date', async ({page}) => {
  await page.goto('/do/ShowDailyReport');
  const date = new Date()
  // Expect the selected date to be the current date
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