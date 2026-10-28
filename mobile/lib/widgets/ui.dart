import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

/// The small always-rendered primitives, mirroring `frontend/src/components/ui.tsx`.

const _radius = BorderRadius.all(Radius.circular(2));

enum ButtonVariant { primary, quiet, ghost, danger }

class AppButton extends StatelessWidget {
  const AppButton({
    super.key,
    required this.onPressed,
    this.label,
    this.icon,
    this.variant = ButtonVariant.quiet,
    this.busy = false,
    this.compact = false,
    this.expand = false,
    this.semanticLabel,
  });

  final VoidCallback? onPressed;
  final String? label;
  final IconData? icon;
  final ButtonVariant variant;

  /// Swaps the icon for a spinner. The button stays the same size.
  final bool busy;
  final bool compact;
  final bool expand;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final enabled = onPressed != null;
    final (bg, fg, border) = switch (variant) {
      ButtonVariant.primary => (p.pencil, p.onPencil, Colors.transparent),
      ButtonVariant.quiet => (p.surface, p.ink, p.ruleStrong),
      ButtonVariant.ghost => (Colors.transparent, p.inkSoft, Colors.transparent),
      ButtonVariant.danger => (Colors.transparent, p.strike, Colors.transparent),
    };
    final iconSize = compact ? 14.0 : 16.0;
    final children = <Widget>[
      if (busy)
        Spinner(size: iconSize - 2, color: variant == ButtonVariant.primary ? p.onPencil : null)
      else if (icon != null)
        Icon(icon, size: iconSize, color: fg),
      if (label != null) Flexible(child: Text(label!, overflow: TextOverflow.ellipsis)),
    ];

    final button = TextButton(
      onPressed: onPressed,
      style: TextButton.styleFrom(
        backgroundColor: bg,
        foregroundColor: fg,
        disabledForegroundColor: fg,
        disabledBackgroundColor: bg,
        overlayColor: variant == ButtonVariant.danger ? p.strike : p.pencil,
        minimumSize: Size(0, compact ? 32 : 40),
        padding: EdgeInsets.symmetric(horizontal: label == null ? 8 : (compact ? 10 : 14), vertical: compact ? 4 : 8),
        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
        textStyle: AppFonts.sans(TextStyle(fontSize: compact ? 12 : 14, fontWeight: FontWeight.w500)),
        shape: RoundedRectangleBorder(
          borderRadius: _radius,
          side: BorderSide(color: border),
        ),
      ),
      child: Row(
        mainAxisSize: expand ? MainAxisSize.max : MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        spacing: 8,
        children: children,
      ),
    );

    return Semantics(
      label: semanticLabel,
      button: true,
      child: Opacity(opacity: enabled ? 1 : 0.45, child: button),
    );
  }
}

class Field extends StatelessWidget {
  const Field({super.key, required this.label, required this.child, this.hint});

  final String label;
  final String? hint;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: 6,
      children: [
        Text(
          label,
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500, color: p.ink),
        ),
        child,
        if (hint != null) Text(hint!, style: TextStyle(fontSize: 12, color: p.inkSoft)),
      ],
    );
  }
}

enum PanelTone { plain, gap, strike }

class Panel extends StatelessWidget {
  const Panel({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(20),
    this.tone = PanelTone.plain,
    this.onTap,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final PanelTone tone;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final (bg, border) = switch (tone) {
      PanelTone.plain => (p.surface, p.rule),
      PanelTone.gap => (p.gapSoft, p.gap.withValues(alpha: 0.4)),
      PanelTone.strike => (p.strikeSoft, p.strike.withValues(alpha: 0.4)),
    };
    return Material(
      color: bg,
      shape: RoundedRectangleBorder(
        borderRadius: _radius,
        side: BorderSide(color: border),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        splashColor: p.pencilSoft,
        highlightColor: p.pencilSoft.withValues(alpha: 0.5),
        child: Padding(padding: padding, child: child),
      ),
    );
  }
}

class PageHeading extends StatelessWidget {
  const PageHeading({super.key, required this.title, this.lede, this.actions = const []});

  final String title;
  final String? lede;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    return Container(
      margin: const EdgeInsets.only(bottom: 24),
      padding: const EdgeInsets.only(bottom: 20),
      decoration: BoxDecoration(
        border: Border(bottom: BorderSide(color: p.rule)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: AppFonts.serif(TextStyle(fontSize: 28, height: 1.2, color: p.ink))),
          if (lede != null) ...[
            const SizedBox(height: 8),
            Text(lede!, style: TextStyle(fontSize: 14, height: 1.5, color: p.inkSoft)),
          ],
          if (actions.isNotEmpty) ...[const SizedBox(height: 16), Wrap(spacing: 8, runSpacing: 8, children: actions)],
        ],
      ),
    );
  }
}

