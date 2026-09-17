"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { Wand2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { Button, Field, Input, PageHeading, Panel, Spinner, Textarea } from "@/components/ui";
import { api } from "@/lib/api";

export default function TailorPage() {
  const router = useRouter();
  const resumes = useQuery({ queryKey: ["resumes"], queryFn: api.resumes.list });
  const profiles = useQuery({ queryKey: ["llm-profiles"], queryFn: api.llmProfiles.list });

  const [resumeId, setResumeId] = useState("");
  const [jdText, setJdText] = useState("");
  const [company, setCompany] = useState("");
  const [role, setRole] = useState("");

  // Preselect the default resume once the list arrives.
  useEffect(() => {
    if (!resumeId && resumes.data?.length) {
      setResumeId((resumes.data.find((r) => r.isDefault) ?? resumes.data[0]).id);
    }
  }, [resumes.data, resumeId]);

  const start = useMutation({
    mutationFn: () =>
      api.runs.create({
        resumeId: resumeId || null,
        jdText,
        company: company.trim() || undefined,
        role: role.trim() || undefined,
      }),
    onSuccess: (run) => router.push(`/runs/${run.id}`),
    onError: (error: Error) => toast.error(error.message),
  });

  const noResumes = resumes.isSuccess && resumes.data.length === 0;
  const noModel = profiles.isSuccess && profiles.data.length === 0;
  const blocked = noResumes || noModel;

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeading
        title="Tailor to a posting"
        lede="Paste the job description. You'll get a rewritten copy of your resume, every edit marked up for review, and a list of what the posting wants that you can't currently back up."
      />

      {blocked ? (
        <Panel className="mb-6 border-gap/40 bg-gap-soft p-4">
          <p className="text-sm text-ink">
            {noResumes ? (
              <>
                Add a resume first —{" "}
                <Link href="/resumes" className="text-pencil underline underline-offset-2">
                  go to Resumes
                </Link>
                .{" "}
              </>
            ) : null}
            {noModel ? (
              <>
                Connect a model first —{" "}
                <Link href="/settings" className="text-pencil underline underline-offset-2">
                  go to Settings
                </Link>
                .
              </>
            ) : null}
          </p>
        </Panel>
      ) : null}

      <form
        className="space-y-5"
        onSubmit={(event) => {
          event.preventDefault();
          start.mutate();
        }}
      >
        <Panel className="p-5">
          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="Start from">
              <select
                value={resumeId}
                onChange={(e) => setResumeId(e.target.value)}
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
            <Field label="Company" hint="Optional — read from the posting if left blank.">
              <Input value={company} onChange={(e) => setCompany(e.target.value)} placeholder="Acme" />
            </Field>
            <Field label="Role" hint="Optional.">
              <Input
                value={role}
                onChange={(e) => setRole(e.target.value)}
                placeholder="Senior Backend Engineer"
              />
            </Field>
          </div>
        </Panel>

        <Panel className="p-5">
          <Field label="Job description" hint="The whole posting works better than a summary of it.">
            <Textarea
              required
              rows={18}
              value={jdText}
              onChange={(e) => setJdText(e.target.value)}
              placeholder="Paste the full posting here…"
              className="leading-relaxed"
            />
          </Field>
        </Panel>

        <div className="flex items-center gap-3">
          <Button type="submit" variant="primary" disabled={start.isPending || blocked || !jdText.trim()}>
            {start.isPending ? <Spinner className="size-3.5" /> : <Wand2 className="size-4" />}
            Tailor my resume
          </Button>
          <span className="text-sm text-ink-soft">Usually takes 20–60 seconds.</span>
        </div>
      </form>
    </div>
  );
}
