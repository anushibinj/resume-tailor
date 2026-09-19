/** Mirrors the backend DTOs in com.resumetailor.*. */

export type Role = "NORMAL_USER" | "ORG_ADMIN" | "ADMIN";

export interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  pictureUrl: string | null;
  role: Role;
}

export interface AuthResponse {
  token: string;
  user: AuthUser;
}

export type ResumeFormat = "LATEX" | "MARKDOWN";
export type RunStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
export type KeywordImportance = "REQUIRED" | "PREFERRED" | "NICE";
export type SuggestionKind = "BULLET" | "SKILL" | "SUMMARY";
export type SuggestionStatus = "PROPOSED" | "ACCEPTED" | "REJECTED";
/** Gap analysis runs after the rewrite, so it has its own status. */
export type GapsStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED" | "OUTDATED";

export interface ResumeSummary {
  id: string;
  name: string;
  format: ResumeFormat;
  isDefault: boolean;
  sectionCount: number;
  characterCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface SectionSummary {
  title: string;
  level: number;
  characterCount: number;
}

export interface ResumeDetail extends Omit<ResumeSummary, "sectionCount" | "characterCount"> {
  sourceText: string;
  preamble: string;
  bodyText: string;
  documentTail: string;
  sections: SectionSummary[];
}

export interface SaveResumeRequest {
  name: string;
  sourceText: string;
  format?: ResumeFormat | null;
  makeDefault?: boolean;
}

export interface LlmProfile {
  id: string;
  name: string;
  baseUrl: string;
  model: string;
  temperature: number;
  maxOutputTokens: number;
  isDefault: boolean;
  hasApiKey: boolean;
  /** Masked tail of the key, e.g. "••••4f2a". The key itself never leaves the backend. */
  apiKeyMask: string;
  createdAt: string;
  updatedAt: string;
}

export interface SaveLlmProfileRequest {
  name: string;
  baseUrl: string;
  /** Leave blank when editing to keep the stored key. */
  apiKey?: string;
  model: string;
  temperature?: number;
  maxOutputTokens?: number;
  makeDefault?: boolean;
}

export interface TestConnectionResponse {
  ok: boolean;
  message: string;
  model: string | null;
  latencyMs: number;
}

export interface SectionDiff {
  title: string;
  level: number;
  originalContent: string;
  tailoredContent: string;
  changeType: string | null;
  rationale: string | null;
}

export interface Suggestion {
  id: string;
  kind: SuggestionKind;
  /** The requirement this addition addresses; null for ones made before gap analysis. */
  keyword: string | null;
  targetSection: string | null;
  content: string;
  rationale: string | null;
  status: SuggestionStatus;
}

export interface KeywordResult {
  keyword: string;
  importance: KeywordImportance;
  /** The model's judgment, with evidence quoted from the resume. */
  covered: boolean;
  evidence: string | null;
  /** Offered when the requirement is not covered, so the gap can be closed in one click. */
  addition: Suggestion | null;
  /**
   * What the requirement is, in plain words. Comes from a glossary shared by every user,
   * so it is null only until some model has explained the term.
   */
  description: string | null;
}

/** A requirement counts as covered once its offered addition has been accepted. */
export function isCovered(keyword: KeywordResult): boolean {
  return keyword.covered || keyword.addition?.status === "ACCEPTED";
}

export interface RunSummary {
  id: string;
  status: RunStatus;
  company: string | null;
  role: string | null;
  resumeName: string | null;
  format: ResumeFormat;
  matchScore: number | null;
  createdAt: string;
  finishedAt: string | null;
}

export interface RunDetail {
  id: string;
  status: RunStatus;
  gapsStatus: GapsStatus;
  gapsError: string | null;
  format: ResumeFormat;
  company: string | null;
  role: string | null;
  resumeId: string;
  resumeName: string | null;
  jobDescriptionId: string;
  jobDescriptionText: string | null;
  originalSource: string;
  tailoredSource: string | null;
  modelUsed: string | null;
  promptTokens: number | null;
  completionTokens: number | null;
  matchScore: number | null;
  errorMessage: string | null;
  sections: SectionDiff[];
  keywords: KeywordResult[];
  /** Additions not tied to a current requirement, e.g. kept from an earlier check. */
  otherSuggestions: Suggestion[];
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface CreateRunRequest {
  resumeId?: string | null;
  jobDescriptionId?: string | null;
  jdText?: string;
  company?: string;
  role?: string;
  sourceUrl?: string;
  llmProfileId?: string | null;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export type QuestionStatus = "COMPLETED" | "FAILED";

/**
 * One single-shot question about a resume and its answer. There is no thread id: every
 * question is answered from the resume alone, never from earlier questions, so this is a
 * flat history entry, not a conversation turn.
 */
export interface ResumeQuestion {
  id: string;
  resumeId: string;
  question: string;
  answer: string | null;
  status: QuestionStatus;
  errorMessage: string | null;
  modelUsed: string | null;
  createdAt: string;
}

export interface AskQuestionRequest {
  question: string;
  llmProfileId?: string | null;
}
