"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FileUp, Star, Trash2 } from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";

import { Button, EmptyState, Field, Input, PageHeading, Panel, Spinner, Tag, Textarea } from "@/components/ui";
import { api } from "@/lib/api";
import type { ResumeSummary, SaveResumeRequest } from "@/lib/types";
import { formatRelative } from "@/lib/utils";

const BLANK: SaveResumeRequest = { name: "", sourceText: "" };

export default function ResumesPage() {
  const queryClient = useQueryClient();
  const resumes = useQuery({ queryKey: ["resumes"], queryFn: api.resumes.list });
  const fileInput = useRef<HTMLInputElement>(null);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<SaveResumeRequest>(BLANK);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["resumes"] });

  const save = useMutation({
    mutationFn: (body: SaveResumeRequest) =>
      editingId ? api.resumes.update(editingId, body) : api.resumes.create(body),
    onSuccess: (resume) => {
      toast.success(
        `${editingId ? "Updated" : "Added"} “${resume.name}” — read as ${
          resume.format === "LATEX" ? "LaTeX" : "Markdown"
        }, ${resume.sections.length} sections`,
      );
      setEditingId(null);
      setForm(BLANK);
      invalidate();
    },
    onError: (error: Error) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: api.resumes.remove,
    onSuccess: () => {
      toast.success("Resume deleted");
      invalidate();
    },
    onError: (error: Error) => toast.error(error.message),
  });

  const setDefault = useMutation({
    mutationFn: api.resumes.setDefault,
    onSuccess: invalidate,
    onError: (error: Error) => toast.error(error.message),
  });

  const startEdit = async (resume: ResumeSummary) => {
    const detail = await api.resumes.get(resume.id);
    setEditingId(detail.id);
    setForm({ name: detail.name, sourceText: detail.sourceText });
  };

  /** Read in the browser so the API stays plain JSON — no multipart handling needed. */
  const onFile = async (file: File | undefined) => {
    if (!file) return;
    const text = await file.text();
    setForm((current) => ({
      ...current,
      name: current.name || file.name.replace(/\.(tex|md|markdown|txt)$/i, ""),
      sourceText: text,
    }));
    toast.success(`Loaded ${file.name}`);
  };

  return (
    <>
      <PageHeading
        title="Your resumes"
        lede="Keep a few originals — one leaning backend, one leaning leadership — and pick the closest starting point for each job. Everything you tailor is built from one of these."
      />

      <div className="grid gap-8 lg:grid-cols-[1fr_420px]">
        <div className="space-y-3">
          {resumes.isLoading ? <Spinner /> : null}

          {resumes.data?.length === 0 ? (
            <EmptyState
              title="Nothing to tailor from yet"
              body="Upload the .tex or .md file you actually send to employers. The format is detected for you, and for LaTeX the preamble is kept aside so no model can touch it."
              action={
                <Button variant="primary" onClick={() => fileInput.current?.click()}>
                  <FileUp className="size-4" /> Choose a file
                </Button>
              }
            />
          ) : null}

          {resumes.data?.map((resume) => (
            <Panel key={resume.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="font-serif text-lg text-ink">{resume.name}</h2>
                  {resume.isDefault ? <Tag tone="pencil">Default</Tag> : null}
                  <Tag>{resume.format === "LATEX" ? "LaTeX" : "Markdown"}</Tag>
                </div>
                <p className="mt-1 text-sm text-ink-soft">
                  {resume.sectionCount} sections · {resume.characterCount.toLocaleString()} characters ·
                  updated {formatRelative(resume.updatedAt)}
                </p>
              </div>

              <div className="flex shrink-0 items-center gap-1">
                <Button variant="quiet" onClick={() => startEdit(resume)} className="px-2.5 py-1.5 text-xs">
                  Edit
                </Button>
                {!resume.isDefault ? (
                  <Button
                    variant="ghost"
                    onClick={() => setDefault.mutate(resume.id)}
                    aria-label={`Make ${resume.name} the default`}
                    className="px-2 py-1.5"
                  >
                    <Star className="size-3.5" />
                  </Button>
                ) : null}
                <Button
                  variant="danger"
                  onClick={() => remove.mutate(resume.id)}
                  aria-label={`Delete ${resume.name}`}
                  className="px-2 py-1.5"
                >
                  <Trash2 className="size-3.5" />
                </Button>
              </div>
            </Panel>
          ))}
        </div>

        <Panel className="h-fit p-5">
          <h2 className="font-serif text-lg text-ink">{editingId ? "Edit resume" : "Add a resume"}</h2>

          <form
            className="mt-4 space-y-4"
            onSubmit={(event) => {
              event.preventDefault();
              save.mutate(form);
            }}
          >
            <Field label="Name" hint="For you, not for employers.">
              <Input
                required
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="Backend-leaning"
              />
            </Field>

            <div>
              <input
                ref={fileInput}
                type="file"
                accept=".tex,.md,.markdown,.txt"
                className="sr-only"
                onChange={(e) => onFile(e.target.files?.[0])}
              />
              <Button type="button" variant="quiet" onClick={() => fileInput.current?.click()}>
                <FileUp className="size-4" /> Load a .tex or .md file
              </Button>
            </div>

            <Field label="Source" hint="Paste it here, or load a file above.">
              <Textarea
                required
                rows={14}
                spellCheck={false}
                value={form.sourceText}
                onChange={(e) => setForm({ ...form, sourceText: e.target.value })}
                placeholder={"\\documentclass{article}\n...\n\nor\n\n# Your Name\n## Experience"}
                className="font-mono text-xs leading-relaxed"
              />
            </Field>

            <div className="flex items-center gap-2">
              <Button type="submit" variant="primary" disabled={save.isPending}>
                {save.isPending ? <Spinner className="size-3.5" /> : null}
                {editingId ? "Save changes" : "Add resume"}
              </Button>
              {editingId ? (
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => {
                    setEditingId(null);
                    setForm(BLANK);
                  }}
                >
                  Cancel
                </Button>
              ) : null}
            </div>
          </form>
        </Panel>
      </div>
    </>
  );
}
