"use client";

import { useState } from "react";
import { ChevronDown } from "lucide-react";

import type { SectionDiff } from "@/lib/types";
import { cn } from "@/lib/utils";
import { Tag } from "./ui";
import { WordDiff } from "./word-diff";

const CHANGE_LABEL: Record<string, string> = {
  REWORDED: "Reworded",
  REORDERED: "Moved",
  TRIMMED: "Trimmed",
  EMPHASISED: "Emphasised",
};

export function SectionDiffView({ sections }: { sections: SectionDiff[] }) {
  return (
    <div className="space-y-3">
      {sections.map((section, index) => (
        <SectionRow key={`${section.title}-${index}`} section={section} />
      ))}
    </div>
  );
}

function SectionRow({ section }: { section: SectionDiff }) {
  const unchanged = section.originalContent.trim() === section.tailoredContent.trim();
  const [open, setOpen] = useState(!unchanged);

  const dropped = section.tailoredContent.trim() === "" && section.originalContent.trim() !== "";
  const added = section.originalContent.trim() === "" && section.tailoredContent.trim() !== "";

  return (
    <section className="rounded-sm border border-rule bg-surface">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        className="flex w-full items-center gap-3 px-4 py-3 text-left"
      >
        <ChevronDown
          className={cn("size-4 shrink-0 text-ink-faint transition-transform", open && "rotate-180")}
        />
        <span className="font-serif text-base text-ink">{section.title}</span>
        {dropped ? <Tag tone="strike">Removed</Tag> : null}
        {added ? <Tag tone="pencil">New section</Tag> : null}
        {!dropped && !added && unchanged ? <Tag>Unchanged</Tag> : null}
        {!dropped && !added && !unchanged && section.changeType ? (
          <Tag tone="pencil">{CHANGE_LABEL[section.changeType] ?? "Edited"}</Tag>
        ) : null}
      </button>

      {open ? (
        <div className="border-t border-rule">
          {section.rationale ? (
            <p className="border-b border-rule bg-surface-sunk px-4 py-2.5 text-sm text-ink-soft">
              {section.rationale}
            </p>
          ) : null}
          <div className="grid gap-px bg-rule md:grid-cols-2">
            <Column heading="Yours">
              <WordDiff
                original={section.originalContent}
                tailored={section.tailoredContent}
                side="original"
              />
            </Column>
            <Column heading="Tailored">
              <WordDiff
                original={section.originalContent}
                tailored={section.tailoredContent}
                side="tailored"
              />
            </Column>
          </div>
        </div>
      ) : null}
    </section>
  );
}

function Column({ heading, children }: { heading: string; children: React.ReactNode }) {
  return (
    <div className="bg-surface px-4 py-3">
      <p className="mb-2 text-xs font-medium text-ink-faint">{heading}</p>
      {children}
    </div>
  );
}
