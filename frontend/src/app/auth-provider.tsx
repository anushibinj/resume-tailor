"use client";

import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";

import { GoogleSignInButton } from "@/components/google-sign-in-button";
import { Panel, Spinner } from "@/components/ui";
import { api, UNAUTHORIZED_EVENT } from "@/lib/api";
import { clearToken, getToken, setToken } from "@/lib/auth-token";
import type { AuthUser } from "@/lib/types";

type AuthStatus = "loading" | "authenticated" | "unauthenticated";

interface AuthContextValue {
  user: AuthUser;
  signOut: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/** Only ever rendered once a session exists -- see the `status !== "authenticated"` gate below. */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be called from inside AuthProvider, once signed in");
  }
  return context;
}

/**
 * Gates the whole app behind a Google sign-in. There is no guest mode and no per-route
 * guard: every backend endpoint except /api/auth/** requires the bearer token this sets
 * up, so gating here is equivalent to gating every page individually.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<AuthUser | null>(null);
  const [signInError, setSignInError] = useState<string | null>(null);

  const signOut = useCallback(() => {
    clearToken();
    setUser(null);
    setStatus("unauthenticated");
  }, []);

  useEffect(() => {
    window.addEventListener(UNAUTHORIZED_EVENT, signOut);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, signOut);
  }, [signOut]);

  useEffect(() => {
    if (!getToken()) {
      setStatus("unauthenticated");
      return;
    }
    api.auth
      .me()
      .then((profile) => {
        setUser(profile);
        setStatus("authenticated");
      })
      .catch(() => {
        // A 401 already cleared the token inside api.ts. Anything else (backend down or
        // restarting) leaves a still-valid token in place so the next reload can restore it.
        setStatus("unauthenticated");
      });
  }, []);

  const handleCredential = useCallback((idToken: string) => {
    setSignInError(null);
    api.auth
      .google(idToken)
      .then(({ token, user: profile }) => {
        setToken(token);
        setUser(profile);
        setStatus("authenticated");
      })
      .catch(() => setSignInError("Couldn't sign you in. Try again."));
  }, []);

  if (status === "loading") {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Spinner />
      </div>
    );
  }

  if (status === "unauthenticated" || !user) {
    return (
      <div className="flex min-h-[80vh] items-center justify-center px-6">
        <Panel className="w-full max-w-sm p-8 text-center">
          <h1 className="font-serif text-2xl text-ink">Resume Tailor</h1>
          <p className="mt-2 text-sm text-ink-soft">Sign in with Google to continue.</p>
          <div className="mt-6 flex justify-center">
            <GoogleSignInButton onCredential={handleCredential} />
          </div>
          {signInError ? <p className="mt-4 text-sm text-strike">{signInError}</p> : null}
        </Panel>
      </div>
    );
  }

  return <AuthContext.Provider value={{ user, signOut }}>{children}</AuthContext.Provider>;
}
