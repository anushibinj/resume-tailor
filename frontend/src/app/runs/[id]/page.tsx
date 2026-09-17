"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Copy, Download, FileText } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";

import { CoverageMeter } from "@/components/coverage-meter";
import { KeywordMargin } from "@/components/keyword-margin";
import { SectionDiffView } from "@/components/section-diff-view";
import { SuggestionMargin } from "@/components/suggestion-margin";
import { Button, Panel, Spinner, Tag } from "@/components/ui";
import { api } from "@/lib/api";
import type { RunDetail, Suggestion } from "@/lib/types";
import { cn, formatDate } from "@/lib/utils";

type Tab = "review" | "source" | "posting";

export default function RunPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>("review");
  const [pendingSuggestion, setPendingSuggestion] = useState<string | null>(null);

  const run = useQuery({
    queryKey: ["run", id],
    queryFn: () => api.runs.get(id),
    // Poll only while work is outstanding; stop as soon as the run reaches a terminal state.
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "PENDING" || status === "RUNNING" ? 2000 : false;
    },
  });

  const decide = useMutation({
    mutationFn: ({ suggestionId, status }: { suggestionId: string; status: Suggestion["status"] }) =>
      api.runs.updateSuggestion(id, suggestionId, status),
    onMutate: ({ suggestionId }) => setPendingSuggestion(suggestionId),
    onSuccess: (updated) => queryClient.setQueryData(["run", id], updated),
    onError: (error: Error) => toast.error(error.message),
    onSettled: () => setPendingSuggestion(null),
  });

  const exportSource = useMutation({
    mutationFn: () => api.runs.downloadSource(id),
    onError: (error: Error) => toast.error(error.message),
  });

  // PDF export can be switched off entirely (PDF_ENABLED=false); hide the button rather
  // than offering something that will only ever return an error.
  const pdf = useQuery({
    queryKey: ["pdf-available", id],
    queryFn: () => api.runs.pdfAvailable(id),
    staleTime: Infinity,
  });

  const exportPdf = useMutation({
    mutationFn: () => api.runs.compilePdf(id),
    onSuccess: () => toast.success("PDF compiled"),
    onError: (error: Error) => toast.error(error.message),
  });

  if (run.isLoading) {
    return (
      <div className="flex items-center gap-3 text-ink-soft">
        <Spinner /> Loading run…
      </div>
    );
  }
  if (run.isError || !run.data) {
    return (
      <Panel className="p-6">
        <p className="text-ink">That run doesn&apos;t exist.</p>
        <Link href="/runs" className="mt-2 inline-block text-sm text-pencil underline underline-offset-2">
          Back to history
        </Link>
      </Panel>
    );
  }

  const data = run.data;
  const working = data.status === "PENDING" || data.status === "RUNNING";

  return (
    <>
      <header className="mb-8 border-b border-rule pb-5">
        <div className="flex flex-wrap items-end justify-between gap-6">
          <div className="min-w-0">
            <h1 className="font-serif text-3xl leading-tight text-ink">
              {data.role ?? "Untitled role"}
              {data.company ? <span className="text-ink-soft"> at {data.company}</span> : null}
            </h1>
            <p className="mt-2 text-sm text-ink-soft">
              From {data.resumeName ?? "a deleted resume"} ·{" "}
              {data.format === "LATEX" ? "LaTeX" : "Markdown"} · started {formatDate(data.createdAt)}
              {data.modelUsed ? ` · ${data.modelUsed}` : ""}
            </p>
          </div>

          {data.status === "COMPLETED" ? (
            <div className="flex flex-wrap items-end gap-6">
              <CoverageMeter keywords={data.keywords} score={data.matchScore} />
              <div className="flex items-center gap-2">
                <Button onClick={() => exportSource.mutate()} disabled={exportSource.isPending}>
                  <Download className="size-4" />.{data.format === "LATEX" ? "tex" : "md"}
                </Button>
                {pdf.data?.enabled !== false ? (
                  <Button
                    variant="primary"
                    onClick={() => exportPdf.mutate()}
                    disabled={exportPdf.isPending}
                  >
                    {exportPdf.isPending ? (
                      <Spinner className="size-3.5" />
                    ) : (
                      <FileText className="size-4" />
                    )}
                    PDF
                  </Button>
                ) : null}
              </div>
            </div>
          ) : null}
        </div>
      </header>

      {working ? <WorkingNotice status={data.status} /> : null}
      {data.status === "FAILED" ? <FailureNotice message={data.errorMessage} /> : null}

      {data.status === "COMPLETED" ? (
        <div className="grid gap-8 lg:grid-cols-[1fr_340px]">
          <div className="min-w-0">
            <nav className="mb-4 flex gap-1 border-b border-rule">
              <TabButton current={tab} value="review" onSelect={setTab}>
                Review edits
              </TabButton>
              <TabButton current={tab} value="source" onSelect={setTab}>
                Final source
              </TabButton>
              <TabButton current={tab} value="posting" onSelect={setTab}>
                Job description
              </TabButton>
            </nav>

            {tab === "review" ? <SectionDiffView sections={data.sections} format={data.format} /> : null}
            {tab === "source" ? <SourceView data={data} /> : null}
            {tab === "posting" ? (
              <Panel className="p-5">
                <p className="whitespace-pre-wrap text-sm leading-relaxed text-ink-soft">
                  {data.jobDescriptionText}
                </p>
              </Panel>
            ) : null}
          </div>

          <aside className="space-y-6">
            <Panel className="p-5">
              <h2 className="mb-4 font-serif text-lg text-ink">What this job asks for</h2>
              <KeywordMargin keywords={data.keywords} />
            </Panel>

            <Panel className="p-5">
              <h2 className="mb-1 font-serif text-lg text-ink">Gaps worth a look</h2>
              <SuggestionMargin
                suggestions={data.suggestions}
                pendingId={pendingSuggestion}
                onDecide={(suggestionId, status) => decide.mutate({ suggestionId, status })}
              />
            </Panel>

            {data.promptTokens ? (
              <p className="text-xs text-ink-faint">
                {data.promptTokens.toLocaleString()} prompt +{" "}
                {(data.completionTokens ?? 0).toLocaleString()} completion tokens
              </p>
            ) : null}
          </aside>
        </div>
      ) : null}
    </>
  );
}

