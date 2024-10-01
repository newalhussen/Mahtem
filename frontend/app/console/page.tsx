import type { Metadata } from "next";
import Link from "next/link";
import { Plus } from "lucide-react";
import { StatusTag } from "@/components/console/ui";
import { apiGet, requireUser } from "@/lib/api.server";
import { dateShort, today } from "@/lib/format";
import type { Overview } from "@/lib/types";

export const metadata: Metadata = { title: "Overview" };

export default async function OverviewPage() {
  const me = await requireUser();
  const o = await apiGet<Overview>("/api/org/overview");
  const max = Math.max(1, ...o.verifications.map((d) => d.ok + d.bad));
  const first = o.verifications[0]?.day;
  const mid = o.verifications[Math.floor(o.verifications.length / 2)]?.day;
  const last = o.verifications[o.verifications.length - 1]?.day;

  return (
    <div className="flex flex-col">
      <div className="flex flex-wrap items-end gap-4 border-b-2 border-divider px-5 pb-5 pt-7 lg:px-9">
        <div className="min-w-[240px] flex-1">
          <div className="text-xs text-neutral-700">{today()}</div>
          <h1 className="m-0 mt-0.5 text-[32px]">Overview</h1>
        </div>
        <Link className="btn btn-secondary" href="/console/log">Open verification log</Link>
        {me.organization.canIssue && (
          <Link className="btn btn-primary" href="/console/credentials/new"><Plus size={15} strokeWidth={2.4} aria-hidden="true" />Issue credential</Link>
        )}
      </div>

      <div className="grid border-b-2 border-divider" style={{ gridTemplateColumns: "repeat(auto-fit,minmax(160px,1fr))" }}>
        {o.kpis.map((k) => (
          <div key={k.label} className="flex flex-col gap-1.5 border-r border-divider py-5 pl-5 pr-6 lg:pl-9">
            <div className="text-xs text-neutral-700">{k.label}</div>
            <div className="text-4xl font-extrabold leading-none tracking-[-0.02em]">{k.value}</div>
            <div className="text-xs text-neutral-800">{k.note}</div>
          </div>
        ))}
      </div>

      <div className="grid border-b-2 border-divider lg:grid-cols-[minmax(0,1.6fr)_minmax(280px,1fr)]">
        <div className="flex flex-col gap-4 border-divider px-5 py-6 lg:border-r-2 lg:px-9">
          <div className="flex flex-wrap items-baseline gap-3">
            <h2 className="m-0 whitespace-nowrap text-[18px]">Verifications · last 30 days</h2>
            <span className="text-xs text-neutral-700">{o.checks30d.toLocaleString("en-GB")} checks · {o.verifiedPct.toFixed(1)}% verified</span>
            <div className="ml-auto flex gap-3.5 text-xs">
              <span className="flex items-center gap-1.5"><span className="size-2.5 bg-ink" />Verified</span>
              <span className="flex items-center gap-1.5"><span className="size-2.5 bg-seal" />Revoked / invalid</span>
            </div>
          </div>
          <div className="flex h-[180px] items-end gap-1 border-b-2 border-ink pt-2" role="img" aria-label={`Daily verification checks for the last 30 days: ${o.checks30d} in total`}>
            {o.verifications.map((d) => (
              <div key={d.day} title={`${dateShort(d.day)}: ${d.ok + d.bad} checks`} className="flex h-full flex-1 flex-col justify-end">
                <div className="bg-seal" style={{ height: `${(d.bad / max) * 100}%` }} />
                <div className="bg-ink" style={{ height: `${(d.ok / max) * 100}%` }} />
              </div>
            ))}
          </div>
          <div className="mono flex justify-between text-[11px] text-neutral-700">
            <span>{first ? dateShort(first).replace(/ \d{4}$/, "") : ""}</span>
            <span>{mid ? dateShort(mid).replace(/ \d{4}$/, "") : ""}</span>
            <span>{last ? dateShort(last).replace(/ \d{4}$/, "") : ""}</span>
          </div>
        </div>
        <div className="flex flex-col gap-3 px-5 py-6 lg:px-7">
          <h2 className="m-0 text-[18px]">Needs attention</h2>
          <div className="flex flex-col border-t-2 border-divider">
            {o.attention.length === 0 && <div className="py-3 text-sm text-neutral-800">Nothing needs your attention right now.</div>}
            {o.attention.map((a) => (
              <Link key={a.title} href={a.href} className="flex items-start gap-3 border-b border-divider py-3 !text-ink no-underline hover:bg-surface">
                <span className={`mt-1.5 size-2 flex-none ${a.tone === "alert" ? "bg-seal" : "bg-neutral-600"}`} />
                <span className="flex flex-1 flex-col gap-0.5">
                  <span className="text-sm font-semibold">{a.title}</span>
                  <span className="text-xs text-neutral-700">{a.detail}</span>
                </span>
                <span className="text-[13px] font-semibold text-seal">{a.cta}</span>
              </Link>
            ))}
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-3 px-5 pb-10 pt-6 lg:px-9">
        <div className="flex items-baseline">
          <h2 className="m-0 text-[18px]">Recently issued</h2>
          <Link className="btn btn-ghost ml-auto" href="/console/credentials">All credentials →</Link>
        </div>
        <div className="overflow-x-auto">
          <table className="table min-w-[640px]">
            <thead>
              <tr><th>Recipient</th><th>Credential</th><th>Issued</th><th>Status</th><th className="!text-right">Verifications</th></tr>
            </thead>
            <tbody>
              {o.recent.map((c) => (
                <tr key={c.id}>
                  <td className="font-semibold"><Link href={`/console/credentials/${c.id}`} className="!text-ink no-underline hover:!text-seal">{c.recipientName}</Link></td>
                  <td>{c.title}</td>
                  <td>{dateShort(c.issuedAt)}</td>
                  <td><StatusTag status={c.status} approval={c.approvalRequired} /></td>
                  <td className="mono text-right">{c.checks}</td>
                </tr>
              ))}
              {o.recent.length === 0 && <tr><td colSpan={5} className="text-neutral-700">No credentials yet. Issue your first one to see it here.</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
