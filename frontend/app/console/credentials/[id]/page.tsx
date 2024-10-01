import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Ban } from "lucide-react";
import { FitCertificate } from "@/components/mahtem/fit-certificate";
import { CredentialActions } from "@/components/console/credential-actions";
import { StatusTag } from "@/components/console/ui";
import { ApiFailure, apiGet } from "@/lib/api.server";
import { dateLong, dateShort, stamp } from "@/lib/format";
import type { CredentialDetail, OrgProfile } from "@/lib/types";

export const metadata: Metadata = { title: "Credential" };

const KIND_COLOR: Record<string, string> = { verified: "bg-ok", revoked: "bg-seal", failed: "bg-seal", sealed: "bg-ink" };

export default async function CredentialPage({ params }: PageProps<"/console/credentials/[id]">) {
  const { id } = await params;
  let d: CredentialDetail;
  try {
    d = await apiGet<CredentialDetail>(`/api/org/credentials/${encodeURIComponent(id)}`);
  } catch (e) {
    if (e instanceof ApiFailure && e.status === 404) notFound();
    throw e;
  }
  const org = await apiGet<OrgProfile>("/api/org/profile");
  const c = d.summary;
  const sealed = c.status !== "PENDING";
  const facts: [string, string, boolean?][] = [
    ["Credential ID", c.id, true],
    ["Reference", c.recipientRef ?? "—", true],
    ["Recipient email", c.recipientEmail ?? "—"],
    [d.dateLabel, dateLong(c.conferredOn)],
    ["Issued", sealed ? dateLong(c.issuedAt) : "Not yet sealed"],
    ["Type", c.typeLabel],
    ["Created by", d.createdBy],
    ["Verifications", String(c.checks)],
  ];
  const proof: [string, string][] = [
    ["Signature", d.proof.signature ? `${d.proof.algorithm} · valid` : "Not yet signed"],
    ["Signing key", d.proof.keyLabel ? `${d.proof.keyLabel} · ${d.proof.keyFingerprint}` : "—"],
    ["Record hash (SHA-256)", d.proof.recordHash ?? "—"],
    ["PDF fingerprint (SHA-256)", d.proof.pdfSha256 ?? "—"],
    ["Sealed at", d.proof.sealedAt ? `${stamp(d.proof.sealedAt)}${d.proof.sealedBy ? ` · ${d.proof.sealedBy}` : ""}` : "—"],
  ];

  return (
    <div className="flex flex-col">
      <div className="flex flex-wrap items-end gap-3 border-b-2 border-divider px-5 pb-5 pt-7 lg:px-9">
        <div className="min-w-[280px] flex-1">
          <div className="text-xs text-neutral-700"><Link href="/console/credentials">Credentials</Link> / <span className="mono">{c.id}</span></div>
          <div className="mt-0.5 flex flex-wrap items-center gap-3">
            <h1 className="m-0 text-[32px]">{c.recipientName}</h1>
            <StatusTag status={c.status} approval={c.approvalRequired} large />
          </div>
          <div className="mt-0.5 text-sm text-neutral-800">{c.title}{c.details ? ` · ${c.details}` : ""}</div>
        </div>
        <CredentialActions id={c.id} name={c.recipientName} verifyUrl={d.verifyUrl} canSeal={d.canSeal} canRevoke={d.canRevoke} sealed={sealed} hasEmail={!!c.recipientEmail} />
      </div>

      {d.revocation && (
        <div className="mx-5 mt-5 flex items-start gap-3 border-2 border-seal bg-accent-100 px-4 py-3.5 text-accent-900 lg:mx-9">
          <Ban size={18} strokeWidth={2.2} className="mt-0.5 flex-none" aria-hidden="true" />
          <div className="text-sm leading-normal">
            <b>Revoked {dateShort(d.revocation.revokedAt)}</b> · {d.revocation.reason}. Anyone scanning this certificate now sees “Revoked”. Revocation cannot be undone; issue a new credential to correct a record.
          </div>
        </div>
      )}
      {c.status === "PENDING" && (
        <div className="mx-5 mt-5 border-2 border-divider bg-surface px-4 py-3.5 text-sm leading-normal lg:mx-9">
          {c.approvalRequired
            ? "This credential is waiting for a second person to approve it. Until it is sealed, verifiers will see “Not verified”."
            : "This is a draft. Until it is sealed, verifiers will see “Not verified”."}
        </div>
      )}

      <div className="grid lg:grid-cols-[minmax(0,1.3fr)_minmax(300px,1fr)]">
        <div className="flex min-w-0 flex-col gap-6 border-divider px-5 py-6 lg:border-r-2 lg:px-9">
          <div className="w-full">
            <FitCertificate
              maxWidth={560}
              recipient={c.recipientName}
              nameAm={c.recipientNameAm}
              title={c.title}
              details={c.details}
              statement={d.statement}
              conferredLabel={d.dateLabel}
              conferredDate={dateLong(c.conferredOn)}
              issuedDate={sealed ? dateLong(c.issuedAt) : "On sealing"}
              credId={c.id}
              verifyUrl={d.verifyUrl}
              institution={org.name}
              institutionAm={org.nameAm}
              department={org.department}
              initials={org.initials}
              signatories={org.signatories}
            />
          </div>
          <dl className="m-0 grid border-t-2 border-divider" style={{ gridTemplateColumns: "repeat(auto-fit,minmax(180px,1fr))" }}>
            {facts.map(([k, v, mono]) => (
              <div key={k} className="border-b border-divider py-3 pr-3">
                <dt className="text-xs text-neutral-700">{k}</dt>
                <dd className={`m-0 break-words text-sm font-semibold ${mono ? "mono" : ""}`}>{v}</dd>
              </div>
            ))}
          </dl>
        </div>
        <div className="flex flex-col">
          <section className="flex flex-col gap-2.5 border-b-2 border-divider px-5 py-6 lg:px-7">
            <h2 className="m-0 text-[17px]">Cryptographic seal</h2>
            {proof.map(([k, v]) => (
              <div key={k} className="flex flex-col gap-px border-t border-divider py-2">
                <span className="text-xs text-neutral-700">{k}</span>
                <span className="mono break-all text-[13px]">{v}</span>
              </div>
            ))}
          </section>
          <section className="flex flex-col gap-1.5 px-5 pb-10 pt-6 lg:px-7">
            <div className="flex items-baseline">
              <h2 className="m-0 text-[17px]">History</h2>
              <Link className="btn btn-ghost ml-auto" href="/console/log">Full log →</Link>
            </div>
            {d.history.map((h, i) => (
              <div key={i} className="grid grid-cols-[14px_1fr] gap-3">
                <div className="flex flex-col items-center">
                  <span className={`mt-[5px] size-2.5 ${KIND_COLOR[h.kind] ?? "bg-neutral-600"}`} />
                  <span className="w-0.5 flex-1 bg-neutral-300" />
                </div>
                <div className="pb-3.5">
                  <div className="text-sm font-semibold">{h.title}</div>
                  <div className="text-xs text-neutral-700">{h.detail}{h.detail ? " · " : ""}{stamp(h.at)}</div>
                </div>
              </div>
            ))}
          </section>
        </div>
      </div>
    </div>
  );
}
