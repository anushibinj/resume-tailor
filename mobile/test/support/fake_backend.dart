import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

typedef Json = Map<String, dynamic>;

const _now = '2026-10-28T10:00:00Z';

/// An in-memory stand-in for the Spring Boot API, speaking the same JSON shapes, so widget
/// tests can drive every screen end to end. Each GET of an unfinished run advances it one
/// step (PENDING -> RUNNING -> COMPLETED), which is what the polling has to cope with.
class FakeBackend {
  static const token = 'app-token';
  static const user = {
    'id': 'u1',
    'email': 'ada@example.com',
    'displayName': 'Ada Lovelace',
    'pictureUrl': null,
    'role': 'NORMAL_USER',
  };

  final List<Json> resumes = [];
  final List<Json> profiles = [];
  final Map<String, Json> runs = {};
  final List<Json> questions = [];
  final List<String> log = [];
  bool pdfEnabled = true;

  /// When set, every authenticated call answers 401, as an expired session would.
  bool expireSession = false;

  int _seq = 0;
  String _id(String prefix) => '$prefix${++_seq}';

  http.Client get client => MockClient((request) async {
        try {
          return await _handle(request);
        } catch (error, stack) {
          // A bug in the fake must not pass for "backend unreachable".
          return _error(500, 'FakeBackend threw: $error\n$stack');
        }
      });

  // --- seeding helpers ---

  Json addResume({String name = 'Backend-leaning', bool isDefault = true, String format = 'MARKDOWN'}) {
    final Json resume = {
      'id': _id('r'),
      'name': name,
      'format': format,
      'isDefault': isDefault,
      'sectionCount': 3,
      'characterCount': 1234,
      'createdAt': _now,
      'updatedAt': _now,
      'sourceText': '# $name\n## Experience\nLed a team of five.',
      'preamble': '',
      'bodyText': '',
      'documentTail': '',
      'sections': [
        {'title': 'Experience', 'level': 2, 'characterCount': 20},
      ],
    };
    resumes.add(resume);
    return resume;
  }

  Json addProfile({String name = 'OpenAI', bool isDefault = true}) {
    final Json profile = {
      'id': _id('p'),
      'name': name,
      'baseUrl': 'https://api.openai.com/v1',
      'model': 'gpt-4o-mini',
      'temperature': 0.2,
      'maxOutputTokens': 8000,
      'isDefault': isDefault,
      'hasApiKey': true,
      'apiKeyMask': '••••4f2a',
      'createdAt': _now,
      'updatedAt': _now,
    };
    profiles.add(profile);
    return profile;
  }

  Json addRun({String status = 'COMPLETED', String role = 'Senior Backend Engineer', String? company = 'Acme'}) {
    final id = _id('run');
    final run = _completedRun(id, role: role, company: company)..['status'] = status;
    if (status != 'COMPLETED') {
      run['gapsStatus'] = 'PENDING';
      run['summary'] = {'status': 'PENDING', 'error': null, 'selectedLines': null, 'variants': []};
      run['sections'] = [];
      run['keywords'] = [];
      run['matchScore'] = null;
    }
    if (status == 'FAILED') run['errorMessage'] = 'The model returned no content';
    runs[id] = run;
    return run;
  }

  Json _suggestion(String keyword, String content, String section) => {
    'id': _id('s'),
    'kind': 'SKILL',
    'keyword': keyword,
    'targetSection': section,
    'content': content,
    'rationale': null,
    'status': 'PROPOSED',
  };

