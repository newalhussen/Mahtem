"use client";

import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { KeyRound } from "lucide-react";
import { FitCertificate } from "@/components/mahtem/fit-certificate";
import { api, ApiError } from "@/lib/api.client";
import { dateLong } from "@/lib/format";
import { CREDENTIAL_TYPES, type CredentialDetail, type CredentialType, type IssueMode, type Me, type OrgProfile } from "@/lib/types";
import { BulkIssue } from "./bulk-issue";
import { useToast } from "./toast";
import { Alert } from "./ui";

const STATEMENT: Record<CredentialType, string> = {
  DEGREE: "having fulfilled all requirements prescribed by the Academic Senate, has been awarded the degree of",
  DIPLOMA: "having completed the prescribed programme of study, is awarded the diploma in",
  TRAINING: "having successfully completed the prescribed course of study and assessment, is awarded the",
  CERTIFICATION: "having demonstrated the knowledge and skills required, is certified as",
  EMPLOYMENT: "is confirmed to be employed by this organization in the position of",
  AWARD: "is recognised with the",
  LICENSE: "is licensed and authorised to practise as",
};

const HONORS = ["With Great Distinction", "With Very Great Distinction", "With Distinction"];

type Form = {
  type: CredentialType; recipientName: string; recipientNameAm: string; recipientEmail: string; recipientRef: string;
  title: string; details: string; conferredOn: string; sendEmail: boolean; requireApproval: boolean;
};

const blank = (): Form => ({
  type: "DEGREE", recipientName: "", recipientNameAm: "", recipientEmail: "", recipientRef: "",
  title: "", details: "", conferredOn: new Date().toISOString().slice(0, 10), sendEmail: true, requireApproval: false,
});

function Section({ n, title, children }: { n: string; title: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-3.5">
      <div className="flex items-baseline gap-2.5 border-b-2 border-divider pb-2">
        <span className="mono text-xs font-semibold text-seal">{n}</span>
        <h2 className="m-0 text-[17px]">{title}</h2>
      </div>
      {children}
    </div>
  );
}

