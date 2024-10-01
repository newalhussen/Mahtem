"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, ApiError } from "@/lib/api.client";

export function AcceptInviteForm({ token }: { token: string }) {
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [again, setAgain] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!token) {
    return (
      <div className="flex flex-col gap-2">
        <h1 className="m-0 text-[32px]">Invitation link incomplete</h1>
        <p className="m-0 text-sm text-neutral-800">Open the full link from your invitation email, or ask your administrator to send a new one.</p>
      </div>
    );
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (password !== again) {
      setError("The two passwords don’t match.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api("/auth/accept-invite", { body: { token, password } });
      router.replace("/console");
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-5" noValidate>
      <div>
        <h1 className="m-0 mb-1.5 text-[32px]">Join your team</h1>
        <div className="text-sm text-neutral-800">Choose a password to finish setting up your account. Use at least 10 characters.</div>
      </div>
      <div className="field">
        <label htmlFor="np">New password</label>
        <input id="np" type="password" autoComplete="new-password" className="input !min-h-11" value={password} onChange={(e) => setPassword(e.target.value)} required />
      </div>
      <div className="field">
        <label htmlFor="np2">Repeat password</label>
        <input id="np2" type="password" autoComplete="new-password" className="input !min-h-11" value={again} onChange={(e) => setAgain(e.target.value)} required />
      </div>
      {error && <div role="alert" className="border-2 border-seal bg-accent-100 p-3 text-sm text-accent-900">{error}</div>}
      <button className="btn btn-primary !min-h-11 !px-3.5" disabled={busy || !password || !again}>{busy ? "Saving…" : "Create account and sign in"}</button>
    </form>
  );
}
