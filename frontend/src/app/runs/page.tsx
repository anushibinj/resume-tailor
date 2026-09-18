"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";

import { Button, EmptyState, PageHeading, Panel, Spinner, Tag } from "@/components/ui";
import { api, describeError } from "@/lib/api";
import type { RunStatus, RunSummary } from "@/lib/types";
import { formatRelative } from "@/lib/utils";

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
  const runs = useQuery({ queryKey: ["runs"], queryFn: () => api.runs.list(0, 50) });

  const remove = useMutation({
    mutationFn: api.runs.remove,
    onSuccess: () => {
      toast.success("Run deleted");
      queryClient.invalidateQueries({ queryKey: ["runs"] });
    },
    onError: (error: unknown) => toast.error(describeError(error)),
  });

  const confirmDelete = (run: RunSummary) => {
    const label = run.role ? `the run for ${run.role}` : "this run";
    if (window.confirm(`Delete ${label}? This removes its history, diff and any compiled PDF. This can't be undone.`)) {
      remove.mutate(run.id);
    }
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

      {runs.isLoading ? <Spinner /> : null}

      {runs.data?.content.length === 0 ? (
        <EmptyState
          title="No runs yet"
          body="Once you tailor a resume to a posting it'll show up here, so you can always see what you sent to whom."
          action={
            <Link href="/tailor">
              <Button variant="primary">Tailor to a posting</Button>
            </Link>
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
