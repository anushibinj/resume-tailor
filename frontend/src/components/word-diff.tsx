"use client";

import { diffWords } from "diff";
import { useMemo } from "react";

/**
 * Side-by-side proofreading marks computed from a single diff pass.
 *
 * <p>The left column shows what you had, with cut text struck through; the right shows
 * what the model produced, with new text underlined in pencil. Deliberately not a
 * red/green block diff -- this is a document being edited, not a code review.
 */
export function WordDiff({
  original,
  tailored,
  side,
}: {
  original: string;
  tailored: string;
  side: "original" | "tailored";
}) {
  const changes = useMemo(() => diffWords(original ?? "", tailored ?? ""), [original, tailored]);

  return (
    <div className="document text-ink">
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
