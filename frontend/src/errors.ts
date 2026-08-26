/**
 * The text to show a user for a caught value.
 *
 * Rejections carry Errors rather than bare strings so they keep a stack trace,
 * but `String(err)` on an Error renders as "Error: Failed to load games" —
 * the prefix leaks into the UI. Unwrap the message instead.
 */
export function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}
