"use client";

import { RefreshCw } from "lucide-react";

import { Button, Panel, Spinner } from "@/components/ui";
import type { SummaryOptions } from "@/lib/types";

/**
 * Picks how long the resume's summary is, from lengths the model already wrote, so moving
 * the slider is instant rather than another wait on a model.
 *
 * The stops are the lengths that exist, not a fixed 4-7 range: a reply can be missing one,
 * and a stop with nothing behind it would be a control that does nothing.
 */
export function SummaryLength({
  summary,
  onSelect,
  onGenerate,
  generating,
}: {
  summary: SummaryOptions;
  onSelect: (lines: number) => void;
  onGenerate: () => void;
  generating: boolean;
}) {
  const { variants } = summary;

  if (summary.status === "PENDING" || summary.status === "RUNNING") {
    return (
      <Panel className="flex items-center gap-3 p-4">
        <Spinner />
        <p className="text-sm text-ink-soft">Writing summary options at 4 to 7 lines…</p>
      </Panel>
    );
  }

  if (variants.length === 0) {
    return (
      <Panel className="p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="font-serif text-base text-ink">Summary length</h2>
            <p className="mt-0.5 text-sm text-ink-soft">
              {summary.status === "NONE"
                ? "This run was made before summary lengths existed."
                : summary.status === "FAILED"
                  ? `The options couldn't be written. ${summary.error ?? ""}`
                  : "Your resume has no summary paragraph to resize."}
            </p>
          </div>
          {summary.status !== "COMPLETED" ? (
            <Button variant={summary.status === "NONE" ? "primary" : "quiet"} onClick={onGenerate} disabled={generating}>
              {generating ? <Spinner className="size-3.5" /> : <RefreshCw className="size-4" />}
              {summary.status === "NONE" ? "Write summary options" : "Try again"}
            </Button>
          ) : null}
        </div>
      </Panel>
    );
  }

  // The handle is driven by the run itself, which the page updates the instant a length is
  // picked, so there is no local drag state to fall out of step with the server.
  const index = Math.max(0, variants.findIndex((variant) => variant.lines === summary.selectedLines));
  const shown = variants[index];

  return (
    <Panel className="p-4">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="font-serif text-base text-ink">Summary length</h2>
        <p className="text-sm text-ink" aria-live="polite">
          ≈ {shown.lines} lines
        </p>
      </div>

      <input
        type="range"
        min={0}
        max={variants.length - 1}
        step={1}
        value={index}
        onChange={(event) => onSelect(variants[Number(event.target.value)].lines)}
        aria-label="Summary length in lines"
        aria-valuetext={`about ${shown.lines} lines`}
        className="mt-3 block w-full cursor-pointer accent-pencil"
      />
      <div className="mt-1 flex justify-between px-1 text-xs text-ink-faint" aria-hidden="true">
        {variants.map((variant, i) => (
          <span key={variant.lines} className={i === index ? "font-medium text-ink" : undefined}>
            {variant.lines}
          </span>
        ))}
      </div>

      <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-xs text-ink-faint">
        <p>Line counts are estimates. Your template&apos;s text width decides the real number.</p>
        <button
          type="button"
          onClick={onGenerate}
          disabled={generating}
          className="inline-flex items-center gap-1 text-ink-soft transition-colors hover:text-pencil disabled:opacity-45"
        >
          <RefreshCw className="size-3" /> Write new options
        </button>
      </div>
      {summary.status === "FAILED" ? (
        <p className="mt-2 text-xs text-strike">
          New options couldn&apos;t be written, so these are from earlier. {summary.error}
        </p>
      ) : null}
    </Panel>
  );
}
