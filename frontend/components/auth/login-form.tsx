"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api.client";

type Step = { kind: "credentials" } | { kind: "code"; challenge: string };

export function LoginForm() {
  const router = useRouter();
  const [step, setStep] = useState<Step>({ kind: "credentials" });
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [digits, setDigits] = useState<string[]>(Array(6).fill(""));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const boxes = useRef<(HTMLInputElement | null)[]>([]);

  useEffect(() => {
    if (step.kind === "code") boxes.current[0]?.focus();
  }, [step.kind]);

  async function submitCredentials(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await api<{ status: string; challenge?: string }>("/auth/login", { body: { email, password } });
      if (res.status === "TWO_FACTOR_REQUIRED" && res.challenge) {
        setStep({ kind: "code", challenge: res.challenge });
      } else {
        router.replace("/console");
        router.refresh();
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Try again.");
    } finally {
      setBusy(false);
    }
  }

  async function submitCode(e?: React.FormEvent, codeOverride?: string) {
    e?.preventDefault();
    if (step.kind !== "code") return;
    const code = codeOverride ?? digits.join("");
    if (code.length !== 6) return;
    setBusy(true);
    setError(null);
    try {
      await api("/auth/2fa", { body: { challenge: step.challenge, code } });
      router.replace("/console");
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Try again.");
      setDigits(Array(6).fill(""));
      boxes.current[0]?.focus();
    } finally {
      setBusy(false);
    }
  }

  function setDigit(i: number, v: string) {
    const clean = v.replace(/\D/g, "");
    if (clean.length > 1) {
      // paste of the whole code
      const next = clean.slice(0, 6).split("");
      const filled = [...next, ...Array(6 - next.length).fill("")];
      setDigits(filled);
      boxes.current[Math.min(next.length, 5)]?.focus();
      if (next.length === 6) void submitCode(undefined, next.join(""));
      return;
    }
    const next = [...digits];
    next[i] = clean;
    setDigits(next);
    if (clean && i < 5) boxes.current[i + 1]?.focus();
    if (clean && i === 5 && next.every(Boolean)) void submitCode(undefined, next.join(""));
  }

  if (step.kind === "code") {
    return (
      <form onSubmit={submitCode} className="flex flex-col gap-5" noValidate>
        <button type="button" className="btn btn-ghost self-start !px-0" onClick={() => { setStep({ kind: "credentials" }); setError(null); setDigits(Array(6).fill("")); }}>
          ← Back
        </button>
        <div>
          <h1 className="m-0 mb-1.5 text-[32px]">Two-step verification</h1>
          <div className="text-sm text-neutral-800">
            Enter the 6-digit code from your authenticator app for <b>{email}</b>.
          </div>
        </div>
        <fieldset className="m-0 grid grid-cols-6 gap-2 border-0 p-0" aria-label="6-digit code">
          {digits.map((d, i) => (
            <input
              key={i}
              ref={(el) => { boxes.current[i] = el; }}
              value={d}
              inputMode="numeric"
              autoComplete={i === 0 ? "one-time-code" : "off"}
              aria-label={`Digit ${i + 1}`}
              maxLength={6}
              onChange={(e) => setDigit(i, e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Backspace" && !digits[i] && i > 0) boxes.current[i - 1]?.focus();
              }}
              className="mono h-14 w-full border border-divider bg-surface text-center text-[22px] font-semibold focus-visible:border-seal"
            />
          ))}
        </fieldset>
        {error && <div role="alert" className="border-2 border-seal bg-accent-100 p-3 text-sm text-accent-900">{error}</div>}
        <button className="btn btn-primary !min-h-11 !px-3.5" disabled={busy || digits.some((d) => !d)}>
          {busy ? "Checking…" : "Verify and sign in"}
        </button>
      </form>
    );
  }

  return (
    <form onSubmit={submitCredentials} className="flex flex-col gap-5" noValidate>
      <div>
        <h1 className="m-0 mb-1.5 text-[32px]">Sign in</h1>
        <div className="text-sm text-neutral-800">For registrars and authorized staff of issuing institutions.</div>
      </div>
      <div className="field">
        <label htmlFor="em">Work email</label>
        <input id="em" type="email" autoComplete="username" className="input !min-h-11" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
      </div>
      <div className="field">
        <label htmlFor="pw">Password</label>
        <input id="pw" type="password" autoComplete="current-password" className="input !min-h-11" value={password} onChange={(e) => setPassword(e.target.value)} required />
      </div>
      {error && <div role="alert" className="border-2 border-seal bg-accent-100 p-3 text-sm text-accent-900">{error}</div>}
      <button className="btn btn-primary !min-h-11 !px-3.5" disabled={busy || !email || !password}>
        {busy ? "Signing in…" : "Continue"}
      </button>
      <div className="text-xs leading-normal text-neutral-700">
        New institution? <a href="mailto:hello@mahtem.et?subject=Issuer%20onboarding">Request issuer onboarding</a>. Employers never need an account to verify a credential.
      </div>
    </form>
  );
}
