"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Qr } from "@/components/mahtem/qr";
import { api, ApiError } from "@/lib/api.client";
import { ROLE_LABEL } from "@/lib/format";
import type { Me } from "@/lib/types";
import { useToast } from "./toast";
import { Alert } from "./ui";

/** Your own account: role and two-step verification (TOTP authenticator apps). */
export function AccountPanel({ me }: { me: Me }) {
  const router = useRouter();
  const toast = useToast();
  const [setup, setSetup] = useState<{ secret: string; otpauthUri: string } | null>(null);
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [disabling, setDisabling] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function run(fn: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Something went wrong. Try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex max-w-[980px] flex-col px-5 pb-10 pt-2 lg:px-9">
      <div className="grid gap-4 border-b border-divider py-6 lg:grid-cols-[260px_minmax(0,1fr)] lg:gap-8">
        <div>
          <h2 className="m-0 mb-1 text-base">Your account</h2>
          <div className="text-[13px] text-neutral-700">How you appear in the staff action log.</div>
        </div>
        <dl className="m-0 grid grid-cols-[110px_1fr] gap-y-1.5 text-sm">
          <dt className="text-neutral-700">Name</dt><dd className="m-0 font-semibold">{me.name}</dd>
          <dt className="text-neutral-700">Email</dt><dd className="m-0 font-semibold">{me.email}</dd>
          <dt className="text-neutral-700">Role</dt><dd className="m-0 font-semibold">{ROLE_LABEL[me.role]}</dd>
        </dl>
      </div>

      <div className="grid gap-4 py-6 lg:grid-cols-[260px_minmax(0,1fr)] lg:gap-8">
        <div>
          <h2 className="m-0 mb-1 text-base">Two-step verification</h2>
          <div className="text-[13px] text-neutral-700">A code from an authenticator app is asked for each time you sign in.</div>
        </div>
        <div className="flex min-w-0 flex-col gap-3.5">
          {me.twoFactor && !disabling && (
            <>
              <div className="flex items-center gap-3 bg-surface px-3.5 py-3"><span className="tag tag-ok">On</span><span className="text-sm">Sign-in needs your password and an authenticator code.</span></div>
              <div><button className="btn btn-secondary" onClick={() => { setDisabling(true); setError(null); }}>Turn off</button></div>
            </>
          )}
          {me.twoFactor && disabling && (
            <form className="flex max-w-[360px] flex-col gap-3" onSubmit={(e) => { e.preventDefault(); void run(async () => { await api("/auth/2fa/disable", { body: { password, code } }); toast("Two-step verification is off"); setDisabling(false); setPassword(""); setCode(""); router.refresh(); }); }}>
              <div className="field"><label htmlFor="d1">Password</label><input id="d1" type="password" className="input" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" /></div>
              <div className="field"><label htmlFor="d2">Authenticator code</label><input id="d2" className="input mono" inputMode="numeric" maxLength={8} value={code} onChange={(e) => setCode(e.target.value)} autoComplete="one-time-code" /></div>
              {error && <Alert>{error}</Alert>}
              <div className="flex gap-2"><button className="btn btn-primary" disabled={busy || !password || !code}>Turn off</button><button type="button" className="btn btn-secondary" onClick={() => setDisabling(false)}>Cancel</button></div>
            </form>
          )}
          {!me.twoFactor && !setup && (
            <>
              <div className="flex items-center gap-3 bg-surface px-3.5 py-3"><span className="tag tag-neutral">Off</span><span className="text-sm">Add a second step so a stolen password isn’t enough.</span></div>
              <div><button className="btn btn-primary" disabled={busy} onClick={() => run(async () => setSetup(await api("/auth/2fa/setup", { method: "POST" })))}>Set up authenticator</button></div>
              {error && <Alert>{error}</Alert>}
            </>
          )}
          {!me.twoFactor && setup && (
            <form className="flex flex-col gap-3.5" onSubmit={(e) => { e.preventDefault(); void run(async () => { await api("/auth/2fa/enable", { body: { code } }); toast("Two-step verification is on"); setSetup(null); setCode(""); router.refresh(); }); }}>
              <ol className="m-0 flex list-decimal flex-col gap-2 pl-5 text-sm leading-normal">
                <li>Open an authenticator app (Google Authenticator, Microsoft Authenticator, 1Password…) and add an account.</li>
                <li>Scan this code, or type the key by hand.</li>
                <li>Enter the 6-digit code the app shows.</li>
              </ol>
              <div className="flex flex-wrap items-start gap-5">
                <div className="size-40 border border-divider"><Qr text={setup.otpauthUri} /></div>
                <div className="flex min-w-0 flex-col gap-1 text-[13px]"><span className="text-neutral-700">Setup key</span><span className="mono break-all font-semibold">{setup.secret}</span></div>
              </div>
              <div className="field max-w-[260px]"><label htmlFor="c1">6-digit code</label><input id="c1" className="input mono" inputMode="numeric" maxLength={8} value={code} onChange={(e) => setCode(e.target.value)} autoComplete="one-time-code" /></div>
              {error && <Alert>{error}</Alert>}
              <div className="flex gap-2"><button className="btn btn-primary" disabled={busy || code.length < 6}>Turn on</button><button type="button" className="btn btn-secondary" onClick={() => { setSetup(null); setError(null); }}>Cancel</button></div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
