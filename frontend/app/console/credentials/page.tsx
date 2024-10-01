import type { Metadata } from "next";
import Link from "next/link";
import { Download, Plus } from "lucide-react";
import { CredentialFilters } from "@/components/console/credential-filters";
import { EmptyState, PageHeader, StatusTag } from "@/components/console/ui";
import { apiGet, requireUser } from "@/lib/api.server";
import { dateShort } from "@/lib/format";
import type { CredentialSummary, PageOf, StatusCounts } from "@/lib/types";

export const metadata: Metadata = { title: "Credentials" };

const SIZE = 20;

export default async function CredentialsPage({ searchParams }: PageProps<"/console/credentials">) {
  const me = await requireUser();
  const sp = await searchParams;
  const one = (k: string) => (typeof sp[k] === "string" ? (sp[k] as string) : "");
  const qs = new URLSearchParams();
  for (const k of ["q", "status", "title", "year"]) if (one(k)) qs.set(k, one(k));
  const page = Math.max(0, Number(one("page")) || 0);
  const [list, stats] = await Promise.all([
    apiGet<PageOf<CredentialSummary>>(`/api/org/credentials?${qs}&page=${page}&size=${SIZE}`),
    apiGet<{ counts: StatusCounts; titles: string[] }>("/api/org/credentials/stats"),
  ]);
  const thisYear = new Date().getFullYear();
  const years = [thisYear, thisYear - 1, thisYear - 2, thisYear - 3];
  const filtered = qs.size > 0;
  const pageHref = (p: number) => `/console/credentials?${new URLSearchParams([...qs.entries(), ["page", String(p)]])}`;
  const from = list.total === 0 ? 0 : page * SIZE + 1;
  const to = Math.min(list.total, page * SIZE + list.items.length);

  return (
    <div className="flex flex-col">
      <PageHeader eyebrow={`${me.organization.name} · ${stats.counts.valid.toLocaleString("en-GB")} issued`} title="Credentials">
        <a className="btn btn-secondary" href={`/api/org/credentials/export.csv?${qs}`}>
          <Download size={15} aria-hidden="true" />Export CSV
        </a>
        {me.organization.canIssue && (
          <Link className="btn btn-primary" href="/console/credentials/new"><Plus size={15} strokeWidth={2.4} aria-hidden="true" />Issue credential</Link>
        )}
      </PageHeader>
      <CredentialFilters titles={stats.titles} years={years} />
      {list.items.length > 0 ? (
        <>
          <div className="overflow-x-auto px-5 lg:px-9">
            <table className="table min-w-[860px]">
              <thead>
                <tr><th>Recipient</th><th>Credential</th><th>Credential ID</th><th>Issued</th><th>Status</th><th className="!text-right">Checks</th><th /></tr>
              </thead>
              <tbody>
                {list.items.map((c) => (
                  <tr key={c.id} className="group">
                    <td className="!py-2.5">
                      <Link href={`/console/credentials/${c.id}`} className="block !text-ink no-underline">
                        <div className="font-semibold group-hover:text-seal">{c.recipientName}</div>
                        <div className="text-xs text-neutral-700">{c.recipientEmail ?? c.recipientRef ?? "—"}</div>
                      </Link>
                    </td>
                    <td><div>{c.title}</div><div className="text-xs text-neutral-700">{c.details || c.typeLabel}</div></td>
                    <td className="mono text-[13px]">{c.status === "PENDING" ? <span className="text-neutral-700">{c.id}</span> : c.id}</td>
                    <td className="whitespace-nowrap">{dateShort(c.issuedAt)}</td>
                    <td><StatusTag status={c.status} approval={c.approvalRequired} /></td>
                    <td className="mono text-right">{c.checks}</td>
                    <td className="text-right text-neutral-700"><Link href={`/console/credentials/${c.id}`} aria-label={`Open ${c.recipientName}`} className="!text-neutral-700 no-underline">→</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex items-center gap-2 px-5 pb-8 pt-3.5 text-[13px] text-neutral-800 lg:px-9">
            <span>Showing {from}–{to} of {list.total.toLocaleString("en-GB")}</span>
            <div className="ml-auto flex gap-1.5">
              {page > 0 ? <Link className="btn btn-secondary" href={pageHref(page - 1)}>Previous</Link> : <button className="btn btn-secondary" disabled>Previous</button>}
              {to < list.total ? <Link className="btn btn-secondary" href={pageHref(page + 1)}>Next</Link> : <button className="btn btn-secondary" disabled>Next</button>}
            </div>
          </div>
        </>
      ) : (
        <EmptyState title={filtered ? "No credentials match" : "No credentials yet"}>
          <div className="text-sm text-neutral-800">
            {filtered
              ? <>Nothing matches “{one("q")}” with the current filters. Check the spelling, or search by the credential ID printed under the QR code.</>
              : <>Issued credentials appear here as soon as they are sealed.</>}
          </div>
          <div className="mt-1.5 flex gap-2">
            {filtered && <Link className="btn btn-secondary" href="/console/credentials">Clear filters</Link>}
            {me.organization.canIssue && <Link className="btn btn-ghost" href="/console/credentials/new">Issue a new credential</Link>}
          </div>
        </EmptyState>
      )}
    </div>
  );
}
