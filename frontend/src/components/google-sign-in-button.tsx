"use client";

import Script from "next/script";
import { useCallback, useRef, useState } from "react";

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: {
            client_id: string;
            callback: (response: { credential: string }) => void;
          }) => void;
          renderButton: (parent: HTMLElement, options: Record<string, unknown>) => void;
        };
      };
    };
  }
}

/**
 * Google Identity Services' own hosted button. It has to be rendered by Google's script
 * into a real DOM node (not a component we control) -- `renderButton` writes markup into
 * the container ref directly.
 *
 * <p>Uses {@code onReady}, not {@code onLoad}: Next only fires {@code onLoad} the first
 * time this script is ever loaded on the page, but this component needs to (re-)render
 * the button every time it mounts (e.g. after a sign-out shows the sign-in screen again).
 */
export function GoogleSignInButton({ onCredential }: { onCredential: (idToken: string) => void }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [ready, setReady] = useState(false);
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

  const renderButton = useCallback(() => {
    if (!clientId || !window.google || !containerRef.current) {
      return;
    }
    window.google.accounts.id.initialize({
      client_id: clientId,
      callback: (response) => onCredential(response.credential),
    });
    window.google.accounts.id.renderButton(containerRef.current, {
      theme: "outline",
      size: "large",
      text: "signin_with",
      shape: "pill",
    });
    setReady(true);
  }, [clientId, onCredential]);

  if (!clientId) {
    return (
      <p className="text-sm text-strike">
        NEXT_PUBLIC_GOOGLE_CLIENT_ID is not set -- see frontend/.env.example.
      </p>
    );
  }

  return (
    <>
      <Script src="https://accounts.google.com/gsi/client" strategy="afterInteractive" onReady={renderButton} />
      <div ref={containerRef} />
      {!ready ? <p className="text-sm text-ink-faint">Loading Google Sign-In…</p> : null}
    </>
  );
}