export function IssueForm({ me, org }: { me: Me; org: OrgProfile }) {
  const router = useRouter();
  const toast = useToast();
  const [mode, setMode] = useState<"single" | "bulk">("single");
  const [f, setF] = useState<Form>(blank);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState<IssueMode | null>(null);
  const t = useMemo(() => CREDENTIAL_TYPES.find((x) => x.value === f.type)!, [f.type]);
  const set = <K extends keyof Form>(k: K, v: Form[K]) => setF((p) => ({ ...p, [k]: v }));
  const key = me.organization;

  async function submit(intent: "SEAL" | "DRAFT") {
    setBusy(intent === "SEAL" && f.requireApproval ? "REQUIRE_APPROVAL" : intent);
    setErrors({});
    setFormError(null);
    const mode: IssueMode = intent === "DRAFT" ? "DRAFT" : f.requireApproval ? "REQUIRE_APPROVAL" : "SEAL";
    try {
      const d = await api<CredentialDetail>("/org/credentials", {
        body: {
          type: f.type, title: f.title.trim(), details: f.details.trim() || null, statement: null,
          recipientName: f.recipientName.trim(), recipientNameAm: f.recipientNameAm.trim() || null,
          recipientEmail: f.recipientEmail.trim() || null, recipientRef: f.recipientRef.trim() || null,
          conferredOn: f.conferredOn, mode, sendEmail: f.sendEmail && !!f.recipientEmail.trim() && mode === "SEAL",
        },
      });
      toast(
        mode === "SEAL" ? `Credential sealed${f.sendEmail && f.recipientEmail ? ` and emailed to ${f.recipientEmail}` : ""}`
          : mode === "DRAFT" ? "Draft saved. It is not verifiable until sealed." : "Sent for approval. A second person must seal it.",
      );
      router.push(`/console/credentials/${d.summary.id}`);
      router.refresh();
    } catch (e) {
      if (e instanceof ApiError) {
        setErrors(e.fields ?? {});
        setFormError(e.fields && Object.keys(e.fields).length ? "Check the highlighted fields." : e.message);
      } else setFormError("Something went wrong. Try again.");
      setBusy(null);
    }
  }

  const field = (name: string) => ({ "aria-invalid": errors[name] ? true : undefined, "aria-describedby": errors[name] ? `${name}-err` : undefined });
  const err = (name: string) => (errors[name] ? <div id={`${name}-err`} className="field-error">{errors[name]}</div> : null);
  const ready = f.recipientName.trim() && f.title.trim() && f.conferredOn;

  return (
    <>
      <div className="flex flex-wrap items-end gap-3 border-b-2 border-divider px-5 pb-5 pt-7 lg:px-9">
        <div className="min-w-[240px] flex-1">
          <div className="text-xs text-neutral-700"><a href="/console/credentials">Credentials</a> / New</div>
          <h1 className="m-0 mt-0.5 text-[32px]">Issue credential</h1>
        </div>
        <div className="seg" role="group" aria-label="Single or bulk">
          <button type="button" aria-pressed={mode === "single"} onClick={() => setMode("single")} style={{ padding: "7px 14px" }}>Single</button>
          <button type="button" aria-pressed={mode === "bulk"} onClick={() => setMode("bulk")} style={{ padding: "7px 14px" }}>Bulk (CSV)</button>
        </div>
      </div>

      {mode === "bulk" ? (
        <BulkIssue me={me} />
      ) : (
        <form onSubmit={(e) => { e.preventDefault(); if (ready) void submit("SEAL"); }} className="grid lg:grid-cols-[minmax(360px,1fr)_minmax(0,1.15fr)]" noValidate>
          <div className="flex flex-col gap-7 border-divider px-5 pb-10 pt-6 lg:border-r-2 lg:px-9">
            <Section n="01" title="Recipient">
              <div className="field">
                <label htmlFor="f1">Full name as it appears on the certificate</label>
                <input id="f1" className="input" value={f.recipientName} onChange={(e) => set("recipientName", e.target.value)} {...field("recipientName")} autoComplete="off" />
                {err("recipientName")}
              </div>
              <div className="field">
                <label htmlFor="f2">Name in Amharic (optional)</label>
                <input id="f2" className="input ethiopic" lang="am" value={f.recipientNameAm} onChange={(e) => set("recipientNameAm", e.target.value)} {...field("recipientNameAm")} />
                {err("recipientNameAm")}
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="field">
                  <label htmlFor="f3">Email</label>
                  <input id="f3" type="email" className="input" value={f.recipientEmail} onChange={(e) => set("recipientEmail", e.target.value)} {...field("recipientEmail")} autoComplete="off" />
                  {err("recipientEmail")}
                </div>
                <div className="field">
                  <label htmlFor="f4">Student or staff ID</label>
                  <input id="f4" className="input mono" value={f.recipientRef} onChange={(e) => set("recipientRef", e.target.value)} {...field("recipientRef")} autoComplete="off" />
                  {err("recipientRef")}
                </div>
              </div>
            </Section>

            <Section n="02" title="Credential">
              <div className="field">
                <label htmlFor="f5">Type</label>
                <select id="f5" className="input" value={f.type} onChange={(e) => set("type", e.target.value as CredentialType)}>
                  {CREDENTIAL_TYPES.map((x) => <option key={x.value} value={x.value}>{x.label}</option>)}
                </select>
              </div>
              <div className="field">
                <label htmlFor="f6">Credential title</label>
                <input id="f6" className="input" value={f.title} onChange={(e) => set("title", e.target.value)} {...field("title")} placeholder={f.type === "DEGREE" ? "Bachelor of Science in Computer Science" : undefined} />
                {err("title")}
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="field">
                  <label htmlFor="f7">{t.detailsLabel}</label>
                  <input id="f7" className="input" list="honors" value={f.details} onChange={(e) => set("details", e.target.value)} {...field("details")} placeholder={t.detailsHint} />
                  {(f.type === "DEGREE" || f.type === "DIPLOMA") && <datalist id="honors">{HONORS.map((h) => <option key={h} value={h} />)}</datalist>}
                  {err("details")}
                </div>
                <div className="field">
                  <label htmlFor="f8">{t.dateLabel}</label>
                  <input id="f8" type="date" className="input" max={new Date().toISOString().slice(0, 10)} value={f.conferredOn} onChange={(e) => set("conferredOn", e.target.value)} {...field("conferredOn")} />
                  {err("conferredOn")}
                </div>
              </div>
            </Section>

            <Section n="03" title="Seal and deliver">
              <div className="flex items-center gap-3 bg-surface p-3">
                <KeyRound size={18} aria-hidden="true" />
                <div className="flex flex-1 flex-col">
                  <span className="text-sm font-semibold">Sign with {key.activeKeyLabel ?? "the active key"}</span>
                  <span className="mono text-xs text-neutral-700">Ed25519 · {key.activeKeyFingerprint ?? "no active key"}</span>
                </div>
                <span className="tag tag-ok">Active</span>
              </div>
              <label className="flex cursor-pointer items-center gap-2 text-sm">
                <input type="checkbox" className="size-4 accent-[#7c1405]" checked={f.sendEmail} onChange={(e) => set("sendEmail", e.target.checked)} />
                Email the certificate and verification link to the recipient
              </label>
              <label className="flex cursor-pointer items-center gap-2 text-sm">
                <input type="checkbox" className="size-4 accent-[#7c1405]" checked={f.requireApproval} onChange={(e) => set("requireApproval", e.target.checked)} />
                Require a second approver before sealing
              </label>
            </Section>

            {formError && <Alert>{formError}</Alert>}
            <div className="flex gap-2 border-t-2 border-divider pt-[18px]">
              <button type="submit" className="btn btn-primary !min-h-[42px] !px-4" disabled={!ready || !!busy}>
                {busy === "SEAL" ? "Sealing…" : busy === "REQUIRE_APPROVAL" ? "Sending…" : f.requireApproval ? "Send for approval" : "Seal and issue"}
              </button>
              <button type="button" className="btn btn-secondary !min-h-[42px]" disabled={!ready || !!busy} onClick={() => submit("DRAFT")}>
                {busy === "DRAFT" ? "Saving…" : "Save as draft"}
              </button>
            </div>
            <div className="-mt-4 text-xs text-neutral-700">Sealing signs the record and makes it verifiable immediately. Details cannot be edited afterwards; corrections are issued as a new credential.</div>
          </div>

          <div className="flex min-w-0 flex-col gap-3 bg-surface px-5 pb-10 pt-6 lg:px-9">
            <div className="flex items-baseline">
              <span className="eyebrow">Live preview</span>
              <span className="ml-auto text-xs text-neutral-700">A4 landscape</span>
            </div>
            <div className="w-full">
              <FitCertificate
                maxWidth={620}
                recipient={f.recipientName}
                nameAm={f.recipientNameAm}
                title={f.title}
                details={f.details}
                statement={STATEMENT[f.type]}
                conferredLabel={t.dateLabel.replace("Date ", "").replace(/^./, (c) => c.toUpperCase())}
                conferredDate={f.conferredOn ? dateLong(f.conferredOn) : ""}
                issuedDate="On sealing"
                credId={`MHT-${org.code}-${String(new Date().getFullYear()).slice(2)}-····-····`}
                verifyUrl="https://verify.mahtem.et/"
                institution={org.name}
                institutionAm={org.nameAm}
                department={org.department}
                initials={org.initials}
                signatories={org.signatories}
              />
            </div>
            <div className="max-w-[620px] text-xs text-neutral-700">The QR code and credential ID are generated when the record is sealed.</div>
          </div>
        </form>
      )}
    </>
  );
}
