import Link from "next/link";
import { Clock, FileText, Mail, Download } from "lucide-react";
import { LogoMark } from "@/components/mahtem/logo";
import { Qr } from "@/components/mahtem/qr";
import { VerifyIdForm } from "@/components/mahtem/verify-id-form";
import { CompareWithPdf, CopyIdButton } from "./actions";
import { dateShort, stamp } from "@/lib/format";
import type { Check, VerificationResponse } from "@/lib/types";

const PUBLIC_BASE = process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000";

const L = "text-[11px] uppercase tracking-[0.1em] text-neutral-700";

function Header({ desktop }: { desktop?: boolean }) {
  return (
    <header className="flex items-center gap-2.5 border-b-2 border-divider px-5 py-3.5 lg:px-10 lg:py-4">
      <Link href="/" className="inline-flex items-center gap-2.5 !text-ink no-underline">
        <LogoMark size={desktop ? 28 : 24} />
        <span className="text-base font-extrabold tracking-[-0.01em] lg:text-lg">Mahtem</span>
      </Link>
      <span className="ml-auto text-xs text-neutral-700 lg:ml-0 lg:border-l lg:border-divider lg:pl-3 lg:text-[13px]">Credential verification</span>
      <div className="ml-auto hidden items-center gap-2 lg:flex">
        <VerifyIdForm id="another" placeholder="Verify another credential ID" cta="Verify" className="!flex-row" inputStyle={{ width: 280, minHeight: 36 }} />
      </div>
    </header>
  );
}

function Glyph({ state }: { state: Check["state"] }) {
  const bg = state === "PASS" ? "bg-ok" : state === "FAIL" ? "bg-seal" : "bg-neutral-700";
  return (
    <div className={`mt-px grid size-5 flex-none place-items-center text-[#f8f4f4] ${bg}`} aria-hidden="true">
      {state === "PASS" ? (
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"><path d="M20 6 9 17l-5-5" /></svg>
      ) : (
        <span className="text-xs font-extrabold">{state === "FAIL" ? "×" : "!"}</span>
      )}
    </div>
  );
}

function stateWord(state: Check["state"]) {
  return state === "PASS" ? "Passed" : state === "FAIL" ? "Failed" : "Warning";
}

function ChecksList({ checks }: { checks: Check[] }) {
  return (
    <ul className="m-0 flex list-none flex-col p-0">
      {checks.map((c) => (
        <li key={c.key} className="flex items-start gap-3 border-b border-divider py-[11px]">
          <Glyph state={c.state} />
          <div className="flex min-w-0 flex-1 flex-col gap-px">
            <div className="flex justify-between gap-2">
              <span className="text-sm font-semibold">{c.label}</span>
              <span className={`text-[13px] font-semibold ${c.state === "PASS" ? "text-ok-ink" : c.state === "FAIL" ? "text-seal" : "text-neutral-800"}`}>
                <span className="sr-only">{stateWord(c.state)}: </span>
                {c.value}
              </span>
            </div>
            <div className="text-xs text-neutral-700">{c.note}</div>
          </div>
        </li>
      ))}
    </ul>
  );
}

function Footer({ children }: { children: React.ReactNode }) {
  return <div className="flex flex-col gap-2 bg-surface px-5 pb-[22px] pt-4 text-xs leading-normal text-neutral-800 lg:px-10">{children}</div>;
}

const REASONS = [
  "The QR code or ID was altered after the certificate was issued.",
  "The certificate was not issued through Mahtem.",
  "The ID was typed incorrectly. Check each character.",
];

/** What an employer sees after scanning the QR code. One component, three outcomes, mobile first. */
export function VerificationView({ r }: { r: VerificationResponse }) {
  if (r.status === "VALID" && r.credential && r.issuer) return <Verified r={r} />;
  if (r.status === "REVOKED" && r.credential && r.issuer) return <Revoked r={r} />;
  return <NotVerified r={r} />;
}

