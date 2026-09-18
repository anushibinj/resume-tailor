"use client";

import { isCovered, type KeywordImportance, type KeywordResult } from "@/lib/types";
import { cn } from "@/lib/utils";

/** Required requirements are drawn tallest, so a missing one is visible at a glance. */
const TICK_HEIGHT: Record<KeywordImportance, string> = {
  REQUIRED: "h-7",
  PREFERRED: "h-5",
  NICE: "h-3.5",
};

/**
 * The match score, drawn as the data rather than as a summary of it: one tick per
 * keyword the posting asks for, filled when the tailored resume covers it. Reading
 * left to right you can see *which* requirements are missing, not just how many.
 */
export function CoverageMeter({
  keywords,
  score,
  className,
}: {
  keywords: KeywordResult[];
  score: number | null;
  className?: string;
}) {
  if (keywords.length === 0) {
    return null;
  }
  const covered = keywords.filter(isCovered).length;

  return (
    <div className={cn("flex items-end gap-4", className)}>
      <div className="flex items-end gap-[3px]" role="img" aria-label={`${covered} of ${keywords.length} job requirements covered`}>
        {keywords.map((keyword) => (
          <span
            key={keyword.keyword}
            title={`${keyword.keyword} — ${keyword.importance.toLowerCase()}, ${
              isCovered(keyword) ? "covered" : "not in your resume"
            }`}
            className={cn(
              "w-[5px] rounded-[1px] border",
              TICK_HEIGHT[keyword.importance],
              isCovered(keyword)
                ? "border-pencil bg-pencil"
                : "border-dashed border-gap bg-transparent",
            )}
          />
        ))}
      </div>
      <div className="leading-none">
        <div className="font-serif text-2xl text-ink">{score ?? "—"}<span className="text-base text-ink-faint">%</span></div>
        <div className="mt-1 text-xs text-ink-soft">
          {covered} of {keywords.length} covered
        </div>
      </div>
    </div>
  );
}
