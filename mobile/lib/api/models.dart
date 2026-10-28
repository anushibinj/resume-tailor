/// Mirrors the backend DTOs in com.resumetailor.* -- and `frontend/src/lib/types.ts`,
/// which mirrors the same DTOs for the web client.
library;

typedef Json = Map<String, dynamic>;

String? _str(Object? value) => value as String?;
int? _int(Object? value) => (value as num?)?.toInt();
double? _double(Object? value) => (value as num?)?.toDouble();
List<Json> _list(Object? value) => ((value as List?) ?? const []).cast<Json>();

enum Role { normalUser, orgAdmin, admin }

Role _role(String? value) => switch (value) {
      'ADMIN' => Role.admin,
      'ORG_ADMIN' => Role.orgAdmin,
      _ => Role.normalUser,
    };

class AuthUser {
  const AuthUser({
    required this.id,
    required this.email,
    required this.displayName,
    required this.pictureUrl,
    required this.role,
  });

  final String id;
  final String email;
  final String displayName;
  final String? pictureUrl;
  final Role role;

  factory AuthUser.fromJson(Json json) => AuthUser(
        id: json['id'] as String,
        email: json['email'] as String,
        displayName: json['displayName'] as String,
        pictureUrl: _str(json['pictureUrl']),
        role: _role(_str(json['role'])),
      );
}

class AuthResponse {
  const AuthResponse({required this.token, required this.user});

  final String token;
  final AuthUser user;

  factory AuthResponse.fromJson(Json json) =>
      AuthResponse(token: json['token'] as String, user: AuthUser.fromJson(json['user'] as Json));
}

enum ResumeFormat {
  latex('LATEX'),
  markdown('MARKDOWN');

  const ResumeFormat(this.wire);
  final String wire;

  static ResumeFormat parse(String? value) => value == 'LATEX' ? latex : markdown;

  String get label => this == latex ? 'LaTeX' : 'Markdown';
  String get extension => this == latex ? 'tex' : 'md';
}

enum RunStatus { pending, running, completed, failed }

RunStatus _runStatus(String? value) => switch (value) {
      'PENDING' => RunStatus.pending,
      'RUNNING' => RunStatus.running,
      'COMPLETED' => RunStatus.completed,
      _ => RunStatus.failed,
    };

enum KeywordImportance { required, preferred, nice }

KeywordImportance _importance(String? value) => switch (value) {
      'REQUIRED' => KeywordImportance.required,
      'PREFERRED' => KeywordImportance.preferred,
      _ => KeywordImportance.nice,
    };

enum SuggestionKind { bullet, skill, summary }

SuggestionKind _kind(String? value) => switch (value) {
      'BULLET' => SuggestionKind.bullet,
      'SUMMARY' => SuggestionKind.summary,
      _ => SuggestionKind.skill,
    };

enum SuggestionStatus {
  proposed('PROPOSED'),
  accepted('ACCEPTED'),
  rejected('REJECTED');

  const SuggestionStatus(this.wire);
  final String wire;

  static SuggestionStatus parse(String? value) => switch (value) {
        'ACCEPTED' => accepted,
        'REJECTED' => rejected,
        _ => proposed,
      };
}

/// Gap analysis runs after the rewrite, so it has its own status.
enum GapsStatus { pending, running, completed, failed, outdated }

GapsStatus _gapsStatus(String? value) => switch (value) {
      'PENDING' => GapsStatus.pending,
      'RUNNING' => GapsStatus.running,
      'COMPLETED' => GapsStatus.completed,
      'OUTDATED' => GapsStatus.outdated,
      _ => GapsStatus.failed,
    };

/// The summary length options are written after the rewrite, so they have a status of their own.
enum SummaryStatus { pending, running, completed, failed, none }

SummaryStatus _summaryStatus(String? value) => switch (value) {
      'PENDING' => SummaryStatus.pending,
      'RUNNING' => SummaryStatus.running,
      'COMPLETED' => SummaryStatus.completed,
      'FAILED' => SummaryStatus.failed,
      _ => SummaryStatus.none,
    };

class ResumeSummary {
  const ResumeSummary({
    required this.id,
    required this.name,
    required this.format,
    required this.isDefault,
    required this.sectionCount,
    required this.characterCount,
    required this.createdAt,
    required this.updatedAt,
  });

  final String id;
  final String name;
  final ResumeFormat format;
  final bool isDefault;
  final int sectionCount;
  final int characterCount;
  final String createdAt;
  final String updatedAt;

  factory ResumeSummary.fromJson(Json json) => ResumeSummary(
        id: json['id'] as String,
        name: json['name'] as String,
        format: ResumeFormat.parse(_str(json['format'])),
        isDefault: json['isDefault'] as bool? ?? false,
        sectionCount: _int(json['sectionCount']) ?? 0,
        characterCount: _int(json['characterCount']) ?? 0,
        createdAt: json['createdAt'] as String,
        updatedAt: json['updatedAt'] as String,
      );
}

