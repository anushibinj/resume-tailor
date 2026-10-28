/// Truncates a URL in the middle using an ellipsis (...), preserving the beginning
/// (identifying the website/domain) and the ending (identifying the job ID/listing).
/// Short URLs (shorter than or equal to [maxLength]) remain unchanged.
///
/// Example:
/// Original: https://linkedin.com/jobapplications/view/123456
/// Display:  https://linkedin.com/jobapplic.../123456
///
/// Same algorithm as `frontend/src/lib/url.ts`, so both clients show the same text.
String truncateUrl(String? url, [int maxLength = 40]) {
  if (url == null || url.isEmpty) return '';
  final trimmed = url.trim();
  if (trimmed.length <= maxLength) return trimmed;

  const ellipsis = '...';
  if (maxLength <= ellipsis.length) return trimmed.substring(0, maxLength);

  final available = maxLength - ellipsis.length;
  // Preserve ending (roughly 20% of available space, at least 7 chars if space permits)
  final suffixLen = (available * 0.2).round() < 7 ? 7 : (available * 0.2).round();
  final prefixLen = available - suffixLen < 1 ? 1 : available - suffixLen;

  return '${trimmed.substring(0, prefixLen)}$ellipsis${trimmed.substring(trimmed.length - suffixLen)}';
}

/// Validates that the input is a valid HTTP or HTTPS URL.
/// Rejects non-HTTP schemes (e.g. javascript:, data:, file:) and invalid URL strings.
bool isValidHttpUrl(String? urlString) {
  if (urlString == null || urlString.trim().isEmpty) return false;
  final uri = Uri.tryParse(urlString.trim());
  if (uri == null) return false;
  // Dart's parser is more lenient than the WHATWG one ("not-a-url" parses as a relative
  // path), so a real host is required as well as the scheme.
  return (uri.scheme == 'http' || uri.scheme == 'https') && uri.host.isNotEmpty;
}
