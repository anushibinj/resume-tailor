import { clearToken, getToken } from "./auth-token";
import type {
  AskQuestionRequest,
  AuthResponse,
  AuthUser,
  CreateRunRequest,
  LlmProfile,
  Page,
  ResumeDetail,
  ResumeQuestion,
  ResumeSummary,
  RunDetail,
  RunSummary,
  SaveLlmProfileRequest,
  SaveResumeRequest,
  SuggestionStatus,
  TestConnectionResponse,
} from "./types";

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/** AuthProvider listens for this to drop a stale session without every caller checking status. */
export const UNAUTHORIZED_EVENT = "resume-tailor:unauthorized";

/**
 * The Google sign-in exchange is the only call made without a session, so it is the only one
 * that skips the bearer header and the 401 handling. /api/auth/me is *not* exempt: it
 * identifies the caller from the token, so it must send it (it once didn't, and every reload
 * signed the user out).
 */
function isPublicAuthEndpoint(path: string): boolean {
  return path === "/api/auth/google";
}

/** Carries the backend's message so the UI can show what to fix, not just "request failed". */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

/**
 * Message worth showing a user: a bean-validation failure arrives as "Validation failed"
 * with the offending fields listed separately, which on its own says nothing actionable.
 */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    const fields = Object.entries(error.fieldErrors);
    if (fields.length > 0) {
      return fields.map(([field, message]) => `${field}: ${message}`).join("; ");
    }
    return error.message;
  }
  return error instanceof Error ? error.message : "Something went wrong";
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  const token = getToken();
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      ...init,
      headers: {
        ...(init?.body ? { "Content-Type": "application/json" } : {}),
        ...(token && !isPublicAuthEndpoint(path) ? { Authorization: `Bearer ${token}` } : {}),
        ...init?.headers,
      },
    });
  } catch {
    throw new ApiError(0, `Can't reach the backend at ${BASE_URL}. Is it running?`);
  }

  if (response.status === 401 && !isPublicAuthEndpoint(path)) {
    // The session token is missing, expired or was invalidated -- drop it and let
    // AuthProvider send the user back to the sign-in screen instead of failing silently.
    clearToken();
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readErrorMessage(response), await readFieldErrors(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/** The body can only be read once, so both helpers work from a clone. */
async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.clone().json();
    return body?.message || `Request failed (${response.status})`;
  } catch {
    return `Request failed (${response.status})`;
  }
}

async function readFieldErrors(response: Response): Promise<Record<string, string>> {
  try {
    const body = await response.clone().json();
    return body?.fieldErrors ?? {};
  } catch {
    return {};
  }
}

async function download(path: string, init?: RequestInit): Promise<void> {
  const token = getToken();
  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  });

  if (response.status === 401) {
    clearToken();
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readErrorMessage(response), await readFieldErrors(response));
  }
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const filename = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? "resume";

  const url = URL.createObjectURL(await response.blob());
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export const api = {
  auth: {
    google: (idToken: string) =>
      request<AuthResponse>("/api/auth/google", { method: "POST", body: JSON.stringify({ idToken }) }),
    me: () => request<AuthUser>("/api/auth/me"),
  },
  resumes: {
    list: () => request<ResumeSummary[]>("/api/resumes"),
    get: (id: string) => request<ResumeDetail>(`/api/resumes/${id}`),
    create: (body: SaveResumeRequest) =>
      request<ResumeDetail>("/api/resumes", { method: "POST", body: JSON.stringify(body) }),
    update: (id: string, body: SaveResumeRequest) =>
      request<ResumeDetail>(`/api/resumes/${id}`, { method: "PUT", body: JSON.stringify(body) }),
    setDefault: (id: string) => request<ResumeDetail>(`/api/resumes/${id}/default`, { method: "POST" }),
    remove: (id: string) => request<void>(`/api/resumes/${id}`, { method: "DELETE" }),
    questions: {
      /** Synchronous: answers in this one round trip, unlike a tailoring run. */
      ask: (resumeId: string, body: AskQuestionRequest) =>
        request<ResumeQuestion>(`/api/resumes/${resumeId}/questions`, {
          method: "POST",
          body: JSON.stringify(body),
        }),
      history: (resumeId: string, page = 0, size = 50) =>
        request<Page<ResumeQuestion>>(`/api/resumes/${resumeId}/questions?page=${page}&size=${size}`),
    },
  },
  llmProfiles: {
    list: () => request<LlmProfile[]>("/api/llm-profiles"),
    create: (body: SaveLlmProfileRequest) =>
      request<LlmProfile>("/api/llm-profiles", { method: "POST", body: JSON.stringify(body) }),
    update: (id: string, body: SaveLlmProfileRequest) =>
      request<LlmProfile>(`/api/llm-profiles/${id}`, { method: "PUT", body: JSON.stringify(body) }),
    setDefault: (id: string) => request<LlmProfile>(`/api/llm-profiles/${id}/default`, { method: "POST" }),
    test: (id: string) =>
      request<TestConnectionResponse>(`/api/llm-profiles/${id}/test`, { method: "POST" }),
    remove: (id: string) => request<void>(`/api/llm-profiles/${id}`, { method: "DELETE" }),
  },
  runs: {
    list: (page = 0, size = 20) => request<Page<RunSummary>>(`/api/runs?page=${page}&size=${size}`),
    get: (id: string) => request<RunDetail>(`/api/runs/${id}`),
    create: (body: CreateRunRequest) =>
      request<RunDetail>("/api/runs", { method: "POST", body: JSON.stringify(body) }),
    updateSuggestion: (runId: string, suggestionId: string, status: SuggestionStatus) =>
      request<RunDetail>(`/api/runs/${runId}/suggestions/${suggestionId}`, {
        method: "PATCH",
        body: JSON.stringify({ status }),
      }),
    remove: (id: string) => request<void>(`/api/runs/${id}`, { method: "DELETE" }),
    recheckGaps: (id: string) => request<RunDetail>(`/api/runs/${id}/gaps`, { method: "POST" }),
    pdfAvailable: (id: string) => request<{ enabled: boolean }>(`/api/runs/${id}/pdf/available`),
    downloadSource: (id: string) => download(`/api/runs/${id}/export`),
    compilePdf: (id: string) => download(`/api/runs/${id}/pdf`, { method: "POST" }),
  },
};
