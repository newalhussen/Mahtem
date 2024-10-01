"use client";

import { useRef, useState } from "react";
import { Check as CheckIcon, Copy } from "lucide-react";

export function CopyIdButton({ id, size = 44 }: { id: string; size?: number }) {
  const [done, setDone] = useState(false);
  return (
    <button
      type="button"
      className="btn btn-secondary btn-icon"
      aria-label={done ? "Credential ID copied" : "Copy credential ID"}
      style={{ marginLeft: "auto", width: size, height: size }}
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(id);
          setDone(true);
          setTimeout(() => setDone(false), 1800);
        } catch {
          /* clipboard unavailable (insecure context): the ID is visible and selectable */
        }
      }}
    >
      {done ? <CheckIcon size={16} /> : <Copy size={16} />}
    </button>
  );
}

async function sha256Hex(file: File): Promise<string> {
  const buf = await file.arrayBuffer();
  const digest = await crypto.subtle.digest("SHA-256", buf);
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

type Outcome = { kind: "match" | "differ"; name: string } | { kind: "error"; message: string } | null;

/**
 * "Compare with the PDF I received": hashes the chosen file in the browser and compares it with the SHA-256 recorded
 * at issuance. The file never leaves the device.
 */
export function CompareWithPdf({ expected, variant = "mobile" }: { expected: string; variant?: "mobile" | "desktop" }) {
  const input = useRef<HTMLInputElement>(null);
  const [outcome, setOutcome] = useState<Outcome>(null);
  const [busy, setBusy] = useState(false);

  async function onFile(f: File | undefined) {
    if (!f) return;
    setBusy(true);
    try {
      if (f.size > 25 * 1024 * 1024) throw new Error("That file is too large to be a Mahtem certificate.");
      const hash = await sha256Hex(f);
      setOutcome({ kind: hash === expected.toLowerCase() ? "match" : "differ", name: f.name });
    } catch (e) {
      setOutcome({ kind: "error", message: e instanceof Error ? e.message : "Could not read that file." });
    } finally {
      setBusy(false);
      if (input.current) input.current.value = "";
    }
  }

  const big = variant === "mobile";
  return (
    <div className="flex flex-col gap-2">
      <input ref={input} type="file" accept="application/pdf,.pdf" className="sr-only" aria-label="Choose the PDF you received" onChange={(e) => onFile(e.target.files?.[0])} tabIndex={-1} />
      <button
        type="button"
        className="btn btn-secondary"
        style={big ? { minHeight: 48, fontSize: 15, paddingInline: 16 } : undefined}
        disabled={busy}
        onClick={() => input.current?.click()}
      >
        {busy ? "Checking file…" : big ? "Compare with the PDF I received" : "Compare with my copy"}
      </button>
      <div role="status" aria-live="polite">
        {outcome?.kind === "match" && (
          <div className="border-2 border-ok bg-ok-tint p-3 text-sm text-ok-ink">
            <b>Identical.</b> <span className="mono text-xs">{outcome.name}</span> is byte-for-byte the document the issuer sealed.
          </div>
        )}
        {outcome?.kind === "differ" && (
          <div className="border-2 border-seal bg-accent-100 p-3 text-sm text-accent-900">
            <b>This file is different.</b> It does not match the document the issuer sealed, so it may have been edited or may be a different document. Rely on this page, not on the file.
          </div>
        )}
        {outcome?.kind === "error" && <div className="border-2 border-seal bg-accent-100 p-3 text-sm text-accent-900">{outcome.message}</div>}
      </div>
    </div>
  );
}
