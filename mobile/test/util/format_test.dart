import 'package:flutter_test/flutter_test.dart';
import 'package:resume_tailor/util/format.dart';

void main() {
  final now = DateTime.utc(2026, 10, 28, 12);

  test('formatRelative buckets like the web client', () {
    expect(formatRelative(null), '—');
    expect(formatRelative(now.subtract(const Duration(seconds: 30)).toIso8601String(), now: now), 'just now');
    expect(formatRelative(now.subtract(const Duration(minutes: 5)).toIso8601String(), now: now), '5m ago');
    expect(formatRelative(now.subtract(const Duration(hours: 3)).toIso8601String(), now: now), '3h ago');
    expect(formatRelative(now.subtract(const Duration(days: 2)).toIso8601String(), now: now), '2d ago');
  });

  test('formatDate shows a dash for missing values', () {
    expect(formatDate(null), '—');
    expect(formatDate('garbage'), '—');
  });
}
