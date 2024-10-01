"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Upload } from "lucide-react";
import { api, ApiError } from "@/lib/api.client";
import { dateShort } from "@/lib/format";
import { CREDENTIAL_TYPES, type BatchPreview, type BatchRow, type CredentialType, type Me } from "@/lib/types";
import { useToast } from "./toast";
import { Alert } from "./ui";

const COLUMNS: [string, string][] = [
  ["full_name", "required"],
  ["title", "required"],
  ["date_conferred", "YYYY-MM-DD"],
  ["full_name_am", "optional"],
  ["email", "optional"],
  ["reference", "optional"],
  ["type", "optional · DEGREE, TRAINING…"],
  ["details", "optional"],
];

const STATUS: Record<string, { label: string; cls: string }> = {
  AWAITING_APPROVAL: { label: "Awaiting approval", cls: "tag-neutral" },
  SEALING: { label: "Sealing…", cls: "tag-neutral" },
  SEALED: { label: "Sealed", cls: "tag-ok" },
  FAILED: { label: "Finished with errors", cls: "tag-revoked" },
};

export function BulkIssue({ me }: { me: Me }) {
  const toast = useToast();
  const input = useRef<HTMLInputElement>(null);
  const [batches, setBatches] = useState<BatchRow[]>([]);
  const [type, setType] = useState<CredentialType>("DEGREE");
  const [approval, setApproval] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [problems, setProblems] = useState<BatchPreview | null>(null);
  const [drag, setDrag] = useState(false);

  const load = useCallback(async () => {
    try {
      setBatches(await api<BatchRow[]>("/org/batches"));
    } catch {
      /* the list is secondary; uploading still works */
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  // While a batch is being sealed in the background, keep its progress fresh.
  const sealing = batches.some((b) => b.status === "SEALING");
  useEffect(() => {
    if (!sealing) return;
    const t = setInterval(load, 2000);
    return () => clearInterval(t);
  }, [sealing, load]);

  async function upload(file: File | undefined) {
    if (!file) return;
    setBusy(true);
    setError(null);
    setProblems(null);
    const form = new FormData();
    form.set("file", file);
    form.set("requireApproval", String(approval));
    form.set("type", type);
    try {
      const res = await api<BatchPreview>("/org/batches", { form });
      if (res.errors.length) {
        setProblems(res);
      } else {
        toast(approval ? `${res.batch?.rows} credentials are waiting for a second approver.` : `Sealing ${res.batch?.rows} credentials…`);
        await load();
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Upload failed. Try again.");
    } finally {
      setBusy(false);
      if (input.current) input.current.value = "";
    }
  }

  async function approve(id: string) {
    try {
      await api(`/org/batches/${id}/approve`, { method: "POST" });
      toast("Approved. Sealing has started.");
      await load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not approve.");
    }
  }

  return (
    <div className="grid gap-9 px-5 pb-10 pt-7 lg:grid-cols-[minmax(0,1fr)_minmax(280px,.6fr)] lg:px-9">
      <div className="flex min-w-0 flex-col gap-4">
        <div
          onDragOver={(e) => { e.preventDefault(); setDrag(true); }}
          onDragLeave={() => setDrag(false)}
          onDrop={(e) => { e.preventDefault(); setDrag(false); void upload(e.dataTransfer.files?.[0]); }}
          className={`flex flex-col items-start gap-2.5 border-2 border-dashed p-10 ${drag ? "border-seal bg-accent-100" : "border-divider bg-surface"}`}
        >
          <Upload size={28} strokeWidth={1.8} aria-hidden="true" />
          <div className="text-[18px] font-extrabold">Drop a CSV of recipients</div>
          <div className="text-sm text-neutral-800">Up to 1,000 rows. Every row is checked before anything is sealed; if one row is wrong, nothing is created.</div>
          <div className="flex flex-wrap items-center gap-x-5 gap-y-2 pt-1">
            <label className="flex items-center gap-2 text-[13px]">
              Default type
              <select className="input !w-auto" value={type} onChange={(e) => setType(e.target.value as CredentialType)}>
                {CREDENTIAL_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
              </select>
            </label>
            <label className="flex cursor-pointer items-center gap-2 text-[13px]">
              <input type="checkbox" className="size-4 accent-[#7c1405]" checked={approval} onChange={(e) => setApproval(e.target.checked)} />
              Require a second approver before sealing
            </label>
          </div>
          <div className="flex gap-2">
            <input ref={input} type="file" accept=".csv,text/csv" className="sr-only" aria-label="Choose a CSV file" tabIndex={-1} onChange={(e) => upload(e.target.files?.[0])} />
            <button type="button" className="btn btn-primary" disabled={busy} onClick={() => input.current?.click()}>{busy ? "Checking file…" : "Choose file"}</button>
            <a className="btn btn-ghost" href="/api/org/batches/template.csv">Download CSV template</a>
          </div>
        </div>

        {error && <Alert>{error}</Alert>}
        {problems && (
          <div className="flex flex-col gap-2" role="alert">
            <Alert>
              <b>Nothing was created.</b> {problems.errors.length} problem{problems.errors.length === 1 ? "" : "s"} found{problems.errors.length >= 100 ? " (showing the first 100)" : ""}. Fix them in your file and upload it again.
            </Alert>
            <div className="max-h-[320px] overflow-auto border border-divider">
              <table className="table">
                <thead><tr><th>Row</th><th>Column</th><th>Problem</th></tr></thead>
                <tbody>
                  {problems.errors.map((p, i) => (
                    <tr key={i}><td className="mono">{p.row}</td><td className="mono">{p.field}</td><td>{p.message}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        <div className="overflow-x-auto">
          <table className="table min-w-[520px]">
            <thead><tr><th>Batch</th><th>Rows</th><th>Status</th><th>Uploaded</th><th /></tr></thead>
            <tbody>
              {batches.map((b) => {
                const s = STATUS[b.status] ?? STATUS.SEALED;
                return (
                  <tr key={b.id}>
                    <td className="font-semibold">{b.filename}<div className="text-xs font-normal text-neutral-700">by {b.uploadedBy}</div></td>
                    <td className="mono">{b.status === "SEALING" ? `${b.sealed} / ${b.rows}` : b.rows}</td>
                    <td><span className={`tag ${s.cls}`}>{s.label}</span></td>
                    <td className="whitespace-nowrap">{dateShort(b.uploadedAt)}</td>
                    <td className="text-right">{b.status === "AWAITING_APPROVAL" && me.organization.canApprove && <button className="btn btn-secondary" onClick={() => approve(b.id)}>Approve</button>}</td>
                  </tr>
                );
              })}
              {batches.length === 0 && <tr><td colSpan={5} className="text-neutral-700">No batches yet.</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
      <div className="flex flex-col gap-2 text-[13px]">
        <div className="text-sm font-semibold">Columns</div>
        {COLUMNS.map(([k, v]) => (
          <div key={k} className="flex justify-between gap-3 border-b border-divider py-2">
            <span className="mono">{k}</span>
            <span className="text-right text-neutral-700">{v}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