function TabButton({
  current,
  value,
  onSelect,
  children,
}: {
  current: Tab;
  value: Tab;
  onSelect: (tab: Tab) => void;
  children: React.ReactNode;
}) {
  const active = current === value;
  return (
    <button
      type="button"
      onClick={() => onSelect(value)}
      aria-current={active ? "true" : undefined}
      className={cn(
        "-mb-px border-b-2 px-3 py-2 text-sm transition-colors",
        active
          ? "border-pencil text-ink"
          : "border-transparent text-ink-soft hover:text-ink",
      )}
    >
      {children}
    </button>
  );
}

function SourceView({ data }: { data: RunDetail }) {
  const source = data.tailoredSource ?? "";
  return (
    <Panel className="overflow-hidden">
      <div className="flex items-center justify-between border-b border-rule px-4 py-2.5">
        <p className="text-sm text-ink-soft">
          Includes every suggestion you accepted. Your original preamble is untouched.
        </p>
        <Button
          variant="ghost"
          className="px-2.5 py-1 text-xs"
          onClick={() => {
            navigator.clipboard.writeText(source);
            toast.success("Copied to clipboard");
          }}
        >
          <Copy className="size-3.5" /> Copy
        </Button>
      </div>
      <pre className="max-h-[70vh] overflow-auto bg-surface-sunk px-4 py-3 font-mono text-xs leading-relaxed text-ink">
        {source}
      </pre>
    </Panel>
  );
}

function WorkingNotice({ status }: { status: string }) {
  return (
    <Panel className="flex items-center gap-3 p-5">
      <Spinner />
      <div>
        <p className="text-ink">
          {status === "PENDING" ? "Queued…" : "Reading the posting and rewriting your resume…"}
        </p>
        <p className="mt-0.5 text-sm text-ink-soft">
          This page updates itself. Usually 20–60 seconds.
        </p>
      </div>
    </Panel>
  );
}

function FailureNotice({ message }: { message: string | null }) {
  return (
    <Panel className="border-strike/40 bg-strike-soft p-5">
      <div className="flex items-start gap-3">
        <AlertTriangle className="mt-0.5 size-5 shrink-0 text-strike" />
        <div>
          <div className="flex items-center gap-2">
            <h2 className="font-serif text-lg text-ink">This run didn&apos;t finish</h2>
            <Tag tone="strike">Failed</Tag>
          </div>
          <p className="mt-1.5 text-sm text-ink">{message ?? "No error was recorded."}</p>
          <p className="mt-3 text-sm text-ink-soft">
            Your resume and the posting are both saved — check your model in{" "}
            <Link href="/settings" className="text-pencil underline underline-offset-2">
              Settings
            </Link>{" "}
            and{" "}
            <Link href="/tailor" className="text-pencil underline underline-offset-2">
              run it again
            </Link>
            .
          </p>
        </div>
      </div>
    </Panel>
  );
}
