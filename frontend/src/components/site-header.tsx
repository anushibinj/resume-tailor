"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTheme } from "next-themes";
import { LogOut, Moon, Sun } from "lucide-react";
import { useEffect, useState } from "react";

import { useAuth } from "@/app/auth-provider";
import { cn } from "@/lib/utils";

const LINKS = [
  { href: "/tailor", label: "Tailor" },
  { href: "/resumes", label: "Resumes" },
  { href: "/runs", label: "History" },
  { href: "/settings", label: "Settings" },
];

export function SiteHeader() {
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-20 border-b border-rule bg-paper/85 backdrop-blur">
      <div className="mx-auto flex w-full max-w-[1400px] items-center gap-8 px-6 py-3.5">
        <Link href="/" className="font-serif text-lg leading-none text-ink">
          Resume Tailor
        </Link>
        <nav className="flex items-center gap-1 text-sm">
          {LINKS.map((link) => {
            const active = pathname === link.href || pathname.startsWith(`${link.href}/`);
            return (
              <Link
                key={link.href}
                href={link.href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "rounded-sm px-3 py-1.5 transition-colors",
                  active ? "bg-pencil-soft text-pencil" : "text-ink-soft hover:text-ink",
                )}
              >
                {link.label}
              </Link>
            );
          })}
        </nav>
        <div className="ml-auto flex items-center gap-3">
          <ThemeToggle />
          <UserMenu />
        </div>
      </div>
    </header>
  );
}

function UserMenu() {
  const { user, signOut } = useAuth();

  return (
    <div className="flex items-center gap-2 border-l border-rule pl-3">
      {user.pictureUrl ? (
        // eslint-disable-next-line @next/next/no-img-element -- a Google-hosted avatar, not worth next/image's config for one small icon.
        <img src={user.pictureUrl} alt="" className="size-6 rounded-full" referrerPolicy="no-referrer" />
      ) : null}
      <span className="hidden text-sm text-ink-soft sm:inline" title={user.email}>
        {user.displayName}
      </span>
      <button
        type="button"
        onClick={signOut}
        className="rounded-sm p-2 text-ink-soft transition-colors hover:bg-surface-sunk hover:text-ink"
        aria-label="Sign out"
        title="Sign out"
      >
        <LogOut className="size-4" />
      </button>
    </div>
  );
}

function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);

  // The server can't know the viewer's theme, so render the icon only after hydration.
  useEffect(() => setMounted(true), []);

  // Before mount the server and client disagree about the theme, so the label and icon
  // must both stay neutral or React reports a hydration mismatch.
  const isDark = mounted && resolvedTheme === "dark";
  return (
    <button
      type="button"
      onClick={() => setTheme(isDark ? "light" : "dark")}
      className="rounded-sm p-2 text-ink-soft transition-colors hover:bg-surface-sunk hover:text-ink"
      aria-label={mounted ? (isDark ? "Switch to light theme" : "Switch to dark theme") : "Switch theme"}
    >
      {isDark ? <Sun className="size-4" /> : <Moon className="size-4" />}
    </button>
  );
}
