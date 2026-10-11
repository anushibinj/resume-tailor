/**
 * Truncates a URL in the middle using an ellipsis (...), preserving the beginning
 * (identifying the website/domain) and the ending (identifying the job ID/listing).
 * Short URLs (shorter than or equal to maxLength) remain unchanged.
 *
 * Example:
 * Original: https://linkedin.com/jobapplications/view/123456
 * Display:  https://linkedin.com/jobapplic.../123456
 */
export function truncateUrl(url: string | null | undefined, maxLength = 40): string {
  if (!url) return "";
  const trimmed = url.trim();
  if (trimmed.length <= maxLength) {
    return trimmed;
  }

  const ellipsis = "...";
  if (maxLength <= ellipsis.length) {
    return trimmed.slice(0, maxLength);
  }

  const available = maxLength - ellipsis.length;
  // Preserve ending (roughly 20% of available space, at least 7 chars if space permits)
  const suffixLen = Math.max(7, Math.round(available * 0.2));
  const prefixLen = Math.max(1, available - suffixLen);

  return `${trimmed.slice(0, prefixLen)}${ellipsis}${trimmed.slice(-suffixLen)}`;
}

/**
 * Validates that the input is a valid HTTP or HTTPS URL.
 * Rejects non-HTTP schemes (e.g. javascript:, data:, file:) and invalid URL strings.
 */
export function isValidHttpUrl(urlString: string | null | undefined): boolean {
  if (!urlString) return false;
  try {
    const url = new URL(urlString.trim());
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}
