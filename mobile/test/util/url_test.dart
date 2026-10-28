import 'package:flutter_test/flutter_test.dart';
import 'package:resume_tailor/util/url.dart';

// Ported one for one from frontend/src/lib/url.test.ts, so both clients agree.
void main() {
  test('truncateUrl returns empty string for falsy input', () {
    expect(truncateUrl(''), '');
    expect(truncateUrl(null), '');
  });

  test('truncateUrl keeps short URLs unchanged', () {
    const shortUrl = 'https://example.com/job/123';
    expect(truncateUrl(shortUrl), shortUrl);
  });

  test('truncateUrl keeps exactly maxLength URLs unchanged', () {
    const exactUrl = 'https://example.com/some/path/1234567890';
    expect(exactUrl.length, 40);
    expect(truncateUrl(exactUrl, 40), exactUrl);
  });

  test('truncateUrl middle-truncates long URLs with ellipsis preserving start and end', () {
    final result = truncateUrl('https://linkedin.com/jobapplications/view/123456', 40);
    expect(result, 'https://linkedin.com/jobapplic.../123456');
    expect(result.length, 40);
  });

  test('truncateUrl respects custom maxLength', () {
    final result = truncateUrl('https://linkedin.com/jobapplications/view/123456', 35);
    expect(result.length, 35);
    expect(result, startsWith('https://linkedin.com/'));
    expect(result, contains('...'));
    expect(result, endsWith('/123456'));
  });

  test('isValidHttpUrl accepts valid http and https URLs', () {
    expect(isValidHttpUrl('https://linkedin.com/jobs/view/123'), isTrue);
    expect(isValidHttpUrl('http://localhost:3000/runs/1'), isTrue);
    expect(isValidHttpUrl('  https://acme.com/careers  '), isTrue);
  });

  test('isValidHttpUrl rejects non-http schemes and malformed strings', () {
    expect(isValidHttpUrl('javascript:alert(1)'), isFalse);
    expect(isValidHttpUrl('data:text/html,test'), isFalse);
    expect(isValidHttpUrl('ftp://example.com'), isFalse);
    expect(isValidHttpUrl('file:///etc/passwd'), isFalse);
    expect(isValidHttpUrl('not-a-url'), isFalse);
    expect(isValidHttpUrl(''), isFalse);
    expect(isValidHttpUrl(null), isFalse);
  });
}
