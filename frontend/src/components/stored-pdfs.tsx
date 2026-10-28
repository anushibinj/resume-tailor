"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, Trash2, Upload } from "lucide-react";
import { useRef } from "react";
import { toast } from "sonner";

import { Button, Spinner } from "@/components/ui";
import { api, describeError } from "@/lib/api";
import type { StoredPdf } from "@/lib/types";
import { formatBytes, formatDate } from "@/lib/utils";

/**
 * The PDFs kept with a run: the last one compiled here, and one the user made elsewhere
 * (say, the exported Markdown run through their own converter) and uploaded. Both live in
 * the backend's configured storage, so they survive a page reload and follow the run.
 */
export function StoredPdfs({ runId, pdfEnabled }: { runId: string; pdfEnabled: boolean }) {
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);

  const stored = useQuery({
    queryKey: ["stored-pdfs", runId],
    queryFn: () => api.runs.pdfs.list(runId),
  });

  const upload = useMutation({
    mutationFn: (file: File) => api.runs.pdfs.upload(runId, file),
    onSuccess: (updated) => {
      queryClient.setQueryData(["stored-pdfs", runId], updated);
      toast.success("PDF saved with this run");
    },
    onError: (error: unknown) => toast.error(describeError(error)),
  });

  const remove = useMutation({
    mutationFn: () => api.runs.pdfs.removeUploaded(runId),
    onSuccess: (updated) => queryClient.setQueryData(["stored-pdfs", runId], updated),
    onError: (error: unknown) => toast.error(describeError(error)),
  });

  const downloadCompiled = useMutation({
    mutationFn: () => api.runs.pdfs.downloadCompiled(runId),
    onError: (error: unknown) => toast.error(describeError(error)),
  });

  const downloadUploaded = useMutation({
    mutationFn: () => api.runs.pdfs.downloadUploaded(runId),
    onError: (error: unknown) => toast.error(describeError(error)),
  });

  const pickFile = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    // Cleared so choosing the same file again still fires a change.
    event.target.value = "";
    if (!file) return;
    if (file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf")) {
      toast.error("Choose a PDF file.");
      return;
    }
    upload.mutate(file);
  };

  const confirmRemove = () => {
    if (window.confirm("Remove the PDF you uploaded for this run? This can't be undone.")) {
      remove.mutate();
    }
  };

  if (stored.isLoading) {
    return <Spinner />;
  }
  if (stored.isError || !stored.data) {
    return <p className="text-sm text-ink-soft">{describeError(stored.error)}</p>;
  }

  const { compiled, uploaded } = stored.data;
  const busy = upload.isPending || remove.isPending;

  return (
    <div className="space-y-4">
      {pdfEnabled || compiled ? (
        <div>
          <h3 className="text-xs font-medium uppercase tracking-wide text-ink-faint">Compiled here</h3>
          {compiled ? (
            <PdfRow
              pdf={compiled}
              label="Last compiled PDF"
              onDownload={() => downloadCompiled.mutate()}
              downloading={downloadCompiled.isPending}
            />
          ) : (
            <p className="mt-1 text-sm text-ink-soft">None yet. The PDF button above compiles and keeps one.</p>
          )}
        </div>
      ) : null}

      <div>
        <h3 className="text-xs font-medium uppercase tracking-wide text-ink-faint">Your own PDF</h3>
        {uploaded ? (
          <>
            <PdfRow
              pdf={uploaded}
              label={uploaded.originalFilename ?? "Uploaded PDF"}
              onDownload={() => downloadUploaded.mutate()}
              downloading={downloadUploaded.isPending}
            />
            <div className="mt-2 flex gap-2">
              <Button
                variant="ghost"
                className="px-2.5 py-1 text-xs"
                onClick={() => fileInput.current?.click()}
                disabled={busy}
              >
                {upload.isPending ? <Spinner className="size-3" /> : <Upload className="size-3.5" />}
                Replace
              </Button>
              <Button variant="danger" className="px-2.5 py-1 text-xs" onClick={confirmRemove} disabled={busy}>
                {remove.isPending ? <Spinner className="size-3" /> : <Trash2 className="size-3.5" />}
                Remove
              </Button>
            </div>
          </>
        ) : (
          <>
            <p className="mt-1 text-sm text-ink-soft">
              Made the PDF yourself, from the exported source? Keep it here with this run.
            </p>
            <Button className="mt-2" onClick={() => fileInput.current?.click()} disabled={busy}>
              {upload.isPending ? <Spinner className="size-3.5" /> : <Upload className="size-4" />}
              Upload PDF
            </Button>
          </>
        )}
        <input
          ref={fileInput}
          type="file"
          accept="application/pdf,.pdf"
          className="hidden"
          onChange={pickFile}
        />
      </div>
    </div>
  );
}

function PdfRow({
  pdf,
  label,
  onDownload,
  downloading,
}: {
  pdf: StoredPdf;
  label: string;
  onDownload: () => void;
  downloading: boolean;
}) {
  return (
    <div className="mt-1 flex items-center justify-between gap-3">
      <div className="min-w-0">
        <p className="truncate text-sm text-ink" title={label}>
          {label}
        </p>
        <p className="text-xs text-ink-faint">
          {formatBytes(pdf.sizeBytes)} · {formatDate(pdf.storedAt)}
        </p>
      </div>
      <Button
        variant="ghost"
        className="shrink-0 px-2.5 py-1 text-xs"
        onClick={onDownload}
        disabled={downloading}
        aria-label={`Download ${label}`}
      >
        {downloading ? <Spinner className="size-3" /> : <Download className="size-3.5" />}
      </Button>
    </div>
  );
}
