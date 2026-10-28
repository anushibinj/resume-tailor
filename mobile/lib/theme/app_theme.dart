import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// "The editing desk" -- this app's job is proofing a document, so the palette is paper
/// and ink with one editor's-pencil accent. Colour carries meaning here: pencil = the
/// editor's mark, strike = removed, gap = a requirement you don't cover.
///
/// Mirrors the CSS custom properties in `frontend/src/app/globals.css` one for one, so
/// the two clients read as the same product.
@immutable
class Palette extends ThemeExtension<Palette> {
  const Palette({
    required this.paper,
    required this.surface,
    required this.surfaceSunk,
    required this.ink,
    required this.inkSoft,
    required this.inkFaint,
    required this.rule,
    required this.ruleStrong,
    required this.pencil,
    required this.pencilSoft,
    required this.onPencil,
    required this.strike,
    required this.strikeSoft,
    required this.gap,
    required this.gapSoft,
  });

  final Color paper;
  final Color surface;
  final Color surfaceSunk;
  final Color ink;
  final Color inkSoft;
  final Color inkFaint;
  final Color rule;
  final Color ruleStrong;
  final Color pencil;
  final Color pencilSoft;

  /// Text drawn on top of [pencil]. Flips with the theme because the accent itself goes
  /// from dark blue to light blue.
  final Color onPencil;
  final Color strike;
  final Color strikeSoft;
  final Color gap;
  final Color gapSoft;

  static const light = Palette(
    paper: Color(0xFFF6F7F4),
    surface: Color(0xFFFFFFFF),
    surfaceSunk: Color(0xFFEEF0EC),
    ink: Color(0xFF1D2B2F),
    inkSoft: Color(0xFF5C6F74),
    inkFaint: Color(0xFF8B9A9E),
    rule: Color(0xFFDCE1DC),
    ruleStrong: Color(0xFFC3CCC4),
    pencil: Color(0xFF2F5D8C),
    pencilSoft: Color(0xFFE4ECF5),
    onPencil: Color(0xFFFFFFFF),
    strike: Color(0xFF8A4A58),
    strikeSoft: Color(0xFFF4E7EA),
    gap: Color(0xFF8A6D2B),
    gapSoft: Color(0xFFF5EFDF),
  );

  static const dark = Palette(
    paper: Color(0xFF141A1C),
    surface: Color(0xFF1B2326),
    surfaceSunk: Color(0xFF101517),
    ink: Color(0xFFE6ECEA),
    inkSoft: Color(0xFF9DB0B3),
    inkFaint: Color(0xFF6B7E82),
    rule: Color(0xFF2A3538),
    ruleStrong: Color(0xFF3B4A4E),
    pencil: Color(0xFF7DABDD),
    pencilSoft: Color(0xFF1C2C3D),
    onPencil: Color(0xFF10191F),
    strike: Color(0xFFD094A0),
    strikeSoft: Color(0xFF332226),
    gap: Color(0xFFD3B166),
    gapSoft: Color(0xFF2E2818),
  );

  @override
  Palette copyWith() => this;

  @override
  Palette lerp(Palette? other, double t) {
    if (other == null) return this;
    Color mix(Color a, Color b) => Color.lerp(a, b, t)!;
    return Palette(
      paper: mix(paper, other.paper),
      surface: mix(surface, other.surface),
      surfaceSunk: mix(surfaceSunk, other.surfaceSunk),
      ink: mix(ink, other.ink),
      inkSoft: mix(inkSoft, other.inkSoft),
      inkFaint: mix(inkFaint, other.inkFaint),
      rule: mix(rule, other.rule),
      ruleStrong: mix(ruleStrong, other.ruleStrong),
      pencil: mix(pencil, other.pencil),
      pencilSoft: mix(pencilSoft, other.pencilSoft),
      onPencil: mix(onPencil, other.onPencil),
      strike: mix(strike, other.strike),
      strikeSoft: mix(strikeSoft, other.strikeSoft),
      gap: mix(gap, other.gap),
      gapSoft: mix(gapSoft, other.gapSoft),
    );
  }
}

/// The three faces the web app uses: a sans for the interface, a serif so resume content
/// reads as a document, and a mono reserved for raw LaTeX / Markdown source, where
/// character alignment is functional.
class AppFonts {
  AppFonts._();

  /// Widget tests have no network, and google_fonts fetches on first use. Tests switch
  /// this off so the platform fallback face is used instead.
  static bool useGoogleFonts = true;

  static TextStyle sans([TextStyle? style]) =>
      useGoogleFonts ? GoogleFonts.instrumentSans(textStyle: style) : (style ?? const TextStyle());

  static TextStyle serif([TextStyle? style]) => useGoogleFonts
      ? GoogleFonts.newsreader(textStyle: style)
      : (style ?? const TextStyle()).copyWith(fontFamily: 'serif');