  Json _completedRun(String id, {required String role, String? company}) => {
    'id': id,
    'status': 'COMPLETED',
    'gapsStatus': 'COMPLETED',
    'gapsError': null,
    'format': 'MARKDOWN',
    'company': company,
    'role': role,
    'jobUrl': 'https://linkedin.com/jobapplications/view/123456',
    'resumeId': resumes.isEmpty ? 'r0' : resumes.first['id'],
    'resumeName': resumes.isEmpty ? 'Backend-leaning' : resumes.first['name'],
    'jobDescriptionId': 'jd1',
    'jobDescriptionText': 'We want Java, Kubernetes and Terraform.',
    'originalSource': '# Ada\n## Experience\nLed a team of five.',
    'tailoredSource': '# Ada\n## Experience\nManaged a team of five Java engineers.',
    'modelUsed': 'gpt-4o-mini',
    'promptTokens': 1200,
    'completionTokens': 800,
    'matchScore': 50,
    'errorMessage': null,
    'sections': [
      {
        'title': 'Experience',
        'level': 2,
        'originalContent': 'Led a team of five.',
        'tailoredContent': 'Managed a team of five Java engineers.',
        'changeType': 'REWORDED',
        'rationale': 'Leads with the stack the posting names.',
      },
      {
        'title': 'Education',
        'level': 2,
        'originalContent': 'BSc Mathematics',
        'tailoredContent': 'BSc Mathematics',
        'changeType': null,
        'rationale': null,
      },
    ],
    'keywords': [
      {
        'keyword': 'Java',
        'importance': 'REQUIRED',
        'covered': true,
        'evidence': 'Managed a team of five Java engineers',
        'addition': null,
        'description': null,
      },
      {
        'keyword': 'Kubernetes',
        'importance': 'REQUIRED',
        'covered': false,
        'evidence': null,
        'addition': _suggestion('Kubernetes', 'Kubernetes', 'Skills'),
        'description': 'A container orchestration system.',
      },
      {
        'keyword': 'Terraform',
        'importance': 'PREFERRED',
        'covered': false,
        'evidence': null,
        'addition': _suggestion('Terraform', 'Terraform (infrastructure as code)', 'Skills'),
        'description': null,
      },
    ],
    'otherSuggestions': [],
    'summary': {
      'status': 'COMPLETED',
      'error': null,
      'selectedLines': 5,
      'variants': [
        for (final lines in [4, 5, 6, 7]) {'lines': lines, 'text': 'Summary at $lines lines.'},
      ],
    },
    'createdAt': _now,
    'startedAt': _now,
    'finishedAt': _now,
  };

  // --- routing ---

  Future<http.Response> _handle(http.Request request) async {
    final path = request.url.path;
    final method = request.method;
    log.add('$method ${request.url.path}${request.url.hasQuery ? '?${request.url.query}' : ''}');
    final body = request.body.isEmpty ? null : jsonDecode(request.body);

    if (path == '/api/auth/google' && method == 'POST') {
      if ((body as Json)['idToken'] != 'google-id-token') return _error(400, 'Invalid Google sign-in token');
      return _json({'token': token, 'user': user});
    }

    if (expireSession || request.headers['Authorization'] != 'Bearer $token') {
      return _error(401, 'Unauthorized');
    }

    if (path == '/api/auth/me') return _json(user);

    final segments = request.url.pathSegments.skip(1).toList(); // drop "api"

    switch (segments) {
      case ['resumes'] when method == 'GET':
        return _json([for (final r in resumes) _resumeSummary(r)]);
      case ['resumes'] when method == 'POST':
        final json = body as Json;
        final resume = addResume(name: json['name'], isDefault: resumes.isEmpty);
        resume['sourceText'] = json['sourceText'];
        resume['format'] = (json['sourceText'] as String).contains(r'\documentclass') ? 'LATEX' : 'MARKDOWN';
        return _json(resume);
      case ['resumes', final id] when method == 'GET':
        return _json(_find(resumes, id));
      case ['resumes', final id] when method == 'PUT':
        final resume = _find(resumes, id)..addAll({'name': (body as Json)['name'], 'sourceText': body['sourceText']});
        return _json(resume);
      case ['resumes', final id] when method == 'DELETE':
        resumes.removeWhere((r) => r['id'] == id);
        return http.Response('', 204);
      case ['resumes', final id, 'default']:
        for (final r in resumes) {
          r['isDefault'] = r['id'] == id;
        }
        return _json(_find(resumes, id));
      case ['resumes', final id, 'questions'] when method == 'POST':
        final Json question = {
          'id': _id('q'),
          'resumeId': id,
          'question': (body as Json)['question'],
          'answer': 'My core experience lies in backend systems.',
          'status': 'COMPLETED',
          'errorMessage': null,
          'modelUsed': 'gpt-4o-mini',
          'createdAt': _now,
        };
        questions.insert(0, question);
        return _json(question);
      case ['resumes', final id, 'questions'] when method == 'GET':
        final items = questions.where((q) => q['resumeId'] == id).toList();
        return _json(_page(items));
      case ['resumes', _, 'questions', final qid] when method == 'DELETE':
        questions.removeWhere((q) => q['id'] == qid);
        return http.Response('', 204);

      case ['llm-profiles'] when method == 'GET':
        return _json(profiles);
      case ['llm-profiles'] when method == 'POST':
        final json = body as Json;
        final profile = addProfile(name: json['name'], isDefault: profiles.isEmpty)
          ..addAll({'baseUrl': json['baseUrl'], 'model': json['model']});
        return _json(profile);
      case ['llm-profiles', final id] when method == 'PUT':
        final json = body as Json;
        return _json(
          _find(profiles, id)..addAll({'name': json['name'], 'baseUrl': json['baseUrl'], 'model': json['model']}),
        );
      case ['llm-profiles', final id] when method == 'DELETE':
        profiles.removeWhere((p) => p['id'] == id);
        return http.Response('', 204);
      case ['llm-profiles', final id, 'default']:
        for (final p in profiles) {
          p['isDefault'] = p['id'] == id;
        }
        return _json(_find(profiles, id));
      case ['llm-profiles', _, 'test']:
        return _json({'ok': true, 'message': 'Connected to gpt-4o-mini', 'model': 'gpt-4o-mini', 'latencyMs': 42});

      case ['runs'] when method == 'GET':
        return _json(_page([for (final r in runs.values.toList().reversed) _runSummary(r)]));
      case ['runs'] when method == 'POST':
        final json = body as Json;
        if ((json['jdText'] as String).trim().isEmpty) {
          return _error(400, 'Validation failed', {'jdText': 'must not be blank'});
        }
        final run = addRun(status: 'PENDING', role: json['role'] ?? 'Untitled', company: json['company']);
        return _json(run, 202);
      case ['runs', final id] when method == 'GET':
        final run = runs[id];
        if (run == null) return _error(404, 'Run not found');
        _advance(run);
        return _json(run);
      case ['runs', final id] when method == 'DELETE':
        runs.remove(id);
        return http.Response('', 204);
      case ['runs', final id, 'suggestions', final sid]:
        final run = runs[id]!;
        final status = (body as Json)['status'];
        for (final k in run['keywords'] as List) {
          if (k['addition']?['id'] == sid) k['addition']['status'] = status;
        }
        for (final s in run['otherSuggestions'] as List) {
          if (s['id'] == sid) s['status'] = status;
        }
        return _json(run);
      case ['runs', final id, 'gaps']:
        return _json(runs[id]!..['gapsStatus'] = 'RUNNING', 202);
      case ['runs', final id, 'summary'] when method == 'PUT':
        (runs[id]!['summary'] as Json)['selectedLines'] = (body as Json)['lines'];
        return _json(runs[id]!);
      case ['runs', final id, 'summary'] when method == 'POST':
        (runs[id]!['summary'] as Json)['status'] = 'RUNNING';
        return _json(runs[id]!, 202);
      case ['runs', _, 'pdf', 'available']:
        return _json({'enabled': pdfEnabled});
      case ['runs', _, 'export']:
        return http.Response(
          '# tailored',
          200,
          headers: {'content-type': 'text/markdown', 'content-disposition': 'attachment; filename="resume-acme.md"'},
        );
      case ['runs', _, 'pdf'] when method == 'POST':
        return http.Response(
          '%PDF-1.7',
          200,
          headers: {'content-type': 'application/pdf', 'content-disposition': 'attachment; filename="resume-acme.pdf"'},
        );
    }
    return _error(404, 'No route for $method $path');
  }