function Verified({ r }: { r: VerificationResponse }) {
  const c = r.credential!;
  const i = r.issuer!;
  const pdf = `/api/public/credentials/${encodeURIComponent(c.id)}/pdf`;
  const canDownload = !!r.document?.downloadable;
  const hash = r.document?.sha256;
  const verifyUrl = `${PUBLIC_BASE}/verify/${c.id}`;
  return (
    <div className="mx-auto flex w-full max-w-[480px] flex-col bg-background lg:max-w-[1280px] lg:shadow-lg">
      <Header desktop />
      <section aria-labelledby="status" className="flex flex-col gap-3.5 bg-ok px-5 pb-6 pt-7 text-[#f8f4f4] lg:flex-row lg:items-center lg:gap-5 lg:px-10 lg:py-7">
        <div className="flex items-center gap-3.5 lg:gap-5">
          <div className="grid size-[52px] flex-none place-items-center border-2 border-[#f8f4f4] lg:size-[60px]">
            <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" aria-hidden="true"><path d="M20 6 9 17l-5-5" /></svg>
          </div>
          <div className="flex flex-col gap-0.5">
            <h1 id="status" className="m-0 text-[40px] font-extrabold leading-none tracking-[-0.02em] lg:text-[44px]">VERIFIED</h1>
            <p className="m-0 hidden text-base font-semibold lg:block">This credential is authentic and in good standing.</p>
          </div>
        </div>
        <p className="m-0 text-base font-semibold leading-[1.4] lg:hidden">This credential is authentic and in good standing.</p>
        <div className="flex items-center gap-1.5 text-xs opacity-90 lg:ml-auto lg:flex-col lg:items-end lg:gap-0.5 lg:text-right lg:text-[13px]">
          <span className="flex items-center gap-1.5"><Clock size={13} aria-hidden="true" />Checked {stamp(r.checkedAt)}</span>
          <span className="mono hidden lg:inline">Receipt {r.receipt}</span>
        </div>
      </section>

      <div className="flex flex-col lg:grid lg:grid-cols-[1.1fr_1fr] lg:border-b-2 lg:border-divider">
        <div className="flex flex-col lg:gap-[22px] lg:border-r-2 lg:border-divider lg:px-10 lg:py-8">
          <div className="flex flex-col gap-1 px-5 pb-1 pt-[22px] lg:p-0">
            <div className={L}>Awarded to</div>
            <div className="text-[28px] font-extrabold leading-[1.1] tracking-[-0.015em] lg:text-[40px] lg:leading-[1.05] lg:tracking-[-0.02em]">{c.recipientName}</div>
            {c.recipientNameAm && <div className="ethiopic text-[15px] text-neutral-700 lg:mt-1 lg:text-base" lang="am">{c.recipientNameAm}</div>}
          </div>
          <div className="flex flex-col gap-0.5 px-5 pb-5 pt-4 lg:p-0">
            <div className={L}>{c.typeLabel}</div>
            <div className="text-[18px] font-semibold leading-[1.3] lg:text-[22px]">{c.title}</div>
            {c.details && <div className="text-sm text-neutral-800 lg:text-[15px]">{c.details}</div>}
          </div>

          {/* mobile: issuer row, dates, id; desktop: three-column strip */}
          <div className="mx-5 flex items-center gap-3 border-b border-t-2 border-b-divider border-t-divider py-3.5 lg:hidden">
            <div className="grid size-11 flex-none place-items-center border border-divider bg-surface text-xs font-extrabold">{i.initials}</div>
            <div className="flex min-w-0 flex-col">
              <div className="text-[15px] font-semibold">{i.name}</div>
              {i.department && <div className="text-xs text-neutral-700">{i.department}</div>}
            </div>
            {i.domainVerified && <span className="tag ml-auto flex-none bg-ok-tint text-ok-ink">Verified issuer</span>}
          </div>
          <div className="mx-5 grid grid-cols-2 border-b border-divider lg:hidden">
            <div className="flex flex-col gap-0.5 border-r border-divider py-3"><div className="text-[11px] text-neutral-700">Date of issue</div><div className="text-[15px] font-semibold">{dateShort(c.issuedAt)}</div></div>
            <div className="flex flex-col gap-0.5 py-3 pl-3.5"><div className="text-[11px] text-neutral-700">{c.dateLabel}</div><div className="text-[15px] font-semibold">{dateShort(c.conferredOn)}</div></div>
          </div>
          <div className="mx-5 flex items-center gap-2.5 border-b-2 border-divider py-3 lg:hidden">
            <div className="flex min-w-0 flex-col gap-0.5">
              <div className="text-[11px] text-neutral-700">Credential ID</div>
              <div className="mono break-all text-sm font-semibold tracking-[0.02em]">{c.id}</div>
            </div>
            <CopyIdButton id={c.id} />
          </div>
          <div className="hidden grid-cols-3 border-t-2 border-divider lg:grid">
            <div className="border-r border-divider py-3 pr-3"><div className="text-xs text-neutral-700">Issuing institution</div><div className="font-semibold">{i.name}</div></div>
            <div className="border-r border-divider px-3 py-3"><div className="text-xs text-neutral-700">{c.dateLabel} · Issued</div><div className="font-semibold">{dateShort(c.conferredOn)} · {dateShort(c.issuedAt)}</div></div>
            <div className="py-3 pl-3"><div className="text-xs text-neutral-700">Credential ID</div><div className="mono flex items-center gap-2 text-sm font-semibold">{c.id}<CopyIdButton id={c.id} size={28} /></div></div>
          </div>

          <div className={`px-5 pb-1.5 pt-[18px] ${L} lg:hidden`}>Security checks</div>
          <div className="px-5 lg:p-0"><ChecksList checks={r.checks} /></div>

          <div className="flex flex-col gap-2 p-5 lg:hidden">
            {canDownload ? (
              <a className="btn btn-primary !min-h-12 !px-4 !text-[15px]" href={pdf} download>
                <FileText size={16} aria-hidden="true" />View issued certificate
              </a>
            ) : null}
            {hash ? <CompareWithPdf expected={hash} /> : null}
          </div>
        </div>

        <aside className="hidden flex-col gap-3.5 bg-surface px-10 py-8 lg:flex" aria-label="Issued document">
          <div className={L}>Issued document</div>
          <div className="relative flex aspect-[1.414] flex-col border border-neutral-300 bg-paper px-[26px] py-[22px] shadow-md">
            <div className="pointer-events-none absolute inset-2 border-[1.5px] border-seal" />
            <div className="text-[9px] font-semibold uppercase tracking-[0.14em] text-seal">{i.name}</div>
            {i.nameAm && <div className="ethiopic text-[8px] text-neutral-700">{i.nameAm}</div>}
            <div className="mt-auto text-[8px] text-neutral-700">This is to certify that</div>
            <div className="text-[22px] font-extrabold leading-[1.05] tracking-[-0.02em]">{c.recipientName}</div>
            <div className="mt-1 text-[9px]">{c.title}{c.details ? ` · ${c.details}` : ""}</div>
            <div className="mt-auto flex items-end gap-3 border-t-[1.5px] border-seal pt-2">
              <div className="size-11 flex-none"><Qr text={verifyUrl} /></div>
              <div className="mono text-[7px] font-semibold">{c.id}</div>
              <div className="ml-auto text-[7px] text-neutral-700">Sealed with the issuer’s key</div>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            {canDownload && (
              <a className="btn btn-primary" href={pdf} download><Download size={15} aria-hidden="true" />Download issued PDF</a>
            )}
            {hash && <div className="min-w-[180px]"><CompareWithPdf expected={hash} variant="desktop" /></div>}
          </div>
          {hash && <p className="m-0 text-xs leading-normal text-neutral-700">“Compare” checks a PDF you received against the SHA-256 fingerprint recorded at issuance. The file never leaves your device.</p>}
        </aside>
      </div>

      <Footer>
        <div>Mahtem confirms this record on behalf of the issuer. Only details printed on the certificate are shown.</div>
        <div className="flex flex-wrap gap-x-4 gap-y-1 lg:gap-6">
          <Link href="/#how">How verification works</Link>
          {i.contact && <a href={`mailto:${i.contact}?subject=Concern%20about%20${encodeURIComponent(c.id)}`}>Report a concern</a>}
          <span className="mono text-neutral-700 lg:hidden">Receipt {r.receipt}</span>
        </div>
      </Footer>
    </div>
  );
}

