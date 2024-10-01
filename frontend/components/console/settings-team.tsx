"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Copy } from "lucide-react";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { api, ApiError } from "@/lib/api.client";
import { ago, ROLE_LABEL } from "@/lib/format";
import type { KeyRow, Member, Role } from "@/lib/types";
import { useToast } from "./toast";
import { Alert } from "./ui";

const ROLES: Role[] = ["ADMIN", "APPROVER", "ISSUER", "VIEWER"];

export function TeamPanel({ members, meId, isAdmin }: { members: Member[]; meId: string; isAdmin: boolean }) {
  const router = useRouter();
  const toast = useToast();
  const [invite, setInvite] = useState(false);
  const [form, setForm] = useState({ fullName: "", email: "", role: "ISSUER" as Role });
  const [link, setLink] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function sendInvite(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const out = await api<{ link: string }>("/org/team/invite", { body: form });
      setLink(out.link);
      router.refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not send the invitation.");
    } finally {
      setBusy(false);
    }
  }

  async function update(id: string, body: { role?: Role; disabled?: boolean }) {
    try {
      await api(`/org/team/${id}`, { method: "PATCH", body });
      toast("Team updated");
      router.refresh();
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Could not update that member.");
    }
  }

  return (
    <div className="flex max-w-[1080px] flex-col gap-4 px-5 pb-10 pt-6 lg:px-9">
      <div className="flex items-end gap-3">
        <div className="flex-1">
          <h2 className="m-0 mb-1 text-[18px]">Team</h2>
          <div className="text-[13px] text-neutral-700">Only Admins and Issuers can seal or revoke credentials. Approvers can seal what someone else created.</div>
        </div>
        {isAdmin && <button className="btn btn-primary" onClick={() => { setInvite(true); setLink(null); setError(null); setForm({ fullName: "", email: "", role: "ISSUER" }); }}>Invite member</button>}
      </div>
      <div className="overflow-x-auto">
        <table className="table min-w-[640px]">
          <thead><tr><th>Name</th><th>Role</th><th>Two-step</th><th>Last active</th><th /></tr></thead>
          <tbody>
            {members.map((m) => (
              <tr key={m.id} className={m.status === "DISABLED" ? "opacity-60" : ""}>
                <td><div className="font-semibold">{m.name}{m.id === meId && <span className="ml-2 text-xs font-normal text-neutral-700">you</span>}</div><div className="text-xs text-neutral-700">{m.email}</div></td>
                <td>
                  {isAdmin && m.id !== meId ? (
                    <select aria-label={`Role for ${m.name}`} className="input !w-auto" value={m.role} onChange={(e) => update(m.id, { role: e.target.value as Role })}>
                      {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
                    </select>
                  ) : ROLE_LABEL[m.role]}
                </td>
                <td>{m.twoFactor ? "Authenticator" : "Not set up"}</td>
                <td>{m.status === "INVITED" ? "Invited" : m.status === "DISABLED" ? "Disabled" : ago(m.lastActive)}</td>
                <td className="text-right">
                  {isAdmin && m.id !== meId && (
                    <button className="btn btn-ghost" onClick={() => update(m.id, { disabled: m.status !== "DISABLED" })}>{m.status === "DISABLED" ? "Enable" : "Disable"}</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Dialog open={invite} onOpenChange={setInvite}>
        <DialogContent showCloseButton={false} className="!w-[min(480px,calc(100%-2rem))] !max-w-none !gap-0 !rounded-none !bg-background !p-0 !ring-0 !shadow-lg sm:!max-w-none">
          <div className="border-b-2 border-divider px-6 pb-4 pt-5">
            <DialogTitle className="m-0 text-xl font-extrabold">Invite a team member</DialogTitle>
            <DialogDescription className="text-[13px] text-neutral-800">They’ll get a link to choose a password. Links expire after 7 days.</DialogDescription>
          </div>
          {link ? (
            <div className="flex flex-col gap-3 px-6 py-5">
              <Alert tone="ok">Invitation created for <b>{form.email}</b>.</Alert>
              <div className="text-[13px] text-neutral-800">Email delivery isn’t configured on this server, so share this one-time link with them directly:</div>
              <div className="mono break-all border border-divider bg-surface p-2.5 text-xs">{link}</div>
              <div className="flex justify-end gap-2">
                <button className="btn btn-secondary" onClick={() => navigator.clipboard?.writeText(link).then(() => toast("Link copied"))}><Copy size={14} aria-hidden="true" />Copy link</button>
                <button className="btn btn-primary" onClick={() => setInvite(false)}>Done</button>
              </div>
            </div>
          ) : (
            <form onSubmit={sendInvite} className="flex flex-col gap-3.5 px-6 py-5">
              <div className="field"><label htmlFor="i1">Full name</label><input id="i1" className="input" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required /></div>
              <div className="field"><label htmlFor="i2">Work email</label><input id="i2" type="email" className="input" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required /></div>
              <div className="field">
                <label htmlFor="i3">Role</label>
                <select id="i3" className="input" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
                  {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
                </select>
              </div>
              {error && <Alert>{error}</Alert>}
              <div className="flex justify-end gap-2 pt-1">
                <button type="button" className="btn btn-secondary" onClick={() => setInvite(false)}>Cancel</button>
                <button className="btn btn-primary" disabled={busy || !form.fullName || !form.email}>{busy ? "Creating…" : "Create invitation"}</button>
              </div>
            </form>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

export function KeysPanel({ keys, isAdmin }: { keys: KeyRow[]; isAdmin: boolean }) {
  const router = useRouter();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function rotate() {
    setBusy(true);
    setError(null);
    try {
      await api("/org/keys/rotate", { method: "POST" });
      setOpen(false);
      toast("New signing key activated. The previous key still verifies existing credentials.");
      router.refresh();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not rotate the key.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex max-w-[1080px] flex-col gap-4 px-5 pb-10 pt-6 lg:px-9">
      <div className="flex items-end gap-3">
        <div className="flex-1">
          <h2 className="m-0 mb-1 text-[18px]">Signing keys</h2>
          <div className="text-[13px] text-neutral-700">Private keys are stored encrypted and are never displayed or exported. Retired keys keep verifying the credentials they signed.</div>
        </div>
        {isAdmin && <button className="btn btn-secondary" onClick={() => { setOpen(true); setError(null); }}>Rotate key</button>}
      </div>
      <div className="overflow-x-auto">
        <table className="table min-w-[640px]">
          <thead><tr><th>Key</th><th>Fingerprint</th><th>Algorithm</th><th>Valid</th><th>Status</th></tr></thead>
          <tbody>
            {keys.map((k) => (
              <tr key={k.id}>
                <td className="font-semibold">{k.label}</td>
                <td className="mono text-[13px]">{k.fingerprint}</td>
                <td>{k.algorithm}</td>
                <td className="whitespace-nowrap">{k.validFrom}{k.validTo ? ` – ${k.validTo}` : ""}</td>
                <td><span className={`tag ${k.status === "ACTIVE" ? "tag-ok" : "tag-neutral"}`}>{k.status === "ACTIVE" ? "Active" : "Retired · still verifies"}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent showCloseButton={false} className="!w-[min(480px,calc(100%-2rem))] !max-w-none !gap-0 !rounded-none !bg-background !p-0 !ring-0 !shadow-lg sm:!max-w-none">
          <div className="border-b-2 border-divider px-6 pb-4 pt-5">
            <DialogTitle className="m-0 text-xl font-extrabold">Rotate the signing key?</DialogTitle>
            <DialogDescription className="text-[13px] text-neutral-800">A new Ed25519 key signs everything issued from now on.</DialogDescription>
          </div>
          <div className="flex flex-col gap-3 px-6 py-5 text-sm leading-normal">
            <div>Credentials you have already issued stay valid: the current key is retired, not revoked, and continues to verify them.</div>
            {error && <Alert>{error}</Alert>}
          </div>
          <div className="flex justify-end gap-2 border-t-2 border-divider bg-surface px-6 py-3.5">
            <button className="btn btn-secondary" onClick={() => setOpen(false)}>Cancel</button>
            <button className="btn btn-primary" onClick={rotate} disabled={busy}>{busy ? "Rotating…" : "Rotate key"}</button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
