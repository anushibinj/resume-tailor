import type { NextConfig } from "next";
import { PHASE_DEVELOPMENT_SERVER } from "next/constants";

export default function nextConfig(phase: string): NextConfig {
  return {
    // `next dev` and `next build` get separate output folders. Sharing `.next` means a
    // build run while the dev server is up overwrites the dev manifests, and the dev
    // server then fails with ENOENT on app-paths-manifest.json until `.next` is deleted.
    // The project's test gate runs `pnpm build` after every change, so this collision is
    // routine rather than rare. (Next.js 16 separates the two by default.)
    distDir: phase === PHASE_DEVELOPMENT_SERVER ? ".next-dev" : ".next",
  };
}
