/**
 * The app's own session token (issued by POST /api/auth/google), kept in localStorage
 * so a page reload doesn't force a fresh Google sign-in. Deliberately not React state --
 * `api.ts` needs to read it on every request without depending on a component tree.
 */
const STORAGE_KEY = "resume-tailor.auth.token";

export function getToken(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    return window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, token);
  } catch {
    // Private browsing / blocked storage: the session just won't survive a reload.
  }
}

export function clearToken(): void {
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // See setToken.
  }
}
