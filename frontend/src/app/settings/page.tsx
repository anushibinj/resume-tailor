"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plug, Star, Trash2 } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { Button, EmptyState, Field, Input, PageHeading, Panel, Spinner, Tag } from "@/components/ui";
import { api } from "@/lib/api";
import type { LlmProfile, SaveLlmProfileRequest } from "@/lib/types";

const PRESETS = [
  { label: "OpenAI", baseUrl: "https://api.openai.com/v1", model: "gpt-4o-mini" },
  { label: "Groq", baseUrl: "https://api.groq.com/openai/v1", model: "llama-3.3-70b-versatile" },
  { label: "Ollama", baseUrl: "http://localhost:11434/v1", model: "llama3.1" },
  { label: "LM Studio", baseUrl: "http://localhost:1234/v1", model: "local-model" },
];

const BLANK: SaveLlmProfileRequest = {
  name: "",
  baseUrl: "https://api.openai.com/v1",
  apiKey: "",
  model: "gpt-4o-mini",
  temperature: 0.2,
  maxOutputTokens: 8000,
};

export default function SettingsPage() {
  const queryClient = useQueryClient();
  const profiles = useQuery({ queryKey: ["llm-profiles"], queryFn: api.llmProfiles.list });

  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<SaveLlmProfileRequest>(BLANK);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["llm-profiles"] });

  const save = useMutation({
    mutationFn: (body: SaveLlmProfileRequest) =>
      editingId ? api.llmProfiles.update(editingId, body) : api.llmProfiles.create(body),
    onSuccess: () => {
      toast.success(editingId ? "Profile updated" : "Profile added");
      setEditingId(null);
      setForm(BLANK);
      invalidate();
    },
    onError: (error: Error) => toast.error(error.message),
  });

  const test = useMutation({
    mutationFn: api.llmProfiles.test,
    onSuccess: (result) =>
      result.ok
        ? toast.success(`${result.message} (${result.latencyMs}ms)`)
        : toast.error(result.message),
    onError: (error: Error) => toast.error(error.message),
  });

  const remove = useMutation({
    mutationFn: api.llmProfiles.remove,
    onSuccess: () => {
      toast.success("Profile deleted");
      invalidate();
    },
    onError: (error: Error) => toast.error(error.message),
  });

  const setDefault = useMutation({
    mutationFn: api.llmProfiles.setDefault,
    onSuccess: invalidate,
    onError: (error: Error) => toast.error(error.message),
  });

  const startEdit = (profile: LlmProfile) => {
    setEditingId(profile.id);
    setForm({
      name: profile.name,
      baseUrl: profile.baseUrl,
      apiKey: "",
      model: profile.model,
      temperature: profile.temperature,
      maxOutputTokens: profile.maxOutputTokens,
    });
  };

  return (
    <>
      <PageHeading
        title="Your model"
        lede="Resume Tailor doesn't ship with a model. Point it at any endpoint that speaks the OpenAI chat API — hosted or running on this machine."
      />

      <div className="grid gap-8 lg:grid-cols-[1fr_380px]">
        <div className="space-y-3">
          {profiles.isLoading ? <Spinner /> : null}

          {profiles.data?.length === 0 ? (
            <EmptyState
              title="No model configured"
              body="Add a connection on the right to start tailoring. Your API key is encrypted before it's stored and is never sent back to this page."
            />
          ) : null}

          {profiles.data?.map((profile) => (
            <Panel key={profile.id} className="p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <h2 className="font-serif text-lg text-ink">{profile.name}</h2>
                    {profile.isDefault ? <Tag tone="pencil">Default</Tag> : null}
                  </div>
                  <dl className="mt-2 grid gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
                    <Detail label="Model" value={profile.model} />
                    <Detail label="Endpoint" value={profile.baseUrl} />
                    <Detail
                      label="API key"
                      value={profile.hasApiKey ? profile.apiKeyMask : "not set"}
                    />
                    <Detail
                      label="Temperature"
                      value={`${profile.temperature} · max ${profile.maxOutputTokens} tokens`}
                    />
                  </dl>
                </div>

                <div className="flex shrink-0 items-center gap-1">
                  <Button
                    variant="quiet"
                    onClick={() => test.mutate(profile.id)}
                    disabled={test.isPending}
                    className="px-2.5 py-1.5 text-xs"
                  >
                    {test.isPending && test.variables === profile.id ? (
                      <Spinner className="size-3" />
                    ) : (
                      <Plug className="size-3.5" />
                    )}
                    Test
                  </Button>
                  <Button variant="quiet" onClick={() => startEdit(profile)} className="px-2.5 py-1.5 text-xs">
                    Edit
                  </Button>
                  {!profile.isDefault ? (
                    <Button
                      variant="ghost"
                      onClick={() => setDefault.mutate(profile.id)}
                      aria-label={`Make ${profile.name} the default`}
                      className="px-2 py-1.5"
                    >
                      <Star className="size-3.5" />
                    </Button>
                  ) : null}
                  <Button
                    variant="danger"
                    onClick={() => remove.mutate(profile.id)}
                    aria-label={`Delete ${profile.name}`}
                    className="px-2 py-1.5"
                  >
                    <Trash2 className="size-3.5" />
                  </Button>
                </div>
              </div>
            </Panel>
          ))}
        </div>

        <Panel className="h-fit p-5">
          <h2 className="font-serif text-lg text-ink">
            {editingId ? "Edit connection" : "Add a connection"}
          </h2>

          <div className="mt-3 flex flex-wrap gap-1.5">
            {PRESETS.map((preset) => (
              <button
                key={preset.label}
                type="button"
                onClick={() =>
                  setForm((current) => ({
                    ...current,
                    name: current.name || preset.label,
                    baseUrl: preset.baseUrl,
                    model: preset.model,
                  }))
                }
                className="rounded-sm border border-rule-strong px-2 py-1 text-xs text-ink-soft transition-colors hover:border-pencil hover:text-pencil"
              >
                {preset.label}
              </button>
            ))}
          </div>

          <form
            className="mt-5 space-y-4"
            onSubmit={(event) => {
              event.preventDefault();
              save.mutate(form);
            }}
          >
            <Field label="Name">
              <Input
                required
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="OpenAI"
              />
            </Field>
            <Field label="Base URL" hint="Everything up to and including /v1.">
              <Input
                required
                value={form.baseUrl}
                onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
              />
            </Field>
            <Field label="Model">
              <Input
                required
                value={form.model}
                onChange={(e) => setForm({ ...form, model: e.target.value })}
              />
            </Field>
            <Field
              label="API key"
              hint={
                editingId
                  ? "Leave blank to keep the key you already saved."
                  : "Encrypted before it's stored. Local runtimes usually accept any value."
              }
            >
              <Input
                type="password"
                autoComplete="off"
                value={form.apiKey ?? ""}
                onChange={(e) => setForm({ ...form, apiKey: e.target.value })}
                placeholder={editingId ? "••••••••" : "sk-..."}
              />
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Temperature" hint="Low keeps edits conservative.">
                <Input
                  type="number"
                  step="0.1"
                  min="0"
                  max="2"
                  value={form.temperature ?? 0.2}
                  onChange={(e) => setForm({ ...form, temperature: Number(e.target.value) })}
                />
              </Field>
              <Field label="Max output tokens">
                <Input
                  type="number"
                  min="256"
                  step="500"
                  value={form.maxOutputTokens ?? 8000}
                  onChange={(e) => setForm({ ...form, maxOutputTokens: Number(e.target.value) })}
                />
              </Field>
            </div>

            <div className="flex items-center gap-2 pt-1">
              <Button type="submit" variant="primary" disabled={save.isPending}>
                {save.isPending ? <Spinner className="size-3.5" /> : null}
                {editingId ? "Save changes" : "Add connection"}
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

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-ink-faint">{label}</dt>
      <dd className="truncate text-ink-soft">{value}</dd>
    </div>
  );
}