class SectionSummary {
  const SectionSummary({required this.title, required this.level, required this.characterCount});

  final String title;
  final int level;
  final int characterCount;

  factory SectionSummary.fromJson(Json json) => SectionSummary(
        title: json['title'] as String,
        level: _int(json['level']) ?? 1,
        characterCount: _int(json['characterCount']) ?? 0,
      );
}

class ResumeDetail {
  const ResumeDetail({
    required this.id,
    required this.name,
    required this.format,
    required this.isDefault,
    required this.sourceText,
    required this.sections,
  });

  final String id;
  final String name;
  final ResumeFormat format;
  final bool isDefault;
  final String sourceText;
  final List<SectionSummary> sections;

  factory ResumeDetail.fromJson(Json json) => ResumeDetail(
        id: json['id'] as String,
        name: json['name'] as String,
        format: ResumeFormat.parse(_str(json['format'])),
        isDefault: json['isDefault'] as bool? ?? false,
        sourceText: _str(json['sourceText']) ?? '',
        sections: _list(json['sections']).map(SectionSummary.fromJson).toList(),
      );
}

class SaveResumeRequest {
  const SaveResumeRequest({required this.name, required this.sourceText});

  final String name;
  final String sourceText;

  Json toJson() => {'name': name, 'sourceText': sourceText};
}

class LlmProfile {
  const LlmProfile({
    required this.id,
    required this.name,
    required this.baseUrl,
    required this.model,
    required this.temperature,
    required this.maxOutputTokens,
    required this.isDefault,
    required this.hasApiKey,
    required this.apiKeyMask,
  });

  final String id;
  final String name;
  final String baseUrl;
  final String model;
  final double temperature;
  final int maxOutputTokens;
  final bool isDefault;
  final bool hasApiKey;

  /// Masked tail of the key, e.g. "••••4f2a". The key itself never leaves the backend.
  final String apiKeyMask;

  factory LlmProfile.fromJson(Json json) => LlmProfile(
        id: json['id'] as String,
        name: json['name'] as String,
        baseUrl: json['baseUrl'] as String,
        model: json['model'] as String,
        temperature: _double(json['temperature']) ?? 0,
        maxOutputTokens: _int(json['maxOutputTokens']) ?? 0,
        isDefault: json['isDefault'] as bool? ?? false,
        hasApiKey: json['hasApiKey'] as bool? ?? false,
        apiKeyMask: _str(json['apiKeyMask']) ?? '',
      );
}

class SaveLlmProfileRequest {
  const SaveLlmProfileRequest({
    required this.name,
    required this.baseUrl,
    required this.model,
    this.apiKey,
    this.temperature,
    this.maxOutputTokens,
  });

  final String name;
  final String baseUrl;

  /// Leave blank when editing to keep the stored key.
  final String? apiKey;
  final String model;
  final double? temperature;
  final int? maxOutputTokens;

  Json toJson() => {
        'name': name,
        'baseUrl': baseUrl,
        'apiKey': apiKey ?? '',
        'model': model,
        if (temperature != null) 'temperature': temperature,
        if (maxOutputTokens != null) 'maxOutputTokens': maxOutputTokens,
      };
}

class TestConnectionResponse {
  const TestConnectionResponse({required this.ok, required this.message, required this.latencyMs});

  final bool ok;
  final String message;
  final int latencyMs;

  factory TestConnectionResponse.fromJson(Json json) => TestConnectionResponse(
        ok: json['ok'] as bool? ?? false,
        message: _str(json['message']) ?? '',
        latencyMs: _int(json['latencyMs']) ?? 0,
      );
}

class SectionDiff {
  const SectionDiff({
    required this.title,
    required this.level,
    required this.originalContent,
    required this.tailoredContent,
    required this.changeType,
    required this.rationale,
  });

  final String title;
  final int level;
  final String originalContent;
  final String tailoredContent;
  final String? changeType;
  final String? rationale;

  factory SectionDiff.fromJson(Json json) => SectionDiff(
        title: _str(json['title']) ?? '',
        level: _int(json['level']) ?? 1,
        originalContent: _str(json['originalContent']) ?? '',
        tailoredContent: _str(json['tailoredContent']) ?? '',
        changeType: _str(json['changeType']),
        rationale: _str(json['rationale']),
      );
}

class Suggestion {
  const Suggestion({
    required this.id,
    required this.kind,
    required this.keyword,
    required this.targetSection,
    required this.content,
    required this.rationale,
    required this.status,
  });

  final String id;
  final SuggestionKind kind;

