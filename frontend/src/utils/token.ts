export function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.exp === 'number' && payload.exp * 1000 < Date.now();
  } catch {
    return true;
  }
}

export function usernameFromToken(token: string): string | null {
  try {
    return JSON.parse(atob(token.split('.')[1])).sub as string;
  } catch {
    return null;
  }
}