  void _advance(Json run) {
    switch (run['status']) {
      case 'PENDING':
        run['status'] = 'RUNNING';
      case 'RUNNING':
        final completed = _completedRun(run['id'], role: run['role'], company: run['company']);
        run
          ..clear()
          ..addAll(completed);
      default:
        if (run['gapsStatus'] == 'RUNNING') run['gapsStatus'] = 'COMPLETED';
        if ((run['summary'] as Json)['status'] == 'RUNNING') (run['summary'] as Json)['status'] = 'COMPLETED';
    }
  }

  Json _find(List<Json> items, String id) => items.firstWhere((item) => item['id'] == id);

  Json _resumeSummary(Json r) => {
    for (final key in ['id', 'name', 'format', 'isDefault', 'sectionCount', 'characterCount', 'createdAt', 'updatedAt'])
      key: r[key],
  };

  Json _runSummary(Json r) => {
    for (final key in [
      'id',
      'status',
      'company',
      'role',
      'jobUrl',
      'resumeName',
      'format',
      'matchScore',
      'createdAt',
      'finishedAt',
    ])
      key: r[key],
  };

  Json _page(List<Object?> items) => {
    'content': items,
    'totalElements': items.length,
    'totalPages': 1,
    'number': 0,
    'size': 50,
  };

  http.Response _json(Object body, [int status = 200]) =>
      http.Response.bytes(utf8.encode(jsonEncode(body)), status, headers: {'content-type': 'application/json'});

  http.Response _error(int status, String message, [Map<String, String> fieldErrors = const {}]) =>
      _json({'message': message, 'fieldErrors': fieldErrors}, status);
}