function Revoked({ r }: { r: VerificationResponse }) {
  const c = r.credential!;
  const i = r.issuer!;
  const rev = r.revocation;
  return (
    <div className="mx-auto flex w-full max-w-[480px] flex-col bg-background sm:shadow-lg">
      <Header />
      <section aria-labelledby="status" className="flex flex-col gap-3.5 bg-seal px-5 pb-6 pt-7 text-[#f8f4f4]">
        <div className="flex items-center gap-3.5">
          <div className="grid size-[52px] flex-none place-items-center border-2 border-[#f8f4f4]">
            <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="m5.6 5.6 12.8 12.8" /></svg>
          </div>
          <h1 id="status" className="m-0 text-[40px] font-extrabold leading-none tracking-[-0.02em]">REVOKED</h1>
        </div>
        <p className="m-0 text-base font-semibold leading-[1.4]">The issuer has withdrawn this credential. It should not be accepted as proof of qualification.</p>
        <div className="text-xs opacity-90">Checked {stamp(r.checkedAt)}</div>
      </section>

      {rev && (
        <div className="mx-5 mt-5 flex flex-col gap-2.5 border-2 border-seal bg-accent-100 px-4 py-3.5 text-accent-900">
          <dl className="m-0 grid grid-cols-[110px_1fr] gap-x-3 gap-y-1.5 text-sm">
            <dt>Revoked on</dt><dd className="m-0 font-semibold">{dateShort(rev.revokedAt)}</dd>
            {rev.reason && (<><dt>Reason</dt><dd className="m-0 font-semibold">{rev.reason}</dd></>)}
            {rev.revokedBy && (<><dt>Revoked by</dt><dd className="m-0 font-semibold">{rev.revokedBy}</dd></>)}
          </dl>
          {rev.note && <div className="border-t border-accent-300 pt-2.5 text-[13px] leading-[1.45]">Issuer note: “{rev.note}”</div>}
        </div>
      )}

      <div className={`px-5 pb-1.5 pt-5 ${L}`}>Record as originally issued</div>
      <dl className="m-0 mx-5 grid grid-cols-[110px_1fr] border-t-2 border-divider text-sm">
        {[
          ["Recipient", c.recipientName],
          ["Credential", c.title],
          ["Issuer", i.name],
          ["Issued", dateShort(c.issuedAt)],
        ].map(([k, v]) => (
          <div key={k} className="contents">
            <dt className="border-b border-divider py-2.5 text-neutral-700">{k}</dt>
            <dd className="m-0 border-b border-divider py-2.5 font-semibold">{v}</dd>
          </div>
        ))}
        <dt className="border-b-2 border-divider py-2.5 text-neutral-700">Credential ID</dt>
        <dd className="mono m-0 break-all border-b-2 border-divider py-2.5 text-[13px] font-semibold">{c.id}</dd>
      </dl>

      <div className={`px-5 pb-1.5 pt-[18px] ${L}`}>Security checks</div>
      <div className="px-5"><ChecksList checks={r.checks} /></div>

      {i.contact && (
        <div className="flex flex-col gap-2 p-5">
          <a className="btn btn-primary !min-h-12 !px-4 !text-[15px]" href={`mailto:${i.contact}?subject=Revoked%20credential%20${encodeURIComponent(c.id)}`}>
            <Mail size={16} aria-hidden="true" />Contact {i.name}
          </a>
        </div>
      )}
      <Footer>Revocation is published by the issuer and takes effect immediately for every copy of this certificate.</Footer>
    </div>
  );
}

