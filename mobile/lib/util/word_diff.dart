/// One run of a word diff: text both sides share, or text only one side has.
class DiffPart {
  const DiffPart(this.value, {this.added = false, this.removed = false});

  final String value;
  final bool added;
  final bool removed;

  @override
  bool operator ==(Object other) =>
      other is DiffPart && other.value == value && other.added == added && other.removed == removed;

  @override
  int get hashCode => Object.hash(value, added, removed);

  @override
  String toString() => added ? '+[$value]' : removed ? '-[$value]' : '[$value]';
}

// Words, runs of whitespace, and single punctuation marks -- the same granularity as
// jsdiff's diffWords, which the web client uses.
final _token = RegExp(r'[\p{L}\p{N}_]+|\s+|[^\p{L}\p{N}_\s]', unicode: true);

List<String> _tokenize(String text) => _token.allMatches(text).map((m) => m.group(0)!).toList();

/// Above this many table cells the texts have so little in common that a token-level
/// diff would only be noise -- and the table would be costly on a phone.
const _maxCells = 16000000;

/// A word-level diff of [original] against [tailored]: equal runs, then for each change
/// the removed text before the added text, mirroring jsdiff's `diffWords` output.
List<DiffPart> diffWords(String original, String tailored) {
  final a = _tokenize(original);
  final b = _tokenize(tailored);

  // Common prefix and suffix need no table.
  var start = 0;
  while (start < a.length && start < b.length && a[start] == b[start]) {
    start++;
  }
  var endA = a.length;
  var endB = b.length;
  while (endA > start && endB > start && a[endA - 1] == b[endB - 1]) {
    endA--;
    endB--;
  }

  final parts = <DiffPart>[];
  void push(String value, {bool added = false, bool removed = false}) {
    if (value.isEmpty) return;
    if (parts.isNotEmpty && parts.last.added == added && parts.last.removed == removed) {
      parts[parts.length - 1] = DiffPart(parts.last.value + value, added: added, removed: removed);
    } else {
      parts.add(DiffPart(value, added: added, removed: removed));
    }
  }

  push(a.sublist(0, start).join());

  final n = endA - start;
  final m = endB - start;
  if (n * m > _maxCells) {
    push(a.sublist(start, endA).join(), removed: true);
    push(b.sublist(start, endB).join(), added: true);
  } else if (n > 0 || m > 0) {
    // lcs[i][j] = longest common subsequence of a[start+i..] and b[start+j..].
    final width = m + 1;
    final lcs = List<int>.filled((n + 1) * width, 0);
    for (var i = n - 1; i >= 0; i--) {
      for (var j = m - 1; j >= 0; j--) {
        lcs[i * width + j] = a[start + i] == b[start + j]
            ? lcs[(i + 1) * width + j + 1] + 1
            : (lcs[(i + 1) * width + j] >= lcs[i * width + j + 1]
                ? lcs[(i + 1) * width + j]
                : lcs[i * width + j + 1]);
      }
    }

    // Removals and additions inside one change are buffered so each change reads as
    // "what went, then what came", never interleaved token by token.
    final removed = StringBuffer();
    final added = StringBuffer();
    void flush() {
      push(removed.toString(), removed: true);
      push(added.toString(), added: true);
      removed.clear();
      added.clear();
    }

    var i = 0;
    var j = 0;
    while (i < n || j < m) {
      if (i < n && j < m && a[start + i] == b[start + j]) {
        flush();
        push(a[start + i]);
        i++;
        j++;
      } else if (j >= m || (i < n && lcs[(i + 1) * width + j] >= lcs[i * width + j + 1])) {
        removed.write(a[start + i]);
        i++;
      } else {
        added.write(b[start + j]);
        j++;
      }
    }
    flush();
  }

  push(a.sublist(endA).join());
  return parts;
}
