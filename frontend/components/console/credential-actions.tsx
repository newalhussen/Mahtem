"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Ban, Copy, Download, ExternalLink, KeyRound, X } from "lucide-react";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { api, ApiError } from "@/lib/api.client";
import { useToast } from "./toast";
import { Alert } from "./ui";

const REASONS = ["Issued in error", "Superseded by a corrected credential", "Academic misconduct", "Requested by recipient", "Other"];

export function CredentialActions({
  id, name, verifyUrl, canSeal, canRevoke, sealed, hasEmail,
}: { id: string; name: string; verifyUrl: string; canSeal: boolean; canRevoke: boolean; sealed: boolean; hasEmail: boolean }) {
  const router = useRouter();
  const toast = useToast();
  const [preview, setPreview] = useState(false);
  const [revoke, setRevoke] = useState(false);
  const [reason, setReason] = useState(REASONS[0]);
  const [note, setNote] = useState("");
  const [confirm, setConfirm] = useState("");
  const [notify, setNotify] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const code = id.split("-").pop() ?? "";

  async function copy() {
    try {
      await navigator.clipboard.writeText(verifyUrl);
      toast(`Link copied: ${verifyUrl.replace(/^https?:\/\//, "")}`);
    } catch {
      toast("Couldn’t copy automatically. Select the link on the verification page instead.");
    }
  }

  async function doSeal() {
    setBusy(true);
    setError(null);
    try {
      await api(`/org/credentials/${id}/seal`, { method: "POST" });
      toast("Credential sealed. It is now verifiable.");
      router.refresh();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not seal. Try again.");
    } finally {
      setBusy(false);
    }
  }

  async function doRevoke() {
    setBusy(true);
    setError(null);
    try {
      await api(`/org/credentials/${id}/revoke`, { body: { reason, note: note.trim() || null, notifyRecipient: notify && hasEmail, confirm } });
      setRevoke(false);
      toast("Credential revoked. Verifiers now see “Revoked”.");
      router.refresh();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not revoke. Try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      {canSeal && (
        <button className="btn btn-primary" onClick={doSeal} disabled={busy}>
          <KeyRound size={15} aria-hidden="true" />{busy ? "Sealing…" : "Seal and issue"}
        </button>
      )}
      {sealed && (
        <>
          <button className="btn btn-secondary" onClick={copy}><Copy size={15} aria-hidden="true" />Copy verification link</button>
          <button className="btn btn-secondary" onClick={() => setPreview(true)}><Download size={15} aria-hidden="true" />Preview &amp; download</button>
        </>
      )}
      {canRevoke && (
        <button className="btn btn-secondary !border-seal !text-seal" onClick={() => { setRevoke(true); setError(null); setConfirm(""); }}>Revoke</button>
      )}
      {error && !revoke && <div className="w-full"><Alert>{error}</Alert></div>}

      <Dialog open={preview} onOpenChange={setPreview}>
        <DialogContent showCloseButton={false} className="!fixed !inset-0 !h-screen !w-screen !max-w-none !translate-x-0 !translate-y-0 !gap-0 !rounded-none !bg-ink/90 !p-0 !ring-0 sm:!max-w-none" style={{ top: 0, left: 0 }}>
          <DialogTitle className="sr-only">Certificate preview for {name}</DialogTitle>
          <DialogDescription className="sr-only">The sealed PDF exactly as issued.</DialogDescription>
          <div className="flex flex-wrap items-center gap-3 bg-ink px-6 py-3 text-background">
            <span className="font-semibold">{name}</span>
            <span className="mono text-xs opacity-70">{id}.pdf · A4</span>
            <div className="ml-auto flex gap-2">
              <a className="btn !border-[rgba(243,242,242,.4)] !text-background" href={verifyUrl} target="_blank" rel="noreferrer"><ExternalLink size={15} aria-hidden="true" />Open verification page</a>
              <a className="btn !bg-background !text-ink" href={`/api/org/credentials/${id}/pdf?download=true`}><Download size={15} aria-hidden="true" />Download PDF</a>
              <button className="btn btn-icon !text-background" onClick={() => setPreview(false)} aria-label="Close"><X size={18} /></button>
            </div>
          </div>
          <div className="min-h-0 flex-1 p-4 lg:p-8">
            <object data={`/api/org/credentials/${id}/pdf#toolbar=0&view=FitH`} type="application/pdf" className="size-full bg-paper shadow-lg" aria-label="Sealed certificate PDF">
              <div className="grid size-full place-items-center bg-background p-6 text-center text-sm">
                <div>Your browser can’t show PDFs inline. <a href={`/api/org/credentials/${id}/pdf?download=true`}>Download the PDF</a> instead.</div>
              </div>
            </object>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={revoke} onOpenChange={setRevoke}>
        <DialogContent showCloseButton={false} className="!w-[min(520px,calc(100%-2rem))] !max-w-none !gap-0 !rounded-none !bg-background !p-0 !ring-0 !shadow-lg sm:!max-w-none">
          <div className="flex items-start gap-3 border-b-2 border-divider px-6 pb-4 pt-5">
            <div className="grid size-9 flex-none place-items-center bg-seal text-[#f8f4f4]"><Ban size={18} strokeWidth={2.4} aria-hidden="true" /></div>
            <div>
              <DialogTitle className="m-0 text-xl font-extrabold">Revoke this credential?</DialogTitle>
              <DialogDescription className="text-[13px] text-neutral-800">{name} · <span className="mono">{id}</span></DialogDescription>
            </div>
          </div>
          <div className="flex flex-col gap-3.5 px-6 py-[18px]">
            <div className="text-sm leading-normal">Every copy of this certificate will show <b className="text-seal">REVOKED</b> when scanned, effective immediately. This cannot be undone.</div>
            <div className="field">
              <label htmlFor="rr">Reason</label>
              <select id="rr" className="input" value={reason} onChange={(e) => setReason(e.target.value)}>
                {REASONS.map((r) => <option key={r}>{r}</option>)}
              </select>
            </div>
            <div className="field">
              <label htmlFor="rn">Note shown to verifiers (optional)</label>
              <textarea id="rn" className="input !min-h-[70px]" placeholder="e.g. Replaced by a corrected credential. Contact the Registrar." value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} />
            </div>
            {hasEmail && (
              <label className="flex cursor-pointer items-center gap-2 text-[13px]">
                <input type="checkbox" className="size-4 accent-[#7c1405]" checked={notify} onChange={(e) => setNotify(e.target.checked)} />Notify the recipient by email
              </label>
            )}
            <div className="field">
              <label htmlFor="rc">Type <b className="mono text-ink">{code}</b> to confirm</label>
              <input id="rc" className="input mono" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="off" autoCapitalize="characters" />
            </div>
            {error && <Alert>{error}</Alert>}
          </div>
          <div className="flex justify-end gap-2 border-t-2 border-divider bg-surface px-6 py-3.5">
            <button className="btn btn-secondary" onClick={() => setRevoke(false)}>Cancel</button>
            <button className="btn btn-primary" disabled={busy || confirm.trim().toUpperCase() !== code} onClick={doRevoke}>{busy ? "Revoking…" : "Revoke credential"}</button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
