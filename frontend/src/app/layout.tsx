import type { Metadata } from "next";
import { IBM_Plex_Mono, Instrument_Sans, Newsreader } from "next/font/google";
import { Toaster } from "sonner";

import { SiteHeader } from "@/components/site-header";
import { Providers } from "./providers";
import "./globals.css";

const instrumentSans = Instrument_Sans({
  variable: "--font-instrument-sans",
  subsets: ["latin"],
});

// Resume content is rendered in a serif so a document reads as a document.
const newsreader = Newsreader({
  variable: "--font-newsreader",
  subsets: ["latin"],
});

// Reserved for raw LaTeX / Markdown source, where character alignment is functional.
const plexMono = IBM_Plex_Mono({
  variable: "--font-plex-mono",
  subsets: ["latin"],
  weight: ["400", "500"],
});

export const metadata: Metadata = {
  title: "Resume Tailor",
  description: "Tailor your resume to a job description, and review every edit before you send it.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      suppressHydrationWarning
      className={`${instrumentSans.variable} ${newsreader.variable} ${plexMono.variable} h-full`}
    >
      {/*
        Browser extensions add attributes to <body> before React hydrates -- ColorZilla adds
        cz-shortcut-listen="true", Grammarly adds data-gr-ext-installed -- which React reports as a
        hydration mismatch the app didn't cause. suppressHydrationWarning only covers this
        element's own attributes, one level deep: a real mismatch in any component inside
        <body> is still reported.
      */}
      <body className="flex min-h-full flex-col" suppressHydrationWarning>
        <Providers>
          <SiteHeader />
          <main className="mx-auto w-full max-w-[1400px] flex-1 px-6 py-10">{children}</main>
          <Toaster position="bottom-right" closeButton richColors />
        </Providers>
      </body>
    </html>
  );
}
