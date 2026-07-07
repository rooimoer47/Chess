import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';

export const TEST_PASSWORD = 'e2e-Test-Pass-123';

export function uniqueUsername(prefix = 'e2e'): string {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

/**
 * Registers a fresh user directly via the API. Runs through the same
 * /api/auth/register endpoint the UI uses, but skips the form so setup
 * for tests that aren't specifically about registration stays fast.
 *
 * Uses `page.request` (not the standalone `request` fixture) because it
 * shares cookie storage with the page's browser context — the auth cookie
 * from this response is then sent automatically on the next page.goto().
 */
export async function registerViaApi(
  page: Page,
  username: string,
  password: string = TEST_PASSWORD,
): Promise<void> {
  const res = await page.request.post('/api/auth/register', {
    data: { username, password },
  });
  if (!res.ok()) {
    throw new Error(`registerViaApi failed (${res.status()}): ${await res.text()}`);
  }
}

/** Logs the browser context out (clears the auth cookie) without touching the UI. */
export async function logoutCookie(page: Page): Promise<void> {
  await page.context().clearCookies();
}

export async function fillLoginForm(page: Page, username: string, password: string): Promise<void> {
  await page.getByPlaceholder('Username').fill(username);
  await page.getByPlaceholder('Password').fill(password);
}

export async function loginViaUi(page: Page, username: string, password: string = TEST_PASSWORD): Promise<void> {
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign In' }).click();
  await fillLoginForm(page, username, password);
  await page.getByRole('button', { name: 'Play' }).click();
  await expect(page).toHaveURL(/\/lobby$/);
}

export async function expectLobby(page: Page, username: string): Promise<void> {
  await expect(page).toHaveURL(/\/lobby$/);
  await expect(page.getByText(`Welcome, ${username}`)).toBeVisible();
}
