"use client";

import { forwardRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from "react";

import { cn } from "@/lib/utils";

type ButtonVariant = "primary" | "quiet" | "ghost" | "danger";

const BUTTON_VARIANTS: Record<ButtonVariant, string> = {
  primary: "bg-pencil text-on-pencil hover:opacity-90 border border-transparent",
  quiet: "bg-surface text-ink border border-rule-strong hover:border-pencil hover:text-pencil",
  ghost: "bg-transparent text-ink-soft border border-transparent hover:text-ink hover:bg-surface-sunk",
  danger: "bg-transparent text-strike border border-transparent hover:bg-strike-soft",
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "quiet", className, ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      className={cn(
        "inline-flex items-center justify-center gap-2 rounded-sm px-3.5 py-2 text-sm font-medium",
        "transition-colors disabled:cursor-not-allowed disabled:opacity-45",
        BUTTON_VARIANTS[variant],
        className,
      )}
      {...props}
    />
  );
});

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function Input({ className, ...props }, ref) {
    return (
      <input
        ref={ref}
        className={cn(
          "w-full rounded-sm border border-rule-strong bg-surface px-3 py-2 text-sm text-ink",
          "placeholder:text-ink-faint focus:border-pencil focus:outline-none",
          className,
        )}
        {...props}
      />
    );
  },
);

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  function Textarea({ className, ...props }, ref) {
    return (
      <textarea
        ref={ref}
        className={cn(
          "w-full rounded-sm border border-rule-strong bg-surface px-3 py-2 text-sm text-ink",
          "placeholder:text-ink-faint focus:border-pencil focus:outline-none",
          className,
        )}
        {...props}
      />
    );
  },
);

export function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: ReactNode;
  children: ReactNode;
}) {
  return (
    <label className="block space-y-1.5">
      <span className="block text-sm font-medium text-ink">{label}</span>
      {children}
      {hint ? <span className="block text-xs text-ink-soft">{hint}</span> : null}
    </label>
  );
}

export function Panel({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <section className={cn("rounded-sm border border-rule bg-surface", className)}>{children}</section>
  );
}

export function PageHeading({
  title,
  lede,
  actions,
}: {
  title: string;
  lede?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="mb-8 flex flex-wrap items-end justify-between gap-4 border-b border-rule pb-5">
      <div className="max-w-[60ch]">
        <h1 className="font-serif text-3xl leading-tight text-ink">{title}</h1>
        {lede ? <p className="mt-2 text-sm text-ink-soft">{lede}</p> : null}
      </div>
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </header>
  );
}

export function EmptyState({
  title,
  body,
  action,
}: {
  title: string;
  body: string;
  action?: ReactNode;
}) {
  return (
    <div className="rounded-sm border border-dashed border-rule-strong bg-surface px-8 py-14 text-center">
      <p className="font-serif text-xl text-ink">{title}</p>
      <p className="mx-auto mt-2 max-w-[48ch] text-sm text-ink-soft">{body}</p>
      {action ? <div className="mt-6 flex justify-center">{action}</div> : null}
    </div>
  );
}

export function Tag({
  children,
  tone = "neutral",
  className,
}: {
  children: ReactNode;
  tone?: "neutral" | "pencil" | "strike" | "gap";
  className?: string;
}) {
  const tones = {
    neutral: "border-rule-strong text-ink-soft",
    pencil: "border-pencil/40 bg-pencil-soft text-pencil",
    strike: "border-strike/40 bg-strike-soft text-strike",
    gap: "border-gap/40 bg-gap-soft text-gap",
  } as const;
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-sm border px-2 py-0.5 text-xs font-medium",
        tones[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      role="status"
      aria-label="Working"
      className={cn(
        "inline-block size-4 animate-spin rounded-full border-2 border-rule-strong border-t-pencil",
        className,
      )}
    />
  );
}

/** A pulsing placeholder block, sized by the caller to approximate the content it stands in for. */
export function Skeleton({ className }: { className?: string }) {
  return <div role="status" aria-label="Loading" className={cn("animate-pulse rounded-sm bg-surface-sunk", className)} />;
}
