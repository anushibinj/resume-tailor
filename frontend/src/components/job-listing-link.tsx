"use client";

import { ExternalLink } from "lucide-react";
import { isValidHttpUrl, truncateUrl } from "@/lib/url";
import { cn } from "@/lib/utils";

interface JobListingLinkProps {
  url: string | null | undefined;
  className?: string;
  maxLength?: number;
}

/**
 * Renders a job listing URL with middle-truncation and a compact button to open
 * the original posting in a new browser tab.
 *
 * If no URL is provided, renders nothing.
 * If the URL is invalid/non-HTTP, external navigation is disabled.
 */
export function JobListingLink({ url, className, maxLength = 40 }: JobListingLinkProps) {
  if (!url || !url.trim()) {
    return null;
  }

  const trimmedUrl = url.trim();
  const valid = isValidHttpUrl(trimmedUrl);
  const displayUrl = truncateUrl(trimmedUrl, maxLength);

  if (!valid) {
    return (
      <span
        className={cn("inline-block max-w-full truncate font-mono text-xs text-ink-soft", className)}
        title={trimmedUrl}
      >
        {displayUrl}
      </span>
    );
  }

  return (
    <div
      className={cn("inline-flex max-w-full items-center gap-1.5 text-xs", className)}
      onClick={(e) => e.stopPropagation()}
    >
      <a
        href={trimmedUrl}
        target="_blank"
        rel="noopener noreferrer"
        title={trimmedUrl}
        className="max-w-full truncate font-mono text-ink-soft underline-offset-2 transition-colors hover:text-pencil hover:underline"
      >
        {displayUrl}
      </a>
      <a
        href={trimmedUrl}
        target="_blank"
        rel="noopener noreferrer"
        aria-label="Open original job listing in a new tab"
        title="Open original job listing in a new tab"
        className="inline-flex shrink-0 items-center justify-center rounded-sm border border-transparent p-1 text-ink-soft transition-colors hover:border-rule hover:bg-surface-sunk hover:text-pencil"
      >
        <ExternalLink className="size-3.5" />
      </a>
    </div>
  );
}
