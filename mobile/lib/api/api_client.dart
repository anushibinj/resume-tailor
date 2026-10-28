import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import 'models.dart';
import 'token_store.dart';

/// Carries the backend's message so the UI can show what to fix, not just "request failed".
class ApiException implements Exception {
  const ApiException(this.status, this.message, [this.fieldErrors = const {}]);

  final int status;
  final String message;
  final Map<String, String> fieldErrors;

  @override
  String toString() => message;
}

/// Message worth showing a user: a bean-validation failure arrives as "Validation failed"
/// with the offending fields listed separately, which on its own says nothing actionable.
String describeError(Object error) {
  if (error is ApiException) {
    if (error.fieldErrors.isNotEmpty) {
      return error.fieldErrors.entries.map((e) => '${e.key}: ${e.value}').join('; ');
    }
    return error.message;
  }
  return 'Something went wrong';
}

/// A file the backend returned as an attachment, with the name from Content-Disposition.
class DownloadedFile {
  const DownloadedFile({required this.filename, required this.bytes, required this.contentType});

  final String filename;
  final Uint8List bytes;
  final String contentType;
}

/// The Google sign-in exchange is the only call made without a session, so it is the only
/// one that skips the bearer header and the 401 handling. /api/auth/me is *not* exempt: it
/// identifies the caller from the token, so it must send it.
bool _isPublicAuthEndpoint(String path) => path == '/api/auth/google';

/// Every backend endpoint the web client calls, one for one (`frontend/src/lib/api.ts`).
/// The phone calls the backend directly -- there is no proxy in between, the same as the
/// web client.
class ApiClient {
  ApiClient({required this.baseUrl, required this.tokens, http.Client? httpClient})
    : _http = httpClient ?? http.Client();

  final String baseUrl;
  final TokenStore tokens;
  final http.Client _http;

  /// Called when a request comes back 401, after the stale token has been dropped, so the
  /// auth gate can send the user back to sign-in instead of every caller checking status.
  void Function()? onUnauthorized;

