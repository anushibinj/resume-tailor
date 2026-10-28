import 'package:flutter_test/flutter_test.dart';
import 'package:resume_tailor/util/word_diff.dart';

String side(List<DiffPart> parts, {required bool original}) =>
    parts.where((p) => original ? !p.added : !p.removed).map((p) => p.value).join();

void main() {
  test('identical text is one equal part', () {
    expect(diffWords('Led a team of five', 'Led a team of five'), [const DiffPart('Led a team of five')]);
  });

  test('a reworded word is removed then added', () {
    expect(diffWords('Led a team of five', 'Managed a team of five'), [
      const DiffPart('Led', removed: true),
      const DiffPart('Managed', added: true),
      const DiffPart(' a team of five'),
    ]);
  });

  test('each side reassembles exactly', () {
    const before = 'Built REST APIs in Java.\nImproved latency by 30%.';
    const after = 'Improved API latency by 30%.\nBuilt REST APIs in Java and Kotlin.';
    final parts = diffWords(before, after);
    expect(side(parts, original: true), before);
    expect(side(parts, original: false), after);
  });

  test('pure additions and removals', () {
    expect(diffWords('', 'New section'), [const DiffPart('New section', added: true)]);
    expect(diffWords('Gone', ''), [const DiffPart('Gone', removed: true)]);
  });

  test('LaTeX markup is tokenised around punctuation', () {
    final parts = diffWords(r'\item Java', r'\item Java, Kotlin');
    expect(parts.first, const DiffPart(r'\item Java'));
    expect(parts.last, const DiffPart(', Kotlin', added: true));
  });
}
