import 'package:intl/intl.dart';

/// e.g. "28 Oct 2026, 14:05" -- the same fields the web client's `formatDate` shows.
String formatDate(String? value) {
  if (value == null || value.isEmpty) return '—';
  final date = DateTime.tryParse(value);
  if (date == null) return '—';
  return DateFormat('d MMM y, HH:mm').format(date.toLocal());
}

String formatRelative(String? value, {DateTime? now}) {
  if (value == null || value.isEmpty) return '—';
  final then = DateTime.tryParse(value);
  if (then == null) return '—';
  final seconds = ((now ?? DateTime.now()).difference(then).inMilliseconds / 1000).round();
  if (seconds < 60) return 'just now';
  final minutes = (seconds / 60).round();
  if (minutes < 60) return '${minutes}m ago';
  final hours = (minutes / 60).round();
  if (hours < 24) return '${hours}h ago';
  final days = (hours / 24).round();
  if (days < 30) return '${days}d ago';
  return DateFormat('d MMM').format(then.toLocal());
}

/// Thousands separators, like `Number.toLocaleString()` on the web.
String formatCount(int value) => NumberFormat.decimalPattern().format(value);

/// "512 B", "38 KB", "1.4 MB" -- the same buckets as the web client's `formatBytes`.
String formatBytes(int bytes) {
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).round()} KB';
  return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
}
