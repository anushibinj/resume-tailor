/**
 * The history page's applied/unapplied quick filter, kept in localStorage so it survives a
 * reload -- useful once several runs are queued and reviewed before any is applied to.
 * Deliberately not React state on its own; see auth-token.ts for the same reasoning.
 */
const STORAGE_KEY = "resume-tailor.runs.applied-filter";

export type AppliedFilter = "all" | "applied" | "unapplied";

export function getStoredAppliedFilter(): AppliedFilter {
  if (typeof window === "undefined") {
    return "all";
  }
  try {
    const value = window.localStorage.getItem(STORAGE_KEY);
    return value === "applied" || value === "unapplied" ? value : "all";
  } catch {
    return "all";
  }
}

export function setStoredAppliedFilter(filter: AppliedFilter): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, filter);
  } catch {
    // Private browsing / blocked storage: the choice just won't survive a reload.
  }
}
