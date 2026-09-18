"use client";

import { Check, Plus, Undo2, X } from "lucide-react";

import { isCovered, type KeywordImportance, type KeywordResult, type Suggestion, type SuggestionStatus } from "@/lib/types";
import { cn } from "@/lib/utils";
import { Button, Spinner, Tag } from "./ui";

const GROUPS: { importance: KeywordImportance; label: string }[] = [
  { importance: "REQUIRED", label: "Required" },
  { importance: "PREFERRED", label: "Preferred" },
  { importance: "NICE", label: "Nice to have" },
];

/**
 * What the posting asks for, and one-click additions for whatever the resume does not
 * show. Coverage is judged by the user's model rather than by matching strings, so each
 * covered requirement carries the line of the resume the judgment rests on.
 */
export function KeywordMargin({
  keywords,
  otherSuggestions,
  onDecide,
  pendingId,
  modelUsed,
  onAddAllMissing,
  addingAll,
}: {
  keywords: KeywordResult[];
  otherSuggestions: Suggestion[];
  onDecide: (id: string, status: SuggestionStatus) => void;
  pendingId: string | null;
  modelUsed: string | null;
  onAddAllMissing?: (suggestionIds: string[]) => void;
  addingAll?: boolean;
}) {
  if (keywords.length === 0 && otherSuggestions.length === 0) {
    return <p className="text-sm text-ink-soft">No requirements were extracted from this posting.</p>;
  }

  const missingIds = keywords
    .filter((k) => !isCovered(k) && k.addition?.status === "PROPOSED")
    .map((k) => k.addition!.id);

  return (
    <div className="space-y-6">
      {onAddAllMissing && missingIds.length > 0 ? (
        <Button
          variant="primary"
          disabled={addingAll}
          onClick={() => onAddAllMissing(missingIds)}
          className="w-full px-2.5 py-1.5 text-xs"
        >
          {addingAll ? <Spinner className="size-3.5" /> : <Plus className="size-3.5" />}
          Add all missing skills to resume
        </Button>
      ) : null}

      {GROUPS.map((group) => {
        const items = keywords.filter((k) => k.importance === group.importance);
        if (items.length === 0) return null;

        const covered = items.filter((k) => k.covered);
        const gaps = items.filter((k) => !k.covered);

        return (
          <section key={group.importance}>
            <div className="mb-2 flex items-baseline justify-between gap-2">
              <h3 className="text-sm font-medium text-ink">{group.label}</h3>
              <span className="text-xs text-ink-faint">
                {gaps.length === 0 ? "all covered" : `${gaps.length} to add`}
              </span>
            </div>

            {covered.length > 0 ? (
              <ul className="mb-2 flex flex-wrap gap-1.5">
                {covered.map((keyword) => (
                  <li key={keyword.keyword}>
                    <span
                      title={keyword.evidence ? `Your resume: "${keyword.evidence}"` : "Covered"}
                      className="inline-flex items-center gap-1 rounded-sm border border-pencil/35 bg-pencil-soft px-2 py-1 text-xs text-pencil"
                    >
                      {keyword.keyword}
                    </span>
                  </li>
                ))}
              </ul>
            ) : null}

            <ul className="space-y-2">
              {gaps.map((keyword) => (
                <li key={keyword.keyword}>
                  <GapRow keyword={keyword} onDecide={onDecide} pendingId={pendingId} extraBusy={addingAll} />
                </li>
              ))}
            </ul>
          </section>
        );
      })}

      {otherSuggestions.length > 0 ? (
        <section className="border-t border-rule pt-4">
          <h3 className="mb-2 text-sm font-medium text-ink">Other additions</h3>
          <ul className="space-y-2">
            {otherSuggestions.map((suggestion) => (
              <li key={suggestion.id}>
                <SuggestionRow suggestion={suggestion} onDecide={onDecide} pendingId={pendingId} />
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      <p className="border-t border-rule pt-3 text-xs leading-relaxed text-ink-soft">
        Checked by {modelUsed ?? "your model"}. Hover anything covered to see the line of your
        resume it read that from.
      </p>
    </div>
  );
}

function GapRow({
  keyword,
  onDecide,
  pendingId,
  extraBusy,
}: {
  keyword: KeywordResult;
  onDecide: (id: string, status: SuggestionStatus) => void;
  pendingId: string | null;
  extraBusy?: boolean;
}) {
  const addition = keyword.addition;
  const added = isCovered(keyword);
  const dismissed = addition?.status === "REJECTED";
  const busy = (addition ? pendingId === addition.id : false) || Boolean(extraBusy);

  return (
    <div
      className={cn(
        "rounded-sm border p-2.5",
        added ? "border-pencil/40 bg-pencil-soft" : "border-dashed border-gap/50 bg-gap-soft",
        dismissed && "border-solid border-rule bg-surface opacity-60",
      )}
    >
      <div className="flex items-center justify-between gap-2">
        <span className={cn("text-sm", added ? "text-pencil" : "text-gap", dismissed && "text-ink-soft")}>
          {keyword.keyword}
        </span>
        {added ? <Tag tone="pencil">Added</Tag> : null}
      </div>

      {addition && addition.content.toLowerCase() !== keyword.keyword.toLowerCase() ? (
        <p className="mt-1 text-xs text-ink-soft">{addition.content}</p>
      ) : null}
      {addition?.targetSection && !dismissed ? (
        <p className="mt-0.5 text-xs text-ink-faint">
          {added ? "In" : "Goes in"} {addition.targetSection}
        </p>
      ) : null}

      {addition ? (
        <div className="mt-2 flex items-center gap-1.5">
          {addition.status === "PROPOSED" ? (
            <>
              <Button
                variant="primary"
                disabled={busy}
                onClick={() => onDecide(addition.id, "ACCEPTED")}
                className="px-2.5 py-1 text-xs"
              >
                <Plus className="size-3.5" /> Add to resume
              </Button>
              <Button
                variant="ghost"
                disabled={busy}
                onClick={() => onDecide(addition.id, "REJECTED")}
                className="px-2 py-1 text-xs"
              >
                <X className="size-3.5" /> Skip
              </Button>
            </>
          ) : (
            <Button
              variant="ghost"
              disabled={busy}
              onClick={() => onDecide(addition.id, "PROPOSED")}
              className="px-2 py-1 text-xs"
            >
              <Undo2 className="size-3.5" />
              {addition.status === "ACCEPTED" ? "Remove" : "Undo"}
            </Button>
          )}
        </div>
      ) : (
        <p className="mt-1 text-xs text-ink-faint">Nothing offered for this one.</p>
      )}
    </div>
  );
}

/** An addition kept from an earlier check, no longer tied to a listed requirement. */
function SuggestionRow({
  suggestion,
  onDecide,
  pendingId,
}: {
  suggestion: Suggestion;
  onDecide: (id: string, status: SuggestionStatus) => void;
  pendingId: string | null;
}) {
  const accepted = suggestion.status === "ACCEPTED";
  const busy = pendingId === suggestion.id;

  return (
    <div
      className={cn(
        "rounded-sm border p-2.5",
        accepted ? "border-pencil/40 bg-pencil-soft" : "border-rule bg-surface",
        suggestion.status === "REJECTED" && "opacity-60",
      )}
    >
      <p className="text-sm text-ink">{suggestion.content}</p>
      {suggestion.rationale ? (
        <p className="mt-1 text-xs text-ink-soft">{suggestion.rationale}</p>
      ) : null}
      <div className="mt-2 flex items-center gap-1.5">
        {suggestion.status === "PROPOSED" ? (
          <>
            <Button
              variant="primary"
              disabled={busy}
              onClick={() => onDecide(suggestion.id, "ACCEPTED")}
              className="px-2.5 py-1 text-xs"
            >
              <Check className="size-3.5" /> Add to resume
            </Button>
            <Button
              variant="ghost"
              disabled={busy}
              onClick={() => onDecide(suggestion.id, "REJECTED")}
              className="px-2 py-1 text-xs"
            >
              <X className="size-3.5" /> Skip
            </Button>
          </>
        ) : (
          <Button
            variant="ghost"
            disabled={busy}
            onClick={() => onDecide(suggestion.id, "PROPOSED")}
            className="px-2 py-1 text-xs"
          >
            <Undo2 className="size-3.5" /> {accepted ? "Remove" : "Undo"}
          </Button>
        )}
      </div>
    </div>
  );
}
