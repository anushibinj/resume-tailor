"use client";

import { CheckCircle2, Circle, ExternalLink, Link2 } from "lucide-react";

import { cn } from "@/lib/utils";

/**
 * Recorded by the user, not inferred: several runs can be tailored and reviewed before any
 * is actually applied to, so this is a plain toggle rather than something derived from run
 * status. Shared between the history list and a run's own page.
 */
export function AppliedToggle({
  applied,
  onToggle,
  pending,
}: {
  applied: boolean;
  onToggle: () => void;
  pending?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={(event) => {
        event.preventDefault();
        event.stopPropagation();
        onToggle();
      }}
      disabled={pending}
      aria-pressed={applied}
      aria-label={applied ? "Mark not applied" : "Mark applied"}
      className={cn(
        "inline-flex shrink-0 items-center gap-1.5 rounded-sm border px-2.5 py-1 text-xs font-medium",
        "transition-colors disabled:cursor-not-allowed disabled:opacity-45",
        applied
          ? "border-pencil/40 bg-pencil-soft text-pencil"
          : "border-rule-strong text-ink-soft hover:border-pencil hover:text-pencil",
      )}
    >
      {applied ? <CheckCircle2 className="size-3.5" /> : <Circle className="size-3.5" />}
      {applied ? "Applied" : "Mark applied"}
    </button>
  );
}

/**
 * Opens the application link in a new tab when one is set, and offers a `window.prompt`
 * editor for adding or changing it -- a lightweight form is all this needs, and it matches
 * how the rest of the app asks for one-off confirmations (`window.confirm` on delete).
 */
export function ApplicationLinkControl({
  link,
  onSave,
  saving,
}: {
  link: string | null;
  onSave: (link: string) => void;
  saving?: boolean;
}) {
  const edit = (event: React.MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    const next = window.prompt("Job application link", link ?? "");
    if (next === null) return;
    onSave(next.trim());
  };

  if (link) {
    return (
      <span className="inline-flex shrink-0 items-center gap-1">
        <a
          href={link}
          target="_blank"
          rel="noopener noreferrer"
          onClick={(event) => event.stopPropagation()}
          aria-label="Open application link"
          className={cn(
            "inline-flex items-center gap-1.5 rounded-sm border border-rule-strong px-2.5 py-1 text-xs text-ink-soft",
            "transition-colors hover:border-pencil hover:text-pencil",
          )}
        >
          <ExternalLink className="size-3.5" /> Open
        </a>
        <button
          type="button"
          onClick={edit}
          disabled={saving}
          aria-label="Edit application link"
          className="inline-flex items-center rounded-sm border border-transparent p-1.5 text-ink-faint transition-colors hover:text-pencil disabled:cursor-not-allowed disabled:opacity-45"
        >
          <Link2 className="size-3.5" />
        </button>
      </span>
    );
  }

  return (
    <button
      type="button"
      onClick={edit}
      disabled={saving}
      className={cn(
        "inline-flex shrink-0 items-center gap-1.5 rounded-sm border border-dashed border-rule-strong px-2.5 py-1 text-xs text-ink-soft",
        "transition-colors hover:border-pencil hover:text-pencil disabled:cursor-not-allowed disabled:opacity-45",
      )}
    >
      <Link2 className="size-3.5" /> Add link
    </button>
  );
}
