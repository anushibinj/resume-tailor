"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { ApplicationLinkControl, AppliedToggle } from "@/components/run-application";
import { Button, EmptyState, PageHeading, Panel, Spinner, Tag } from "@/components/ui";
import { api, describeError } from "@/lib/api";
import { getStoredAppliedFilter, setStoredAppliedFilter, type AppliedFilter } from "@/lib/runs-filter";
import type { RunStatus, RunSummary } from "@/lib/types";
import { cn, formatRelative } from "@/lib/utils";

const FILTERS: { value: AppliedFilter; label: string }[] = [
  { value: "all", label: "All" },
  { value: "unapplied", label: "Not applied" },
  { value: "applied", label: "Applied" },
];

const STATUS_TONE: Record<RunStatus, "neutral" | "pencil" | "strike"> = {
  PENDING: "neutral",
  RUNNING: "neutral",
  COMPLETED: "pencil",
  FAILED: "strike",
};

const STATUS_LABEL: Record<RunStatus, string> = {
  PENDING: "Queued",
  RUNNING: "Running",
  COMPLETED: "Done",
  FAILED: "Failed",
};

export default function RunsPage() {
  const queryClient = useQueryClient();
  // Starts at "all" (matching what the server renders) and picks up the stored choice once
  // mounted, so this never disagrees with the HTML React hydrates against.
  const [filter, setFilter] = useState<AppliedFilter>("all");
  useEffect(() => setFilter(getStoredAppliedFilter()), []);

  const runs = useQuery({
    queryKey: ["runs", filter],
    queryFn: () => api.runs.list(0, 50, filter === "all" ? undefined : filter === "applied"),
  });

  const remove = useMutation({
    mutationFn: api.runs.remove,
    onSuccess: () => {
      toast.success("Run deleted");
      queryClient.invalidateQueries({ queryKey: ["runs"] });
    },
    onError: (error: unknown) => toast.error(describeError(error)),
  });

  const applied = useMutation({
    mutationFn: ({ id, applied: nextApplied }: { id: string; applied: boolean }) =>
      api.runs.setApplied(id, nextApplied),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["runs"] }),
    onError: (error: unknown) => toast.error(describeError(error)),
  });

  const applicationLink = useMutation({
    mutationFn: ({ id, link }: { id: string; link: string }) => api.runs.setApplicationLink(id, link),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["runs"] }),
    onError: (error: unknown) => toast.error(describeError(error)),
  });

  const confirmDelete = (run: RunSummary) => {
    const label = run.role ? `the run for ${run.role}` : "this run";
    if (window.confirm(`Delete ${label}? This removes its history, diff and any compiled PDF. This can't be undone.`)) {
      remove.mutate(run.id);
    }
  };

  const selectFilter = (value: AppliedFilter) => {
    setFilter(value);
    setStoredAppliedFilter(value);
  };

  return (
    <>
      <PageHeading
        title="History"
        lede="Every tailoring run you've made, with the copy you actually sent."
        actions={
          <Link href="/tailor">
            <Button variant="primary">Tailor to a posting</Button>
          </Link>
        }
      />

      <div className="mb-4 flex w-fit items-center gap-1 rounded-sm border border-rule bg-surface p-1">
        {FILTERS.map((option) => (
          <button
            key={option.value}
            type="button"
            onClick={() => selectFilter(option.value)}
            aria-current={filter === option.value ? "true" : undefined}
            className={cn(
              "rounded-sm px-3 py-1.5 text-sm transition-colors",
              filter === option.value ? "bg-pencil text-on-pencil" : "text-ink-soft hover:text-ink",
            )}
          >
            {option.label}
          </button>
        ))}
      </div>

      {runs.isLoading ? <Spinner /> : null}

      {runs.data?.content.length === 0 ? (
        <EmptyState
          title={filter === "all" ? "No runs yet" : filter === "applied" ? "No applied runs yet" : "Nothing left to apply to"}
          body={
            filter === "all"
              ? "Once you tailor a resume to a posting it'll show up here, so you can always see what you sent to whom."
              : filter === "applied"
                ? "Mark a run applied once you've actually sent it, and it'll show up here."
                : "Every run is marked applied, or switch back to “All” to see everything."
          }
          action={
            filter === "all" ? (
              <Link href="/tailor">
                <Button variant="primary">Tailor to a posting</Button>
              </Link>
            ) : undefined
          }
        />
      ) : null}

      <div className="space-y-2">
        {runs.data?.content.map((run) => (
          <Link key={run.id} href={`/runs/${run.id}`} className="block">
            <Panel className="flex flex-wrap items-center gap-4 p-4 transition-colors hover:border-pencil">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="font-serif text-lg text-ink">
                    {run.role ?? "Untitled role"}
                    {run.company ? <span className="text-ink-soft"> at {run.company}</span> : null}
                  </h2>
                  <Tag tone={STATUS_TONE[run.status]}>{STATUS_LABEL[run.status]}</Tag>
                </div>
                <p className="mt-1 text-sm text-ink-soft">
                  From {run.resumeName ?? "a deleted resume"} · {formatRelative(run.createdAt)}
                </p>
              </div>

              <AppliedToggle
                applied={run.applied}
                onToggle={() => applied.mutate({ id: run.id, applied: !run.applied })}
                pending={applied.isPending && applied.variables?.id === run.id}
              />

              <ApplicationLinkControl
                link={run.applicationLink}
                onSave={(link) => applicationLink.mutate({ id: run.id, link })}
                saving={applicationLink.isPending && applicationLink.variables?.id === run.id}
              />

              {run.matchScore !== null ? (
                <div className="text-right">
                  <div className="font-serif text-2xl leading-none text-ink">
                    {run.matchScore}
                    <span className="text-sm text-ink-faint">%</span>
                  </div>
                  <div className="mt-1 text-xs text-ink-faint">covered</div>
                </div>
              ) : null}

              <Button
                variant="danger"
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  confirmDelete(run);
                }}
                disabled={remove.isPending}
                aria-label="Delete run"
                className="shrink-0 px-2 py-1.5"
              >
                <Trash2 className="size-3.5" />
              </Button>
            </Panel>
          </Link>
        ))}
      </div>
    </>
  );
}