  /// The requirement this addition addresses; null for ones made before gap analysis.
  final String? keyword;
  final String? targetSection;
  final String content;
  final String? rationale;
  final SuggestionStatus status;

  factory Suggestion.fromJson(Json json) => Suggestion(
        id: json['id'] as String,
        kind: _kind(_str(json['kind'])),
        keyword: _str(json['keyword']),
        targetSection: _str(json['targetSection']),
        content: _str(json['content']) ?? '',
        rationale: _str(json['rationale']),
        status: SuggestionStatus.parse(_str(json['status'])),
      );
}

class KeywordResult {
  const KeywordResult({
    required this.keyword,
    required this.importance,
    required this.covered,
    required this.evidence,
    required this.addition,
    required this.description,
  });

  final String keyword;
  final KeywordImportance importance;

  /// The model's judgment, with evidence quoted from the resume.
  final bool covered;
  final String? evidence;

  /// Offered when the requirement is not covered, so the gap can be closed in one click.
  final Suggestion? addition;

  /// What the requirement is, in plain words. Comes from a glossary shared by every user,
  /// so it is null only until some model has explained the term.
  final String? description;

  /// A requirement counts as covered once its offered addition has been accepted.
  bool get isCovered => covered || addition?.status == SuggestionStatus.accepted;

  factory KeywordResult.fromJson(Json json) => KeywordResult(
        keyword: json['keyword'] as String,
        importance: _importance(_str(json['importance'])),
        covered: json['covered'] as bool? ?? false,
        evidence: _str(json['evidence']),
        addition: json['addition'] == null ? null : Suggestion.fromJson(json['addition'] as Json),
        description: _str(json['description']),
      );
}

class SummaryVariant {
  const SummaryVariant({required this.lines, required this.text});

  /// The length asked for. An estimate: only compiling the PDF shows the real line count.
  final int lines;
  final String text;

  factory SummaryVariant.fromJson(Json json) =>
      SummaryVariant(lines: _int(json['lines']) ?? 0, text: _str(json['text']) ?? '');
}

/// The resume's summary written at several lengths, one of which is in the document.
class SummaryOptions {
  const SummaryOptions({
    required this.status,
    required this.error,
    required this.selectedLines,
    required this.variants,
  });

  final SummaryStatus status;
  final String? error;

  /// The length in the document now; null when the summary is exactly as the model wrote it.
  final int? selectedLines;

  /// Shortest first. Empty until written, or when the resume has no summary to resize.
  final List<SummaryVariant> variants;

  static const empty =
      SummaryOptions(status: SummaryStatus.none, error: null, selectedLines: null, variants: []);

  SummaryOptions withSelectedLines(int lines) =>
      SummaryOptions(status: status, error: error, selectedLines: lines, variants: variants);

  factory SummaryOptions.fromJson(Json? json) {
    if (json == null) return empty;
    return SummaryOptions(
      status: _summaryStatus(_str(json['status'])),
      error: _str(json['error']),
      selectedLines: _int(json['selectedLines']),
      variants: _list(json['variants']).map(SummaryVariant.fromJson).toList(),
    );
  }
}

class RunSummary {
  const RunSummary({
    required this.id,
    required this.status,
    required this.company,
    required this.role,
    required this.jobUrl,
    required this.resumeName,
    required this.format,
    required this.matchScore,
    required this.createdAt,
  });

  final String id;
  final RunStatus status;
  final String? company;
  final String? role;
  final String? jobUrl;
  final String? resumeName;
  final ResumeFormat format;
  final int? matchScore;
  final String createdAt;

  factory RunSummary.fromJson(Json json) => RunSummary(
        id: json['id'] as String,
        status: _runStatus(_str(json['status'])),
        company: _str(json['company']),
        role: _str(json['role']),
        jobUrl: _str(json['jobUrl']),
        resumeName: _str(json['resumeName']),
        format: ResumeFormat.parse(_str(json['format'])),
        matchScore: _int(json['matchScore']),
        createdAt: json['createdAt'] as String,
      );
}

class RunDetail {
  const RunDetail({
    required this.id,
    required this.status,
    required this.gapsStatus,
    required this.gapsError,
    required this.format,
    required this.company,
    required this.role,
    required this.jobUrl,
    required this.resumeName,
    required this.jobDescriptionText,
    required this.tailoredSource,
    required this.modelUsed,
    required this.promptTokens,
    required this.completionTokens,
    required this.matchScore,
    required this.errorMessage,
    required this.sections,
    required this.keywords,
    required this.otherSuggestions,
    required this.summary,
    required this.createdAt,
  });

