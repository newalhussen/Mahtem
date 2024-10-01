"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Copy } from "lucide-react";
import { api, ApiError } from "@/lib/api.client";
import { dateShort } from "@/lib/format";
import type { OrgProfile } from "@/lib/types";
import { useToast } from "./toast";
import { Alert } from "./ui";

function Row({ title, hint, children }: { title: string; hint: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-4 border-b border-divider py-6 lg:grid-cols-[260px_minmax(0,1fr)] lg:gap-8">
      <div>
        <h2 className="m-0 mb-1 text-base">{title}</h2>
        <div className="text-[13px] text-neutral-700">{hint}</div>
      </div>
      {children}
    </div>
  );
}

export function SettingsOrg({ profile, isAdmin }: { profile: OrgProfile; isAdmin: boolean }) {
  const router = useRouter();
  const toast = useToast();
  const [p, setP] = useState(profile);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [dnsBusy, setDnsBusy] = useState(false);
  const [dnsMsg, setDnsMsg] = useState<string | null>(null);
  const set = <K extends keyof OrgProfile>(k: K, v: OrgProfile[K]) => setP((x) => ({ ...x, [k]: v }));
  const sig = (i: number) => p.signatories[i] ?? { name: "", title: "" };
  const setSig = (i: number, key: "name" | "title", v: string) => {
    const next = [sig(0), sig(1)];
    next[i] = { ...next[i], [key]: v };
    set("signatories", next);
  };
  const disabled = !isAdmin;
  const field = (n: string) => ({ "aria-invalid": errors[n] ? true : undefined });
  const err = (n: string) => (errors[n] ? <div className="field-error">{errors[n]}</div> : null);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setErrors({});
    try {
      const out = await api<OrgProfile>("/org/profile", {
        method: "PUT",
        body: {
          name: p.name, nameAm: p.nameAm || null, initials: p.initials, department: p.department || null,
          accreditationBody: p.accreditationBody || null, country: p.country, verifierContact: p.verifierContact || null,
          showDetailsPublicly: p.showDetailsPublicly, showRevocationReason: p.showRevocationReason, allowPdfDownload: p.allowPdfDownload,
          signatories: p.signatories.filter((s) => s.name.trim() && s.title.trim()),
        },
      });
      setP(out);
      toast("Settings saved");
      router.refresh();
    } catch (e2) {
      if (e2 instanceof ApiError) {
        setErrors(e2.fields ?? {});
        setError(e2.fields && Object.keys(e2.fields).length ? "Check the highlighted fields." : e2.message);
      } else setError("Could not save. Try again.");
    } finally {
      setBusy(false);
    }
  }

  async function checkDomain() {
    setDnsBusy(true);
    setDnsMsg(null);
    try {
      const out = await api<OrgProfile>("/org/domain/verify", { method: "POST" });
      setP(out);
      if (out.domainVerified) {
        toast(`${out.domain} is verified`);
        router.refresh();
      } else setDnsMsg("We couldn’t find the record yet. DNS changes can take a few minutes to appear. Check the name and value, then try again.");
    } catch (e) {
      setDnsMsg(e instanceof ApiError ? e.message : "Could not check DNS right now.");
    } finally {
      setDnsBusy(false);
    }
  }

  return (
    <form onSubmit={save} className="flex max-w-[980px] flex-col px-5 pb-10 pt-2 lg:px-9" noValidate>
      <Row title="Institution profile" hint="Shown to anyone who verifies one of your credentials.">
        <fieldset disabled={disabled} className="m-0 flex min-w-0 flex-col gap-3.5 border-0 p-0">
          <div className="flex items-center gap-4">
            <div className="grid size-16 place-items-center border-2 border-ink font-extrabold">{p.initials}</div>
            <span className="text-xs text-neutral-700">Initials appear in the certificate crest box.</span>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="field"><label htmlFor="o1">Legal name</label><input id="o1" className="input" value={p.name} onChange={(e) => set("name", e.target.value)} {...field("name")} />{err("name")}</div>
            <div className="field"><label htmlFor="o2">Name in Amharic</label><input id="o2" className="input ethiopic" lang="am" value={p.nameAm ?? ""} onChange={(e) => set("nameAm", e.target.value)} {...field("nameAm")} />{err("nameAm")}</div>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="field"><label htmlFor="o3">Initials (crest)</label><input id="o3" className="input" maxLength={8} value={p.initials} onChange={(e) => set("initials", e.target.value.toUpperCase())} {...field("initials")} />{err("initials")}</div>
            <div className="field"><label htmlFor="o4">College or office</label><input id="o4" className="input" value={p.department ?? ""} onChange={(e) => set("department", e.target.value)} {...field("department")} />{err("department")}</div>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="field"><label htmlFor="o5">Accreditation body</label><input id="o5" className="input" value={p.accreditationBody ?? ""} onChange={(e) => set("accreditationBody", e.target.value)} {...field("accreditationBody")} />{err("accreditationBody")}</div>
            <div className="field"><label htmlFor="o6">Country</label><input id="o6" className="input" value={p.country} onChange={(e) => set("country", e.target.value)} {...field("country")} />{err("country")}</div>
          </div>
          <div className="field"><label htmlFor="o7">Contact for verifiers</label><input id="o7" type="email" className="input" value={p.verifierContact ?? ""} onChange={(e) => set("verifierContact", e.target.value)} {...field("verifierContact")} />{err("verifierContact")}</div>
        </fieldset>
      </Row>

      <Row title="Signatories" hint="Printed with a signature line on every certificate. Up to two.">
        <fieldset disabled={disabled} className="m-0 flex min-w-0 flex-col gap-3 border-0 p-0">
          {[0, 1].map((i) => (
            <div key={i} className="grid gap-3 sm:grid-cols-2">
              <div className="field"><label htmlFor={`s${i}n`}>Name {i + 1}</label><input id={`s${i}n`} className="input" value={sig(i).name} onChange={(e) => setSig(i, "name", e.target.value)} /></div>
              <div className="field"><label htmlFor={`s${i}t`}>Title {i + 1}</label><input id={`s${i}t`} className="input" value={sig(i).title} onChange={(e) => setSig(i, "title", e.target.value)} /></div>
            </div>
          ))}
          {err("signatories")}
        </fieldset>
      </Row>

      <Row title="Verified domain" hint="Proves to employers that credentials come from you.">
        <div className="flex min-w-0 flex-col gap-3">
          <div className="flex flex-wrap items-center gap-3 bg-surface px-3.5 py-3">
            <span className="mono text-sm font-semibold">{p.domain}</span>
            {p.domainVerified ? <span className="tag tag-ok">Verified · DNS TXT</span> : <span className="tag tag-revoked">Not verified</span>}
            {p.domainVerified && p.domainVerifiedAt && <span className="ml-auto text-xs text-neutral-700">Verified {dateShort(p.domainVerifiedAt)}</span>}
          </div>
          {!p.domainVerified && (
            <div className="flex flex-col gap-2.5 border-2 border-dashed border-divider p-4 text-sm">
              <div>Add this TXT record to your DNS, then check again. Credentials can only be sealed once your domain is verified.</div>
              <div className="grid gap-1 text-[13px] sm:grid-cols-[70px_1fr]">
                <span className="text-neutral-700">Name</span><span className="mono break-all">{p.domainTxtName}</span>
                <span className="text-neutral-700">Value</span><span className="mono break-all">{p.domainTxtValue}</span>
              </div>
              <div className="flex gap-2">
                <button type="button" className="btn btn-secondary" onClick={() => navigator.clipboard?.writeText(p.domainTxtValue).then(() => toast("Value copied"))}><Copy size={14} aria-hidden="true" />Copy value</button>
                {isAdmin && <button type="button" className="btn btn-primary" onClick={checkDomain} disabled={dnsBusy}>{dnsBusy ? "Checking DNS…" : "Check now"}</button>}
              </div>
              {dnsMsg && <Alert>{dnsMsg}</Alert>}
            </div>
          )}
        </div>
      </Row>

      <Row title="Public verification page" hint="What verifiers can see.">
        <fieldset disabled={disabled} className="m-0 flex flex-col gap-2.5 border-0 p-0">
          {([
            ["showDetailsPublicly", "Show honors and CGPA"],
            ["showRevocationReason", "Show revocation reason"],
            ["allowPdfDownload", "Allow verifiers to download the issued PDF"],
          ] as const).map(([k, label]) => (
            <label key={k} className="flex cursor-pointer items-center gap-2 text-sm">
              <input type="checkbox" className="size-4 accent-[#7c1405]" checked={p[k]} onChange={(e) => set(k, e.target.checked)} />{label}
            </label>
          ))}
        </fieldset>
      </Row>

      {error && <div className="pt-5"><Alert>{error}</Alert></div>}
      {isAdmin ? (
        <div className="flex gap-2 pt-5">
          <button className="btn btn-primary" disabled={busy}>{busy ? "Saving…" : "Save changes"}</button>
          <button type="button" className="btn btn-secondary" onClick={() => { setP(profile); setErrors({}); setError(null); }}>Discard</button>
        </div>
      ) : (
        <div className="pt-5 text-[13px] text-neutral-700">Only Admins can change these settings.</div>
      )}
    </form>
  );
}
