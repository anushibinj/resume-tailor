"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight } from "lucide-react";
import Link from "next/link";

import { Button, Panel, Spinner, Tag } from "@/components/ui";
import { api } from "@/lib/api";
import { formatRelative } from "@/lib/utils";

export default function HomePage() {
  const runs = useQuery({ queryKey: ["runs"], queryFn: () => api.runs.list(0, 5) });
  const resumes = useQuery({ queryKey: ["resumes"], queryFn: api.resumes.list });
  const profiles = useQuery({ queryKey: ["llm-profiles"], queryFn: api.llmProfiles.list });

  const needsResume = resumes.isSuccess && resumes.data.length === 0;
  const needsModel = profiles.isSuccess && profiles.data.length === 0;
  const ready = !needsResume && !needsModel;

  return (
    <>
      <section className="mb-12 max-w-[62ch]">
        <h1 className="font-serif text-[2.75rem] leading-[1.1] text-ink">
          One resume. A different emphasis for every job.
        </h1>
        <p className="mt-4 text-base leading-relaxed text-ink-soft">
          Paste a posting and your resume gets rewritten to speak to it — reordered, reworded,
          trimmed. Nothing is invented. Anything the job wants that you can&apos;t back up is listed
          separately, for you to decide on.
        </p>
        {ready ? (
          <Link href="/tailor" className="mt-6 inline-block">
            <Button variant="primary">
              Tailor to a posting <ArrowRight className="size-4" />
            </Button>
          </Link>
        ) : null}
      </section>

      {!ready && (resumes.isSuccess || profiles.isSuccess) ? (
        <ol className="mb-12 grid gap-3 sm:grid-cols-2">
          <SetupStep
            done={!needsModel}
            href="/settings"
            title="Connect your model"
            body="Any OpenAI-compatible endpoint — OpenAI, Groq, or something running on this machine."
          />
          <SetupStep
            done={!needsResume}
            href="/resumes"
            title="Add your resume"
            body="The .tex or .md file you already send to employers."
          />
        </ol>
      ) : null}

      <section>
        <div className="mb-4 flex items-baseline justify-between border-b border-rule pb-3">
          <h2 className="font-serif text-xl text-ink">Recent runs</h2>
          {runs.data?.content.length ? (
            <Link href="/runs" className="text-sm text-pencil underline underline-offset-2">
              See all
            </Link>
          ) : null}
        </div>

        {runs.isLoading ? <Spinner /> : null}
        {runs.data?.content.length === 0 ? (
          <p className="py-6 text-sm text-ink-soft">Nothing yet.</p>
        ) : null}

        <div className="space-y-2">
          {runs.data?.content.map((run) => (
            <Link key={run.id} href={`/runs/${run.id}`} className="block">
              <Panel className="flex items-center gap-4 p-4 transition-colors hover:border-pencil">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-serif text-base text-ink">
                    {run.role ?? "Untitled role"}
                    {run.company ? <span className="text-ink-soft"> at {run.company}</span> : null}
                  </p>
                  <p className="mt-0.5 text-sm text-ink-soft">{formatRelative(run.createdAt)}</p>
                </div>
                {run.status !== "COMPLETED" ? (
                  <Tag tone={run.status === "FAILED" ? "strike" : "neutral"}>
                    {run.status === "FAILED" ? "Failed" : "Running"}
                  </Tag>
                ) : (
                  <span className="font-serif text-xl text-ink">
                    {run.matchScore}
                    <span className="text-sm text-ink-faint">%</span>
                  </span>
                )}
              </Panel>
            </Link>
          ))}
        </div>
      </section>
    </>
  );
}

function SetupStep({
  done,
  href,
  title,
  body,
}: {
  done: boolean;
  href: string;
  title: string;
  body: string;
}) {
  return (
    <li>
      <Link href={href} className="block h-full">
        <Panel className="h-full p-5 transition-colors hover:border-pencil">
          <div className="flex items-center gap-2">
            <h3 className="font-serif text-lg text-ink">{title}</h3>
            {done ? <Tag tone="pencil">Done</Tag> : null}
          </div>
          <p className="mt-1.5 text-sm text-ink-soft">{body}</p>
        </Panel>
      </Link>
    </li>
  );
}