  final String id;
  final RunStatus status;
  final GapsStatus gapsStatus;
  final String? gapsError;
  final ResumeFormat format;
  final String? company;
  final String? role;
  final String? jobUrl;
  final String? resumeName;
  final String? jobDescriptionText;
  final String? tailoredSource;
  final String? modelUsed;
  final int? promptTokens;
  final int? completionTokens;
  final int? matchScore;
  final String? errorMessage;
  final List<SectionDiff> sections;
  final List<KeywordResult> keywords;

  /// Additions not tied to a current requirement, e.g. kept from an earlier check.
  final List<Suggestion> otherSuggestions;
  final SummaryOptions summary;
  final String createdAt;

  bool get isRewriting => status == RunStatus.pending || status == RunStatus.running;

  /// Any work still outstanding. The summary options and the gap check both run after the
  /// rewrite is already saved, so the run can be COMPLETED while either is still being
  /// worked on.
  bool get isWorking =>
      isRewriting ||
      gapsStatus == GapsStatus.pending ||
      gapsStatus == GapsStatus.running ||
      summary.status == SummaryStatus.pending ||
      summary.status == SummaryStatus.running;

  RunDetail withSummary(SummaryOptions value) => RunDetail(
        id: id,
        status: status,
        gapsStatus: gapsStatus,
        gapsError: gapsError,
        format: format,
        company: company,
        role: role,
        jobUrl: jobUrl,
        resumeName: resumeName,
        jobDescriptionText: jobDescriptionText,
        tailoredSource: tailoredSource,
        modelUsed: modelUsed,
        promptTokens: promptTokens,
        completionTokens: completionTokens,
        matchScore: matchScore,
        errorMessage: errorMessage,
        sections: sections,
        keywords: keywords,
        otherSuggestions: otherSuggestions,
        summary: value,
        createdAt: createdAt,
      );

  factory RunDetail.fromJson(Json json) => RunDetail(
        id: json['id'] as String,
        status: _runStatus(_str(json['status'])),
        gapsStatus: _gapsStatus(_str(json['gapsStatus'])),
        gapsError: _str(json['gapsError']),
        format: ResumeFormat.parse(_str(json['format'])),
        company: _str(json['company']),
        role: _str(json['role']),
        jobUrl: _str(json['jobUrl']),
        resumeName: _str(json['resumeName']),
        jobDescriptionText: _str(json['jobDescriptionText']),
        tailoredSource: _str(json['tailoredSource']),
        modelUsed: _str(json['modelUsed']),
        promptTokens: _int(json['promptTokens']),
        completionTokens: _int(json['completionTokens']),
        matchScore: _int(json['matchScore']),
        errorMessage: _str(json['errorMessage']),
        sections: _list(json['sections']).map(SectionDiff.fromJson).toList(),
        keywords: _list(json['keywords']).map(KeywordResult.fromJson).toList(),
        otherSuggestions: _list(json['otherSuggestions']).map(Suggestion.fromJson).toList(),
        summary: SummaryOptions.fromJson(json['summary'] as Json?),
        createdAt: json['createdAt'] as String,
      );
}

class CreateRunRequest {
  const CreateRunRequest({
    required this.resumeId,
    required this.jdText,
    this.company,
    this.role,
    this.jobUrl,
  });

  final String? resumeId;
  final String jdText;
  final String? company;
  final String? role;
  final String? jobUrl;

  Json toJson() => {
        'resumeId': resumeId,
        'jdText': jdText,
        if (company != null) 'company': company,
        if (role != null) 'role': role,
        if (jobUrl != null) 'jobUrl': jobUrl,
      };
}

class PageOf<T> {
  const PageOf({required this.content, required this.totalElements});

  final List<T> content;
  final int totalElements;

  factory PageOf.fromJson(Json json, T Function(Json) item) => PageOf(
        content: _list(json['content']).map(item).toList(),
        totalElements: _int(json['totalElements']) ?? 0,
      );
}

enum QuestionStatus { completed, failed }

/// One single-shot question about a resume and its answer. There is no thread id: every
/// question is answered from the resume alone, never from earlier questions, so this is a
/// flat history entry, not a conversation turn.
class ResumeQuestion {
  const ResumeQuestion({
    required this.id,
    required this.resumeId,
    required this.question,
    required this.answer,
    required this.status,
    required this.errorMessage,
    required this.createdAt,
  });

  final String id;
  final String resumeId;
  final String question;
  final String? answer;
  final QuestionStatus status;
  final String? errorMessage;
  final String createdAt;

  factory ResumeQuestion.fromJson(Json json) => ResumeQuestion(
        id: json['id'] as String,
        resumeId: json['resumeId'] as String,
        question: json['question'] as String,
        answer: _str(json['answer']),
        status: _str(json['status']) == 'COMPLETED' ? QuestionStatus.completed : QuestionStatus.failed,
        errorMessage: _str(json['errorMessage']),
        createdAt: json['createdAt'] as String,
      );
}
