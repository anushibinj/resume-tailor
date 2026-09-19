"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Copy, Download, FileText, RefreshCw, Trash2 } from "lucide-react";
import dynamic from "next/dynamic";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";

import { CoverageMeter } from "@/components/coverage-meter";
import { Button, Panel, Skeleton, Spinner, Tag } from "@/components/ui";
import { api, describeError } from "@/lib/api";
import type { RunDetail, Suggestion } from "@/lib/types";
import { cn, formatDate } from "@/lib/utils";

// Both panes are non-trivial (a word-diff engine, a multi-state gap review UI) and only
// matter once the run has completed and the relevant tab/data is in view, so they're
// split out of the initial route chunk rather than loaded with every run page.
const SectionDiffView = dynamic(
  () => import("@/components/section-diff-view").then((mod) => mod.SectionDiffView),
  { loading: () => <SectionDiffViewSkeleton /> },
);
const KeywordMargin = dynamic(
  () => import("@/components/keyword-margin").then((mod) => mod.KeywordMargin),
  { loading: () => <KeywordMarginSkeleton /> },
);

type Tab = "review" | "source" | "posting";

export default function RunPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>("review");
  const [pendingSuggestion, setPendingSuggestion] = useState<string | null>(null);

  const run = useQuery({
    queryKey: ["run", id],
    queryFn: () => api.runs.get(id),
    // Poll while any work is outstanding. The gap check runs after the rewrite is already
    // saved, so the run can be COMPLETED while its requirements are still being judged.
    refetchInterval: (query) => {
      const data = query.state.data;
      const working =
        data?.status === "PENDING" ||
        data?.status === "RUNNING" ||
        data?.gapsStatus === "PENDING" ||
        data?.gapsStatus === "RUNNING";
      return working ? 2000 : false;
    },
  });

  const decide = useMutation({
    mutationFn: ({ suggestionId, status }: { suggestionId: string; status: Suggestion["status"] }) =>
      api.runs.updateSuggestion(id, suggestionId, status),
    onMutate: ({ suggestionId }) => setPendingSuggestion(suggestionId),
    onSuccess: (updated) => queryClient.setQueryData(["run", id], updated),
    onError: (error: unknown) => toast.error(describeError(error)),
    onSettled: () => setPendingSuggestion(null),
  });

  const recheck = useMutation({
    mutationFn: () => api.runs.recheckGaps(id),
    onSuccess: (updated) => queryClient.setQueryData(["run", id], updated),
    onError: (error: unknown) => toast.error(describeError(error)),
  });

  // Applied one at a time (not in parallel) because each accept recomputes the whole
  // tailored document server-side; overlapping requests could race on that rebuild.
  const addAllMissing = useMutation({
    mutationFn: async (suggestionIds: string[]) => {
      let updated: RunDetail | undefined;
      for (const suggestionId of suggestionIds) {
        updated = await api.runs.updateSuggestion(id, suggestionId, "ACCEPTED");
      }
      return updated;
    },
    onSuccess: (updated) => {
      if (updated) queryClient.setQueryData(["run", id], updated);
    },
    onError: (error: unknown) => toast.error(describeError(error)),
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

  const remove = useMutation({
    mutationFn: () => api.runs.remove(id),
    onSuccess: () => {
      toast.success("Run deleted");
      queryClient.invalidateQueries({ queryKey: ["runs"] });
      router.push("/runs");
    },
    onError: (error: unknown) => toast.error(describeError(error)),
  });

  const confirmDelete = () => {
    if (window.confirm("Delete this run? This removes its history, diff and any compiled PDF. This can't be undone.")) {
      remove.mutate();
    }
  };

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
              {data.gapsStatus === "COMPLETED" ? (
                <CoverageMeter keywords={data.keywords} score={data.matchScore} />
              ) : null}
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

          <Button
            variant="danger"
            onClick={confirmDelete}
            disabled={remove.isPending}
            aria-label="Delete run"
          >
            {remove.isPending ? <Spinner className="size-3.5" /> : <Trash2 className="size-4" />}
            Delete
          </Button>
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
              <div className="mb-4 flex items-baseline justify-between gap-3">
                <h2 className="font-serif text-lg text-ink">What this job asks for</h2>
                {data.gapsStatus === "COMPLETED" ? (
                  <button
                    type="button"
                    onClick={() => recheck.mutate()}
                    disabled={recheck.isPending}
                    className="inline-flex items-center gap-1 text-xs text-ink-soft transition-colors hover:text-pencil"
                  >
                    <RefreshCw className="size-3" /> Re-check
                  </button>
                ) : null}
              </div>
              <GapsPane
                data={data}
                onDecide={(suggestionId, status) => decide.mutate({ suggestionId, status })}
                pendingId={pendingSuggestion}
                onRecheck={() => recheck.mutate()}
                rechecking={recheck.isPending}
                onAddAllMissing={(suggestionIds) => addAllMissing.mutate(suggestionIds)}
                addingAll={addAllMissing.isPending}
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

/**
 * The requirements pane across every state the gap check can be in. Coverage from the
 * retired keyword matcher is deliberately not shown: those numbers were wrong often
 * enough that displaying them is worse than offering a re-check.
 */
function GapsPane({
  data,
  onDecide,
  pendingId,
  onRecheck,
  rechecking,
  onAddAllMissing,
  addingAll,
}: {
  data: RunDetail;
  onDecide: (suggestionId: string, status: Suggestion["status"]) => void;
  pendingId: string | null;
  onRecheck: () => void;
  rechecking: boolean;
  onAddAllMissing: (suggestionIds: string[]) => void;
  addingAll: boolean;
}) {
  if (data.status !== "COMPLETED") {
    return <p className="text-sm text-ink-soft">Available once the rewrite finishes.</p>;
  }

  if (data.gapsStatus === "PENDING" || data.gapsStatus === "RUNNING") {
    return (
      <div className="flex items-center gap-3">
        <Spinner />
        <p className="text-sm text-ink-soft">Checking your resume against the posting…</p>
      </div>
    );
  }

  if (data.gapsStatus === "OUTDATED") {
    return (
      <div className="space-y-3">
        <p className="text-sm text-ink-soft">
          This run was checked by the old keyword matcher, which compared text literally and
          got it wrong often. Re-check it with your model to see what&apos;s really covered.
        </p>
        <Button variant="primary" onClick={onRecheck} disabled={rechecking}>
          {rechecking ? <Spinner className="size-3.5" /> : <RefreshCw className="size-4" />}
          Re-check with your model
        </Button>
        {data.otherSuggestions.length > 0 ? (
          <KeywordMargin
            keywords={[]}
            otherSuggestions={data.otherSuggestions}
            onDecide={onDecide}
            pendingId={pendingId}
            modelUsed={data.modelUsed}
          />
        ) : null}
      </div>
    );
  }

  if (data.gapsStatus === "FAILED") {
    return (
      <div className="space-y-3">
        <p className="text-sm text-ink">The check didn&apos;t finish.</p>
        <p className="text-sm text-ink-soft">{data.gapsError}</p>
        <Button variant="quiet" onClick={onRecheck} disabled={rechecking}>
          {rechecking ? <Spinner className="size-3.5" /> : <RefreshCw className="size-4" />}
          Try again
        </Button>
      </div>
    );
  }

  return (
    <KeywordMargin
      keywords={data.keywords}
      otherSuggestions={data.otherSuggestions}
      onDecide={onDecide}
      pendingId={pendingId}
      modelUsed={data.modelUsed}
      onAddAllMissing={onAddAllMissing}
      addingAll={addingAll}
    />
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

/** Approximates the accordion of section rows `SectionDiffView` renders once loaded. */
function SectionDiffViewSkeleton() {
  return (
    <div className="space-y-3">
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="flex items-center gap-3 rounded-sm border border-rule bg-surface px-4 py-3">
          <Skeleton className="h-4 w-4 shrink-0" />
          <Skeleton className="h-4 w-40" />
        </div>
      ))}
    </div>
  );
}

/** Approximates the grouped requirement rows `KeywordMargin` renders once loaded. */
function KeywordMarginSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-4 w-28" />
      {[0, 1, 2].map((i) => (
        <Skeleton key={i} className="h-16 w-full" />
      ))}
    </div>
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