function NotVerified({ r }: { r: VerificationResponse }) {
  const tampered = r.invalidCause === "TAMPERED";
  return (
    <div className="mx-auto flex w-full max-w-[480px] flex-col bg-background sm:shadow-lg">
      <Header />
      <section aria-labelledby="status" className="flex flex-col gap-3.5 border-b-[6px] border-b-[#ec3013] bg-neutral-900 px-5 pb-6 pt-7 text-[#f8f4f4]">
        <div className="flex items-center gap-3.5">
          <div className="grid size-[52px] flex-none place-items-center border-2 border-[#f8f4f4]">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12" /></svg>
          </div>
          <h1 id="status" className="m-0 text-4xl font-extrabold leading-none tracking-[-0.02em]">NOT VERIFIED</h1>
        </div>
        <p className="m-0 text-base font-semibold leading-[1.4]">We could not confirm this credential. Treat it as unverified.</p>
        <div className="text-xs opacity-85">Checked {stamp(r.checkedAt)}</div>
      </section>

      <div className="flex flex-col gap-1.5 px-5 pt-5">
        <div className={L}>What we found</div>
        <div className="text-[18px] font-semibold leading-[1.3]">
          {tampered ? "This record does not match what the issuer sealed." : "No credential in Mahtem matches this code."}
        </div>
        <div className="text-sm leading-normal text-neutral-800">
          The link carried ID <span className="mono bg-surface px-1.5 py-px text-[13px] font-semibold break-all">{r.queriedId}</span>
          {tampered ? ", which exists but fails its integrity check." : ", which was never issued."}
        </div>
      </div>

      <div className="mx-5 mt-[18px] border-t-2 border-divider">
        <div className="pb-1.5 pt-3 text-[13px] font-semibold">This usually means one of the following</div>
        <ol className="m-0 list-none p-0">
          {REASONS.map((t, n) => (
            <li key={t} className="flex gap-2.5 border-b border-divider py-2 text-sm leading-[1.4]">
              <span className="mono pt-0.5 text-xs font-semibold text-neutral-700">{String(n + 1).padStart(2, "0")}</span>
              <span>{t}</span>
            </li>
          ))}
        </ol>
      </div>

      <div className="mx-5 mt-5 flex flex-col gap-2.5 bg-surface p-4">
        <label htmlFor="vid" className="text-xs text-[#201e1d]/70">Check by credential ID</label>
        <VerifyIdForm id="vid" initial={r.queriedId} cta="Verify ID" buttonClass="btn btn-primary" inputStyle={{ minHeight: 48, fontSize: 15, background: "var(--bg)" }} className="!gap-0" />
        <div className="text-xs text-neutral-700">The ID is printed under the QR code on the certificate.</div>
      </div>

      <div className="flex flex-col gap-1 p-5 text-sm">
        <div className="font-semibold">Still unsure?</div>
        <div className="leading-normal text-neutral-800">Contact the institution named on the certificate directly, using contact details from their official website rather than the document.</div>
      </div>
      <Footer>
        <div className="flex gap-4">
          <Link href="/#how">How verification works</Link>
        </div>
        <span className="mono text-neutral-700">Receipt {r.receipt}</span>
      </Footer>
    </div>
  );
}