  static TextStyle mono([TextStyle? style]) => useGoogleFonts
      ? GoogleFonts.ibmPlexMono(textStyle: style)
      : (style ?? const TextStyle()).copyWith(fontFamily: 'monospace');
}

extension PaletteContext on BuildContext {
  Palette get palette => Theme.of(this).extension<Palette>()!;
}

ThemeData buildTheme(Brightness brightness) {
  final p = brightness == Brightness.dark ? Palette.dark : Palette.light;
  final base = ThemeData(brightness: brightness, useMaterial3: true);
  final textTheme = (AppFonts.useGoogleFonts
          ? GoogleFonts.instrumentSansTextTheme(base.textTheme)
          : base.textTheme)
      .apply(bodyColor: p.ink, displayColor: p.ink);

  const radius = BorderRadius.all(Radius.circular(2));
  OutlineInputBorder border(Color color) =>
      OutlineInputBorder(borderRadius: radius, borderSide: BorderSide(color: color));

  return base.copyWith(
    scaffoldBackgroundColor: p.paper,
    colorScheme: ColorScheme.fromSeed(
      seedColor: p.pencil,
      brightness: brightness,
    ).copyWith(
      primary: p.pencil,
      onPrimary: p.onPencil,
      surface: p.surface,
      onSurface: p.ink,
      error: p.strike,
      outline: p.ruleStrong,
      outlineVariant: p.rule,
    ),
    textTheme: textTheme,
    extensions: [p],
    dividerColor: p.rule,
    dividerTheme: DividerThemeData(color: p.rule, space: 1, thickness: 1),
    appBarTheme: AppBarTheme(
      backgroundColor: p.paper,
      foregroundColor: p.ink,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      scrolledUnderElevation: 0,
      shape: Border(bottom: BorderSide(color: p.rule)),
      titleTextStyle: AppFonts.serif(TextStyle(fontSize: 20, color: p.ink)),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: p.surface,
      indicatorColor: p.pencilSoft,
      surfaceTintColor: Colors.transparent,
      iconTheme: WidgetStateProperty.resolveWith(
        (states) => IconThemeData(color: states.contains(WidgetState.selected) ? p.pencil : p.inkSoft),
      ),
      labelTextStyle: WidgetStateProperty.resolveWith(
        (states) => AppFonts.sans(TextStyle(
          fontSize: 12,
          color: states.contains(WidgetState.selected) ? p.pencil : p.inkSoft,
        )),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: p.surface,
      isDense: true,
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      hintStyle: TextStyle(color: p.inkFaint, fontSize: 14),
      border: border(p.ruleStrong),
      enabledBorder: border(p.ruleStrong),
      focusedBorder: border(p.pencil),
      errorBorder: border(p.strike),
      focusedErrorBorder: border(p.strike),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: p.surface,
      contentTextStyle: TextStyle(color: p.ink, fontSize: 14),
      shape: RoundedRectangleBorder(borderRadius: radius, side: BorderSide(color: p.ruleStrong)),
      showCloseIcon: true,
      closeIconColor: p.inkSoft,
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: p.surface,
      surfaceTintColor: Colors.transparent,
      shape: const RoundedRectangleBorder(borderRadius: radius),
    ),
    popupMenuTheme: PopupMenuThemeData(
      color: p.surface,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(borderRadius: radius, side: BorderSide(color: p.rule)),
    ),
    tooltipTheme: TooltipThemeData(
      decoration: BoxDecoration(
        color: p.surface,
        borderRadius: radius,
        border: Border.all(color: p.ruleStrong),
        boxShadow: const [BoxShadow(color: Color(0x22000000), blurRadius: 12, offset: Offset(0, 4))],
      ),
      textStyle: TextStyle(color: p.inkSoft, fontSize: 12, height: 1.5),
      padding: const EdgeInsets.all(10),
      margin: const EdgeInsets.symmetric(horizontal: 24),
      waitDuration: Duration.zero,
      showDuration: const Duration(seconds: 6),
    ),
    sliderTheme: SliderThemeData(
      activeTrackColor: p.pencil,
      inactiveTrackColor: p.rule,
      thumbColor: p.pencil,
      overlayColor: p.pencilSoft,
      activeTickMarkColor: p.onPencil,
      inactiveTickMarkColor: p.ruleStrong,
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(color: p.pencil, linearTrackColor: p.rule),
    tabBarTheme: TabBarThemeData(
      labelColor: p.ink,
      unselectedLabelColor: p.inkSoft,
      indicatorColor: p.pencil,
      dividerColor: p.rule,
      labelStyle: AppFonts.sans(const TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
      unselectedLabelStyle: AppFonts.sans(const TextStyle(fontSize: 14)),
    ),
  );
}
