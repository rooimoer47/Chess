import { test, expect } from '@playwright/test';
import { TEST_PASSWORD, uniqueUsername, registerViaApi, fillLoginForm, expectLobby } from './fixtures';

test.describe('registration', () => {
  test('happy path: creates an account and lands in the lobby', async ({ page }) => {
    const username = uniqueUsername('register');

    await page.goto('/');
    await page.getByRole('button', { name: 'Create Account' }).click();
    await fillLoginForm(page, username, TEST_PASSWORD);
    await page.getByRole('button', { name: 'Register & Play' }).click();

    await expectLobby(page, username);
  });

  test('rejects a username that is already taken', async ({ page }) => {
    const username = uniqueUsername('dupe');
    await registerViaApi(page, username);
    await page.context().clearCookies();

    await page.goto('/');
    await page.getByRole('button', { name: 'Create Account' }).click();
    await fillLoginForm(page, username, TEST_PASSWORD);
    await page.getByRole('button', { name: 'Register & Play' }).click();

    await expect(page.locator('.login-error')).toHaveText('Username already taken');
    await expect(page).not.toHaveURL(/\/lobby$/);
  });

  test('rejects an invalid username format', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Create Account' }).click();
    await fillLoginForm(page, 'bad username', TEST_PASSWORD);
    await page.getByRole('button', { name: 'Register & Play' }).click();

    await expect(page.locator('.login-error')).toHaveText(
      'Username must be 1-100 characters, no spaces or / \\ ? #',
    );
    await expect(page).not.toHaveURL(/\/lobby$/);
  });
});

test.describe('login', () => {
  test('happy path: signs in with correct credentials', async ({ page }) => {
    const username = uniqueUsername('login');
    await registerViaApi(page, username);
    await page.context().clearCookies();

    await page.goto('/');
    await fillLoginForm(page, username, TEST_PASSWORD);
    await page.getByRole('button', { name: 'Play' }).click();

    await expectLobby(page, username);
  });

  test('rejects an incorrect password', async ({ page }) => {
    const username = uniqueUsername('badpass');
    await registerViaApi(page, username);
    await page.context().clearCookies();

    await page.goto('/');
    await fillLoginForm(page, username, 'not-the-right-password');
    await page.getByRole('button', { name: 'Play' }).click();

    await expect(page.locator('.login-error')).toHaveText('Invalid credentials');
    await expect(page).not.toHaveURL(/\/lobby$/);
  });

  test('rejects an unknown username', async ({ page }) => {
    await page.goto('/');
    await fillLoginForm(page, uniqueUsername('nosuchuser'), TEST_PASSWORD);
    await page.getByRole('button', { name: 'Play' }).click();

    await expect(page.locator('.login-error')).toHaveText('Invalid credentials');
    await expect(page).not.toHaveURL(/\/lobby$/);
  });
});

test('logout returns to the sign-in screen', async ({ page }) => {
  const username = uniqueUsername('logout');
  await registerViaApi(page, username);

  await page.goto('/');
  await expectLobby(page, username);

  await page.getByRole('button', { name: 'Log out' }).click();

  // Logout swaps to the login UI without changing the browser URL (no client-side
  // navigation happens), so assert on the visible screen rather than the URL.
  await expect(page.getByRole('button', { name: 'Sign In' })).toBeVisible();
  await expect(page.getByPlaceholder('Username')).toBeVisible();
});
