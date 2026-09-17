"use client";

import type { KeywordImportance, KeywordResult } from "@/lib/types";
import { cn } from "@/lib/utils";

const GROUPS: { importance: KeywordImportance; label: string }[] = [
  { importance: "REQUIRED", label: "Required" },
  { importance: "PREFERRED", label: "Preferred" },
  { importance: "NICE", label: "Nice to have" },
];

export function KeywordMargin({ keywords }: { keywords: KeywordResult[] }) {
  if (keywords.length === 0) {
    return <p className="text-sm text-ink-soft">No keywords were extracted from this posting.</p>;
  }

  return (
    <div className="space-y-5">
      {GROUPS.map((group) => {
        const items = keywords.filter((k) => k.importance === group.importance);
        if (items.length === 0) return null;
        const missing = items.filter((k) => !k.presentInTailored).length;

        return (
          <div key={group.importance}>
            <div className="mb-2 flex items-baseline justify-between gap-2">
              <h3 className="text-sm font-medium text-ink">{group.label}</h3>
              <span className="text-xs text-ink-faint">
                {missing === 0 ? "all covered" : `${missing} missing`}
              </span>
            </div>
            <ul className="flex flex-wrap gap-1.5">
              {items.map((keyword) => (
                <li key={keyword.keyword}>
                  <KeywordChip keyword={keyword} />
                </li>
              ))}
            </ul>
          </div>
        );
      })}
      <p className="border-t border-rule pt-3 text-xs leading-relaxed text-ink-soft">
        Coverage is checked against the resume text itself, not reported by the model.
      </p>
    </div>
  );
}

function KeywordChip({ keyword }: { keyword: KeywordResult }) {
  const gained = keyword.presentInTailored && !keyword.presentInOriginal;

  return (
    <span
      title={
        keyword.presentInTailored
          ? gained
            ? "Surfaced by tailoring — it was in your resume but not prominent"
            : "Already covered"
          : "Not supported by your resume"
      }
      className={cn(
        "inline-flex items-center gap-1 rounded-sm border px-2 py-1 text-xs",
        keyword.presentInTailored
          ? "border-pencil/35 bg-pencil-soft text-pencil"
          : "border-dashed border-gap/50 bg-gap-soft text-gap",
      )}
    >
      {gained ? <span aria-hidden className="text-[10px] leading-none">+</span> : null}
      {keyword.keyword}
    </span>
  );
}