class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.title, required this.body, this.action});

  final String title;
  final String body;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    return CustomPaint(
      painter: _DashedBorderPainter(p.ruleStrong),
      child: Container(
        color: p.surface,
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 48),
        child: Column(
          children: [
            Text(
              title,
              textAlign: TextAlign.center,
              style: AppFonts.serif(TextStyle(fontSize: 20, color: p.ink)),
            ),
            const SizedBox(height: 8),
            Text(
              body,
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 14, height: 1.5, color: p.inkSoft),
            ),
            if (action != null) ...[const SizedBox(height: 24), action!],
          ],
        ),
      ),
    );
  }
}

/// A dashed outline -- the web's `border-dashed`, used for empty states and gap rows.
class DashedBorder extends StatelessWidget {
  const DashedBorder({super.key, required this.color, required this.child});

  final Color color;
  final Widget child;

  @override
  Widget build(BuildContext context) => CustomPaint(foregroundPainter: _DashedBorderPainter(color), child: child);
}

class _DashedBorderPainter extends CustomPainter {
  _DashedBorderPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;
    final path = Path()..addRect(Offset.zero & size);
    for (final metric in path.computeMetrics()) {
      for (var d = 0.0; d < metric.length; d += 7) {
        canvas.drawPath(metric.extractPath(d, d + 4), paint);
      }
    }
  }

  @override
  bool shouldRepaint(_DashedBorderPainter old) => old.color != color;
}

enum TagTone { neutral, pencil, strike, gap }

class Tag extends StatelessWidget {
  const Tag(this.text, {super.key, this.tone = TagTone.neutral});

  final String text;
  final TagTone tone;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final (bg, border, fg) = switch (tone) {
      TagTone.neutral => (Colors.transparent, p.ruleStrong, p.inkSoft),
      TagTone.pencil => (p.pencilSoft, p.pencil.withValues(alpha: 0.4), p.pencil),
      TagTone.strike => (p.strikeSoft, p.strike.withValues(alpha: 0.4), p.strike),
      TagTone.gap => (p.gapSoft, p.gap.withValues(alpha: 0.4), p.gap),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: _radius,
        border: Border.all(color: border),
      ),
      child: Text(
        text,
        style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: fg),
      ),
    );
  }
}

class Spinner extends StatelessWidget {
  const Spinner({super.key, this.size = 16, this.color});

  final double size;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    return Semantics(
      label: 'Working',
      child: SizedBox.square(
        dimension: size,
        child: CircularProgressIndicator(
          strokeWidth: 2,
          color: color ?? p.pencil,
          backgroundColor: color == null ? p.ruleStrong : Colors.transparent,
        ),
      ),
    );
  }
}

/// A pulsing placeholder block, sized by the caller to approximate the content it stands in for.
class Skeleton extends StatefulWidget {
  const Skeleton({super.key, this.width, this.height = 16});

  final double? width;
  final double height;

  @override
  State<Skeleton> createState() => _SkeletonState();
}

class _SkeletonState extends State<Skeleton> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1000),
  )..repeat(reverse: true);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Loading',
      child: FadeTransition(
        opacity: Tween(begin: 1.0, end: 0.5).animate(_controller),
        child: Container(
          width: widget.width,
          height: widget.height,
          decoration: BoxDecoration(color: context.palette.surfaceSunk, borderRadius: _radius),
        ),
      ),
    );
  }
}

/// Inline text link in the accent colour, underlined -- "go to Settings".
class InlineLink extends StatelessWidget {
  const InlineLink(this.text, {super.key, required this.onTap, this.fontSize = 14});

  final String text;
  final VoidCallback onTap;
  final double fontSize;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    return InkWell(
      onTap: onTap,
      child: Text(
        text,
        style: TextStyle(
          fontSize: fontSize,
          color: p.pencil,
          decoration: TextDecoration.underline,
          decorationColor: p.pencil,
        ),
      ),
    );
  }
}

/// The small uppercase label used above "You asked" / "Answer".
class Eyebrow extends StatelessWidget {
  const Eyebrow(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) => Text(
    text.toUpperCase(),
    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, letterSpacing: 0.6, color: context.palette.inkFaint),
  );
}

/// A centred spinner for a whole-screen loading state.
class LoadingBlock extends StatelessWidget {
  const LoadingBlock({super.key, this.label});

  final String? label;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 16),
    child: Row(
      spacing: 12,
      children: [
        const Spinner(),
        if (label != null) Text(label!, style: TextStyle(color: context.palette.inkSoft)),
      ],
    ),
  );
}
