"use client";

import { Check, Undo2, X } from "lucide-react";

import type { Suggestion } from "@/lib/types";
import { cn } from "@/lib/utils";
import { Button, Tag } from "./ui";

const KIND_LABEL = {
  BULLET: "Bullet",
  SKILL: "Skill",
  SUMMARY: "Summary",
} as const;

/**
 * Nothing here is in the resume yet. Accepting is the user asserting a claim is true
 * about them, which is why the model is never allowed to apply these on its own.
 */
export function SuggestionMargin({
  suggestions,
  onDecide,
  pendingId,
}: {
  suggestions: Suggestion[];
  onDecide: (id: string, status: Suggestion["status"]) => void;
  pendingId: string | null;
}) {
  if (suggestions.length === 0) {
    return (
      <p className="text-sm text-ink-soft">
        The model didn&apos;t propose anything beyond what your resume already supports.
      </p>
    );
  }

  return (
    <div className="space-y-3">
      <p className="text-xs leading-relaxed text-ink-soft">
        These are not in your resume. Accept one only if it&apos;s genuinely true of you — it
        gets added to the tailored copy, and you can take it back out.
      </p>
      <ul className="space-y-2.5">
        {suggestions.map((suggestion) => {
          const busy = pendingId === suggestion.id;
          const accepted = suggestion.status === "ACCEPTED";
          const rejected = suggestion.status === "REJECTED";

          return (
            <li
              key={suggestion.id}
              className={cn(
                "rounded-sm border p-3",
                accepted ? "border-pencil/40 bg-pencil-soft" : "border-rule bg-surface",
                rejected && "opacity-55",
              )}
            >
              <div className="mb-1.5 flex items-center gap-2">
                <Tag tone={accepted ? "pencil" : "neutral"}>{KIND_LABEL[suggestion.kind]}</Tag>
                {suggestion.targetSection ? (
                  <span className="truncate text-xs text-ink-faint">{suggestion.targetSection}</span>
                ) : null}
              </div>

              <p className={cn("text-sm text-ink", rejected && "line-through")}>{suggestion.content}</p>

              {suggestion.rationale ? (
                <p className="mt-1.5 text-xs text-ink-soft">{suggestion.rationale}</p>
              ) : null}

              <div className="mt-2.5 flex items-center gap-1.5">
                {suggestion.status === "PROPOSED" ? (
                  <>
                    <Button
                      variant="primary"
                      disabled={busy}
                      onClick={() => onDecide(suggestion.id, "ACCEPTED")}
                      className="px-2.5 py-1 text-xs"
                    >
                      <Check className="size-3.5" /> Add it
                    </Button>
                    <Button
                      variant="ghost"
                      disabled={busy}
                      onClick={() => onDecide(suggestion.id, "REJECTED")}
                      className="px-2.5 py-1 text-xs"
                    >
                      <X className="size-3.5" /> Dismiss
                    </Button>
                  </>
                ) : (
                  <Button
                    variant="ghost"
                    disabled={busy}
                    onClick={() => onDecide(suggestion.id, "PROPOSED")}
                    className="px-2.5 py-1 text-xs"
                  >
                    <Undo2 className="size-3.5" />
                    {accepted ? "Remove from resume" : "Undo"}
                  </Button>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
