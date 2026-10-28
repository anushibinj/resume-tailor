import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../state/providers.dart';
import '../theme/app_theme.dart';
import '../widgets/toast.dart';
import '../widgets/ui.dart';
import 'common.dart';

const _presets = [
  (label: 'OpenAI', baseUrl: 'https://api.openai.com/v1', model: 'gpt-4o-mini'),
  (label: 'Groq', baseUrl: 'https://api.groq.com/openai/v1', model: 'llama-3.3-70b-versatile'),
  (label: 'Ollama', baseUrl: 'http://localhost:11434/v1', model: 'llama3.1'),
  (label: 'LM Studio', baseUrl: 'http://localhost:1234/v1', model: 'local-model'),
];

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  String? _testing;

  void _invalidate() => ref.invalidate(profilesProvider);

  Future<void> _openForm([LlmProfile? editing]) =>
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => ProfileFormScreen(editing: editing)));

  Future<void> _test(LlmProfile profile) async {
    setState(() => _testing = profile.id);
    try {
      final result = await ref.read(apiClientProvider).testProfile(profile.id);
      result.ok ? toastSuccess('${result.message} (${result.latencyMs}ms)') : toastError(result.message);
    } catch (error) {
      toastError(describeError(error));
    } finally {
      if (mounted) setState(() => _testing = null);
    }
  }

  Future<void> _run(Future<void> Function() action, {String? success}) async {
    try {
      await action();
      if (success != null) toastSuccess(success);
      _invalidate();
    } catch (error) {
      toastError(describeError(error));
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final profiles = ref.watch(profilesProvider);
    final api = ref.read(apiClientProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openForm(),
        backgroundColor: p.pencil,
        foregroundColor: p.onPencil,
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(2))),
        icon: const Icon(Icons.add),
        label: const Text('Add a connection'),
      ),
      body: RefreshIndicator(
        onRefresh: () => quietly(ref.refresh(profilesProvider.future)),
        child: ListView(
          // Pull-to-refresh needs an overscroll even when the list is shorter than the screen.
          physics: const AlwaysScrollableScrollPhysics(),
          padding: screenPadding.copyWith(bottom: 96),
          children: [
            const PageHeading(
              title: 'Your model',
              lede:
                  "Resume Tailor doesn't ship with a model. Point it at any endpoint that speaks the "
                  'OpenAI chat API — hosted or running on your own machine.',
            ),
            if (profiles.isLoading && !profiles.hasValue) const LoadingBlock(),
            if (profiles.hasError && !profiles.hasValue) ErrorBlock(error: profiles.error!, onRetry: _invalidate),
            if (profiles.value?.isEmpty ?? false)
              const EmptyState(
                title: 'No model configured',
                body:
                    "Add a connection to start tailoring. Your API key is encrypted before it's stored "
                    'and is never sent back to this app.',
              ),
            for (final profile in profiles.value ?? const <LlmProfile>[])
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Panel(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        spacing: 8,
                        children: [
                          Flexible(
                            child: Text(profile.name, style: AppFonts.serif(TextStyle(fontSize: 18, color: p.ink))),
                          ),
                          if (profile.isDefault) const Tag('Default', tone: TagTone.pencil),
                        ],
                      ),
                      const SizedBox(height: 8),
                      _Detail(label: 'Model', value: profile.model),
                      _Detail(label: 'Endpoint', value: profile.baseUrl),
                      _Detail(label: 'API key', value: profile.hasApiKey ? profile.apiKeyMask : 'not set'),
                      _Detail(
                        label: 'Temperature',
                        value: '${_number(profile.temperature)} · max ${profile.maxOutputTokens} tokens',
                      ),
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 4,
                        runSpacing: 4,
                        children: [
                          AppButton(
                            compact: true,
                            icon: Icons.power_outlined,
                            busy: _testing == profile.id,
                            label: 'Test',
                            onPressed: _testing != null ? null : () => _test(profile),
                          ),
                          AppButton(compact: true, label: 'Edit', onPressed: () => _openForm(profile)),
                          if (!profile.isDefault)
                            AppButton(
                              variant: ButtonVariant.ghost,
                              compact: true,
                              icon: Icons.star_outline,
                              semanticLabel: 'Make ${profile.name} the default',
                              onPressed: () => _run(() => api.setDefaultProfile(profile.id)),
                            ),
                          AppButton(
                            variant: ButtonVariant.danger,
                            compact: true,
                            icon: Icons.delete_outline,
                            semanticLabel: 'Delete ${profile.name}',
                            onPressed: () => _run(() => api.deleteProfile(profile.id), success: 'Profile deleted'),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// 0.2 rather than 0.20000000298, and 1 rather than 1.0, like JavaScript prints numbers.
String _number(double value) => value == value.roundToDouble() ? value.toInt().toString() : value.toString();

class _Detail extends StatelessWidget {
  const _Detail({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: TextStyle(fontSize: 12, color: p.inkFaint)),
          Text(
            value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(fontSize: 14, color: p.inkSoft),
          ),
        ],
      ),
    );
  }
}

/// The web's "Add a connection" / "Edit connection" side panel, as its own screen.
class ProfileFormScreen extends ConsumerStatefulWidget {
  const ProfileFormScreen({super.key, this.editing});

  final LlmProfile? editing;

  @override
  ConsumerState<ProfileFormScreen> createState() => _ProfileFormScreenState();
}

class _ProfileFormScreenState extends ConsumerState<ProfileFormScreen> {
  final _formKey = GlobalKey<FormState>();
  late final _name = TextEditingController(text: widget.editing?.name ?? '');
  late final _baseUrl = TextEditingController(text: widget.editing?.baseUrl ?? 'https://api.openai.com/v1');
  late final _model = TextEditingController(text: widget.editing?.model ?? 'gpt-4o-mini');
  final _apiKey = TextEditingController();
  late final _temperature = TextEditingController(text: _number(widget.editing?.temperature ?? 0.2));
  late final _maxTokens = TextEditingController(text: '${widget.editing?.maxOutputTokens ?? 8000}');
  bool _saving = false;

  @override
  void dispose() {
    for (final c in [_name, _baseUrl, _model, _apiKey, _temperature, _maxTokens]) {
      c.dispose();
    }
    super.dispose();
  }

  void _applyPreset(({String label, String baseUrl, String model}) preset) {
    setState(() {
      if (_name.text.isEmpty) _name.text = preset.label;
      _baseUrl.text = preset.baseUrl;
      _model.text = preset.model;
    });
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    final api = ref.read(apiClientProvider);
    final body = SaveLlmProfileRequest(
      name: _name.text,
      baseUrl: _baseUrl.text,
      apiKey: _apiKey.text,
      model: _model.text,
      temperature: double.tryParse(_temperature.text.trim()),
      maxOutputTokens: num.tryParse(_maxTokens.text.trim())?.toInt(),
    );
    try {
      final editing = widget.editing;
      editing != null ? await api.updateProfile(editing.id, body) : await api.createProfile(body);
      toastSuccess(editing != null ? 'Profile updated' : 'Profile added');
      ref.invalidate(profilesProvider);
      if (mounted) Navigator.of(context).pop();
    } catch (error) {
      toastError(describeError(error));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  String? _required(String? value) => value == null || value.isEmpty ? 'Required' : null;

  // Any number in range, never a step: a step grid once rejected valid values like 0.25
  // and 8000 on the web form (see the comment in frontend/src/app/settings/page.tsx).
  String? Function(String?) _range(num min, num max) => (value) {
    if (value == null || value.trim().isEmpty) return null;
    final number = num.tryParse(value.trim());
    if (number == null) return 'Enter a number';
    if (number < min || number > max) return 'Between $min and $max';
    return null;
  };

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final editing = widget.editing != null;
    const numberKeyboard = TextInputType.numberWithOptions(decimal: true);

    return Scaffold(
      appBar: AppBar(title: Text(editing ? 'Edit connection' : 'Add a connection')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: screenPadding,
          keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
          children: [
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                for (final preset in _presets)
                  OutlinedButton(
                    onPressed: () => _applyPreset(preset),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: p.inkSoft,
                      side: BorderSide(color: p.ruleStrong),
                      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(2))),
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      minimumSize: const Size(0, 30),
                      textStyle: const TextStyle(fontSize: 12),
                    ),
                    child: Text(preset.label),
                  ),
              ],
            ),
            const SizedBox(height: 20),
            Field(
              label: 'Name',
              child: TextFormField(
                controller: _name,
                validator: _required,
                decoration: const InputDecoration(hintText: 'OpenAI'),
              ),
            ),
            const SizedBox(height: 16),
            Field(
              label: 'Base URL',
              hint: 'Everything up to and including /v1.',
              child: TextFormField(
                controller: _baseUrl,
                validator: _required,
                keyboardType: TextInputType.url,
                autocorrect: false,
              ),
            ),
            const SizedBox(height: 16),
            Field(
              label: 'Model',
              child: TextFormField(controller: _model, validator: _required, autocorrect: false),
            ),
            const SizedBox(height: 16),
            Field(
              label: 'API key',
              hint: editing
                  ? 'Leave blank to keep the key you already saved.'
                  : "Encrypted before it's stored. Local runtimes usually accept any value.",
              child: TextFormField(
                controller: _apiKey,
                obscureText: true,
                autocorrect: false,
                enableSuggestions: false,
                decoration: InputDecoration(hintText: editing ? '••••••••' : 'sk-...'),
              ),
            ),
            const SizedBox(height: 16),
            Field(
              label: 'Temperature',
              hint: 'Low keeps edits conservative. 0 to 2.',
              child: TextFormField(controller: _temperature, keyboardType: numberKeyboard, validator: _range(0, 2)),
            ),
            const SizedBox(height: 16),
            Field(
              label: 'Max output tokens',
              hint: 'Must fit your whole resume, or the model shortens it to fit.',
              child: TextFormField(
                controller: _maxTokens,
                keyboardType: numberKeyboard,
                validator: _range(256, 200000),
              ),
            ),
            const SizedBox(height: 20),
            Row(
              spacing: 8,
              children: [
                AppButton(
                  variant: ButtonVariant.primary,
                  busy: _saving,
                  label: editing ? 'Save changes' : 'Add connection',
                  onPressed: _saving ? null : _save,
                ),
                AppButton(variant: ButtonVariant.ghost, label: 'Cancel', onPressed: () => Navigator.of(context).pop()),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
