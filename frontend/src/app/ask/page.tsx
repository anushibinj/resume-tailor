"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { RotateCcw, Send } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { Button, EmptyState, Field, PageHeading, Panel, Spinner, Tag, Textarea } from "@/components/ui";
import { api, describeError } from "@/lib/api";
import type { ResumeQuestion } from "@/lib/types";
import { formatRelative } from "@/lib/utils";

export default function AskPage() {
  const queryClient = useQueryClient();
  const resumes = useQuery({ queryKey: ["resumes"], queryFn: api.resumes.list });
  const profiles = useQuery({ queryKey: ["llm-profiles"], queryFn: api.llmProfiles.list });

  const [resumeId, setResumeId] = useState("");
  const [question, setQuestion] = useState("");
  // The question and answer currently on screen. Once this is set the form is replaced by
  // the answer and a single "ask another" action -- this is never a thread to append to.
  const [asked, setAsked] = useState<ResumeQuestion | null>(null);

  // Preselect the default resume once the list arrives.
  useEffect(() => {
    if (!resumeId && resumes.data?.length) {
      setResumeId((resumes.data.find((r) => r.isDefault) ?? resumes.data[0]).id);
    }
  }, [resumes.data, resumeId]);

  const history = useQuery({
    queryKey: ["resume-questions", resumeId],
    queryFn: () => api.resumes.questions.history(resumeId),
    enabled: !!resumeId,
  });

  const ask = useMutation({
    mutationFn: () => api.resumes.questions.ask(resumeId, { question: question.trim() }),
    onSuccess: (result) => {
      setAsked(result);
      queryClient.invalidateQueries({ queryKey: ["resume-questions", resumeId] });
      if (result.status === "FAILED") {
        toast.error(result.errorMessage ?? "The question could not be answered");
      }
    },
    onError: (error: unknown) => toast.error(describeError(error)),
  });

  const startNewChat = () => {
    setAsked(null);
    setQuestion("");
  };

  const onResumeChange = (id: string) => {
    setResumeId(id);
    startNewChat();
  };

  const noResumes = resumes.isSuccess && resumes.data.length === 0;
  const noModel = profiles.isSuccess && profiles.data.length === 0;

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeading
        title="Ask about a resume"
        lede="Ask one question at a time about a resume you've uploaded -- your core experience, a specific skill, how a project might be summarised. Each question is answered on its own, from that resume alone; there's no back-and-forth to keep track of."
      />

      {noResumes ? (
        <EmptyState
          title="Nothing to ask about yet"
          body="Upload a resume first, then come back here to ask questions about it."
          action={
            <Link href="/resumes">
              <Button variant="primary">Add a resume</Button>
            </Link>
          }
        />
      ) : (
        <div className="space-y-6">
          {noModel ? (
            <Panel className="border-gap/40 bg-gap-soft p-4">
              <p className="text-sm text-ink">
                Connect a model first —{" "}
                <Link href="/settings" className="text-pencil underline underline-offset-2">
                  go to Settings
                </Link>
                .
              </p>
            </Panel>
          ) : null}

          <Panel className="p-5">
            <Field label="Resume">
              <select
                value={resumeId}
                onChange={(e) => onResumeChange(e.target.value)}
                className="w-full rounded-sm border border-rule-strong bg-surface px-3 py-2 text-sm text-ink focus:border-pencil focus:outline-none"
              >
                {resumes.data?.map((resume) => (
                  <option key={resume.id} value={resume.id}>
                    {resume.name}
                    {resume.isDefault ? " (default)" : ""}
                  </option>
                ))}
              </select>
            </Field>
          </Panel>

          <Panel className="p-5">
            {!asked ? (
              <form
                className="space-y-4"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (question.trim()) ask.mutate();
                }}
              >
                <Field
                  label="Your question"
                  hint="Answered from this resume alone -- a single response, no follow-up."
                >
                  <Textarea
                    required
                    rows={3}
                    value={question}
                    onChange={(e) => setQuestion(e.target.value)}
                    placeholder="Briefly tell about my core experience and technical strengths"
                  />
                </Field>
                <Button type="submit" variant="primary" disabled={ask.isPending || !question.trim() || noModel}>
                  {ask.isPending ? <Spinner className="size-3.5" /> : <Send className="size-4" />}
                  Ask
                </Button>
              </form>
            ) : (
              <div className="space-y-4">
                <div>
                  <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">You asked</p>
                  <p className="mt-1 font-serif text-lg text-ink">{asked.question}</p>
                </div>
                <div>
                  <p className="text-xs font-medium uppercase tracking-wide text-ink-faint">
                    {asked.status === "COMPLETED" ? "Answer" : "Could not answer"}
                  </p>
                  {asked.status === "COMPLETED" ? (
                    <p className="mt-1 whitespace-pre-wrap text-sm leading-relaxed text-ink">{asked.answer}</p>
                  ) : (
                    <p className="mt-1 text-sm text-strike">{asked.errorMessage}</p>
                  )}
                </div>
                <Button variant="primary" onClick={startNewChat}>
                  <RotateCcw className="size-4" /> Ask another question in a new chat
                </Button>
              </div>
            )}
          </Panel>

          <div>
            <h2 className="mb-3 font-serif text-lg text-ink">Past questions</h2>
            {history.isLoading ? <Spinner /> : null}
            {history.data?.content.length === 0 ? (
              <p className="text-sm text-ink-soft">Nothing asked about this resume yet.</p>
            ) : null}
            <div className="space-y-2">
              {history.data?.content.map((item) => (
                <Panel key={item.id} className="p-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="font-serif text-base text-ink">{item.question}</p>
                    <div className="flex shrink-0 items-center gap-2">
                      {item.status === "FAILED" ? <Tag tone="strike">Failed</Tag> : null}
                      <span className="text-xs text-ink-faint">{formatRelative(item.createdAt)}</span>
                    </div>
                  </div>
                  <p
                    className={`mt-2 whitespace-pre-wrap text-sm leading-relaxed ${
                      item.status === "FAILED" ? "text-strike" : "text-ink-soft"
                    }`}
                  >
                    {item.status === "COMPLETED" ? item.answer : item.errorMessage}
                  </p>
                </Panel>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
