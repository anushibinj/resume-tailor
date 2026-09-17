"use client";

import { diffWords } from "diff";
import { useMemo } from "react";

import type { ResumeFormat } from "@/lib/types";
import { cn } from "@/lib/utils";

/**
 * Side-by-side proofreading marks computed from a single diff pass.
 *
 * <p>The left column shows what you had, with cut text struck through; the right shows
 * what the model produced, with new text underlined in pencil. Deliberately not a
 * red/green block diff -- this is a document being edited, not a code review.
 *
 * <p>Markdown is set in the serif document face because it reads as prose. LaTeX is set
 * in monospace: what's on screen is markup like \begin{itemize}, and a serif makes that
 * harder to scan, not easier.
 */
export function WordDiff({
  original,
  tailored,
  side,
  format,
}: {
  original: string;
  tailored: string;
  side: "original" | "tailored";
  format: ResumeFormat;
}) {
  const changes = useMemo(() => diffWords(original ?? "", tailored ?? ""), [original, tailored]);

  return (
    <div className={cn("document text-ink", format === "LATEX" && "font-mono text-xs leading-relaxed")}>
      {changes.map((change, index) => {
        if (side === "original" && change.added) return null;
        if (side === "tailored" && change.removed) return null;

        const marked =
          (side === "original" && change.removed) || (side === "tailored" && change.added);
        if (!marked) {
          return <span key={index}>{change.value}</span>;
        }
        return (
          <span key={index} className={side === "original" ? "mark-cut" : "mark-add"}>
            {change.value}
          </span>
        );
      })}
    </div>
  );
}