  /// [body] is sent as JSON; [rawBody] as-is, with [rawContentType] (a file upload).
  Future<http.Response> _send(
    String method,
    String path, {
    Object? body,
    Uint8List? rawBody,
    String? rawContentType,
    bool authenticated = true,
  }) async {
    final token = tokens.token;
    final request = http.Request(method, Uri.parse('$baseUrl$path'));
    if (body != null) {
      request.headers['Content-Type'] = 'application/json';
      // Encoded to bytes up front so the request carries a Content-Length.
      request.bodyBytes = utf8.encode(jsonEncode(body));
    } else if (rawBody != null) {
      request.headers['Content-Type'] = rawContentType ?? 'application/octet-stream';
      request.bodyBytes = rawBody;
    }
    if (token != null && authenticated && !_isPublicAuthEndpoint(path)) {
      request.headers['Authorization'] = 'Bearer $token';
    }

    http.Response response;
    try {
      response = await http.Response.fromStream(await _http.send(request));
    } catch (_) {
      throw ApiException(0, "Can't reach the backend at $baseUrl. Is it running?");
    }

    if (response.statusCode == 401 && !_isPublicAuthEndpoint(path)) {
      // The session token is missing, expired or was invalidated -- drop it and let the
      // auth gate send the user back to the sign-in screen instead of failing silently.
      await tokens.clear();
      onUnauthorized?.call();
    }

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw _errorFrom(response);
    }
    return response;
  }

  ApiException _errorFrom(http.Response response) {
    var message = 'Request failed (${response.statusCode})';
    var fieldErrors = <String, String>{};
    try {
      final body = jsonDecode(utf8.decode(response.bodyBytes));
      if (body is Map<String, dynamic>) {
        final text = body['message'];
        if (text is String && text.isNotEmpty) message = text;
        final fields = body['fieldErrors'];
        if (fields is Map) {
          fieldErrors = fields.map((key, value) => MapEntry('$key', '$value'));
        }
      }
    } catch (_) {
      // Not JSON -- keep the generic message.
    }
    return ApiException(response.statusCode, message, fieldErrors);
  }

  Future<Object?> _json(String method, String path, {Object? body}) async {
    final response = await _send(method, path, body: body);
    if (response.statusCode == 204 || response.bodyBytes.isEmpty) return null;
    return jsonDecode(utf8.decode(response.bodyBytes));
  }

  Future<Json> _object(String method, String path, {Object? body}) async =>
      (await _json(method, path, body: body)) as Json;

  Future<List<Json>> _array(String path) async => ((await _json('GET', path)) as List).cast<Json>();

  Future<DownloadedFile> _download(String method, String path) async {
    final response = await _send(method, path);
    final disposition = response.headers['content-disposition'] ?? '';
    final filename = RegExp(r'filename="?([^";]+)"?').firstMatch(disposition)?.group(1) ?? 'resume';
    return DownloadedFile(
      filename: filename,
      bytes: response.bodyBytes,
      contentType: response.headers['content-type'] ?? 'application/octet-stream',
    );
  }

  // --- auth ---

  Future<AuthResponse> signInWithGoogle(String idToken) async =>
      AuthResponse.fromJson(await _object('POST', '/api/auth/google', body: {'idToken': idToken}));

  Future<AuthUser> me() async => AuthUser.fromJson(await _object('GET', '/api/auth/me'));

  // --- resumes ---

  Future<List<ResumeSummary>> listResumes() async =>
      (await _array('/api/resumes')).map(ResumeSummary.fromJson).toList();

  Future<ResumeDetail> getResume(String id) async => ResumeDetail.fromJson(await _object('GET', '/api/resumes/$id'));

  Future<ResumeDetail> createResume(SaveResumeRequest body) async =>
      ResumeDetail.fromJson(await _object('POST', '/api/resumes', body: body.toJson()));

  Future<ResumeDetail> updateResume(String id, SaveResumeRequest body) async =>
      ResumeDetail.fromJson(await _object('PUT', '/api/resumes/$id', body: body.toJson()));

  Future<ResumeDetail> setDefaultResume(String id) async =>
      ResumeDetail.fromJson(await _object('POST', '/api/resumes/$id/default'));

  Future<void> deleteResume(String id) async => _json('DELETE', '/api/resumes/$id');

  /// Synchronous: answers in this one round trip, unlike a tailoring run.
  Future<ResumeQuestion> askQuestion(String resumeId, String question) async =>
      ResumeQuestion.fromJson(await _object('POST', '/api/resumes/$resumeId/questions', body: {'question': question}));

  Future<PageOf<ResumeQuestion>> questionHistory(String resumeId, {int page = 0, int size = 50}) async =>
      PageOf.fromJson(
        await _object('GET', '/api/resumes/$resumeId/questions?page=$page&size=$size'),
        ResumeQuestion.fromJson,
      );

  Future<void> deleteQuestion(String resumeId, String questionId) async =>
      _json('DELETE', '/api/resumes/$resumeId/questions/$questionId');

  // --- LLM profiles ---

  Future<List<LlmProfile>> listProfiles() async =>
      (await _array('/api/llm-profiles')).map(LlmProfile.fromJson).toList();

  Future<LlmProfile> createProfile(SaveLlmProfileRequest body) async =>
      LlmProfile.fromJson(await _object('POST', '/api/llm-profiles', body: body.toJson()));

  Future<LlmProfile> updateProfile(String id, SaveLlmProfileRequest body) async =>
      LlmProfile.fromJson(await _object('PUT', '/api/llm-profiles/$id', body: body.toJson()));

  Future<LlmProfile> setDefaultProfile(String id) async =>
      LlmProfile.fromJson(await _object('POST', '/api/llm-profiles/$id/default'));

  Future<TestConnectionResponse> testProfile(String id) async =>
      TestConnectionResponse.fromJson(await _object('POST', '/api/llm-profiles/$id/test'));

  Future<void> deleteProfile(String id) async => _json('DELETE', '/api/llm-profiles/$id');

  // --- runs ---

  Future<PageOf<RunSummary>> listRuns({int page = 0, int size = 20}) async =>
      PageOf.fromJson(await _object('GET', '/api/runs?page=$page&size=$size'), RunSummary.fromJson);

  Future<RunDetail> getRun(String id) async => RunDetail.fromJson(await _object('GET', '/api/runs/$id'));

  Future<RunDetail> createRun(CreateRunRequest body) async =>
      RunDetail.fromJson(await _object('POST', '/api/runs', body: body.toJson()));

  Future<RunDetail> updateSuggestion(String runId, String suggestionId, SuggestionStatus status) async =>
      RunDetail.fromJson(
        await _object('PATCH', '/api/runs/$runId/suggestions/$suggestionId', body: {'status': status.wire}),
      );

  Future<void> deleteRun(String id) async => _json('DELETE', '/api/runs/$id');

  /// Redoes the whole run from its saved resume and posting; returns 202 and the screen polls.
  Future<RunDetail> rerun(String id) async => RunDetail.fromJson(await _object('POST', '/api/runs/$id/rerun'));

  Future<RunDetail> recheckGaps(String id) async => RunDetail.fromJson(await _object('POST', '/api/runs/$id/gaps'));

  /// Instant: switches to a summary length that was already written.
  Future<RunDetail> selectSummary(String id, int lines) async =>
      RunDetail.fromJson(await _object('PUT', '/api/runs/$id/summary', body: {'lines': lines}));

  /// Writes the summary lengths (again); returns 202 and the screen polls until they land.
  Future<RunDetail> generateSummary(String id) async =>
      RunDetail.fromJson(await _object('POST', '/api/runs/$id/summary'));

  Future<bool> pdfAvailable(String id) async =>
      (await _object('GET', '/api/runs/$id/pdf/available'))['enabled'] as bool? ?? false;

  Future<DownloadedFile> downloadSource(String id) => _download('GET', '/api/runs/$id/export');

  Future<DownloadedFile> compilePdf(String id) => _download('POST', '/api/runs/$id/pdf');

  // --- PDFs kept in the backend's storage (local disk or S3) alongside the run ---

  Future<StoredPdfs> storedPdfs(String id) async => StoredPdfs.fromJson(await _object('GET', '/api/runs/$id/pdfs'));

  /// The last compiled PDF, as stored -- no recompile.
  Future<DownloadedFile> downloadCompiledPdf(String id) => _download('GET', '/api/runs/$id/pdf');

  /// Sends the file as the raw request body rather than multipart (the API has none); its
  /// name rides along as a query parameter, for display only.
  Future<StoredPdfs> uploadPdf(String id, Uint8List bytes, String filename) async {
    final response = await _send(
      'PUT',
      '/api/runs/$id/pdfs/uploaded?filename=${Uri.encodeQueryComponent(filename)}',
      rawBody: bytes,
      rawContentType: 'application/pdf',
    );
    return StoredPdfs.fromJson(jsonDecode(utf8.decode(response.bodyBytes)) as Json);
  }

  Future<DownloadedFile> downloadUploadedPdf(String id) => _download('GET', '/api/runs/$id/pdfs/uploaded');

  Future<StoredPdfs> removeUploadedPdf(String id) async =>
      StoredPdfs.fromJson(await _object('DELETE', '/api/runs/$id/pdfs/uploaded'));
}
