import type { Metadata } from "next";
import Link from "next/link";
import { LogFilters } from "@/components/console/log-filters";
import { Alert, EmptyState, PageHeader } from "@/components/console/ui";
import { apiGet } from "@/lib/api.server";
import { logTime, METHOD_LABEL, RESULT_LABEL } from "@/lib/format";
import type { ActionLog, PageOf, VerificationRow } from "@/lib/types";

export const metadata: Metadata = { title: "Verification log" };

const SIZE = 25;
const RESULT_COLOR = { VALID: "text-ok", REVOKED: "text-seal", INVALID: "text-ink" } as const;
const RESULT_BG = { VALID: "bg-ok", REVOKED: "bg-seal", INVALID: "bg-ink" } as const;

export default async function LogPage({ searchParams }: PageProps<"/console/log">) {
  const sp = await searchParams;
  const one = (k: string) => (typeof sp[k] === "string" ? (sp[k] as string) : "");
  const actions = one("tab") === "actions";
  const page = Math.max(0, Number(one("page")) || 0);
  const base = new URLSearchParams();
  for (const k of ["tab", "result", "id", "days"]) if (one(k)) base.set(k, one(k));
  const href = (p: number) => `/console/log?${new URLSearchParams([...base.entries(), ["page", String(p)]])}`;

  let body: React.ReactNode;
  if (actions) {
    const log = await apiGet<ActionLog>(`/api/org/log/actions?page=${page}&size=${SIZE}`);
    const to = Math.min(log.total, page * SIZE + log.items.length);
    body = (
      <>
        <div className="px-5 pt-4 lg:px-9">
          {log.chainIntact ? (
            <Alert tone="ok">
              <b>Log chain intact.</b> All {log.chainEntries.toLocaleString("en-GB")} entries link to the one before them; any edit or deletion would break the chain.
            </Alert>
          ) : (
            <Alert>
              <b>Log chain broken at entry {log.brokenAtSeq}.</b> Something in the staff action log was changed after it was written. Contact Mahtem support.
            </Alert>
          )}
        </div>
        <div className="overflow-x-auto px-5 pb-8 lg:px-9">
          <table className="table min-w-[760px]">
            <thead><tr><th>Time (EAT)</th><th>Action</th><th>Detail</th><th>Staff</th><th>IP</th></tr></thead>
            <tbody>
              {log.items.map((v) => (
                <tr key={v.seq}>
                  <td className="mono whitespace-nowrap text-[13px]">{logTime(v.at)}</td>
                  <td className="font-semibold">{v.action}</td>
                  <td>{v.detail}</td>
                  <td>{v.staff}</td>
                  <td className="mono text-xs text-neutral-700">{v.ip ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pager page={page} to={to} total={log.total} href={href} />
      </>
    );
  } else {
    const qs = new URLSearchParams();
    if (one("result")) qs.set("result", one("result"));
    if (one("id")) qs.set("id", one("id"));
    if (one("days")) qs.set("days", one("days"));
    const log = await apiGet<PageOf<VerificationRow>>(`/api/org/log/verifications?${qs}&page=${page}&size=${SIZE}`);
    const to = Math.min(log.total, page * SIZE + log.items.length);
    body =
      log.items.length === 0 ? (
        <EmptyState title="No verifications match">
          <div className="text-sm text-neutral-800">Nothing in this period matches the filters. Checks appear here the moment someone scans or looks up one of your credentials.</div>
          <div><Link className="btn btn-secondary" href="/console/log">Clear filters</Link></div>
        </EmptyState>
      ) : (
        <>
          <div className="overflow-x-auto px-5 lg:px-9">
            <table className="table min-w-[860px]">
              <thead><tr><th>Time (EAT)</th><th>Result</th><th>Credential ID</th><th>Recipient</th><th>Method</th><th>Location</th><th>Receipt</th></tr></thead>
              <tbody>
                {log.items.map((v) => (
                  <tr key={v.receipt}>
                    <td className="mono whitespace-nowrap text-[13px]">{logTime(v.at)}</td>
                    <td>
                      <span className={`inline-flex items-center gap-1.5 font-semibold ${RESULT_COLOR[v.result]}`}>
                        <span className={`size-2 ${RESULT_BG[v.result]}`} />{RESULT_LABEL[v.result]}
                      </span>
                    </td>
                    <td className="mono text-[13px]">{v.credentialId}</td>
                    <td>{v.recipient ?? "—"}</td>
                    <td>{METHOD_LABEL[v.method]}</td>
                    <td>{v.location ?? "—"}</td>
                    <td className="mono text-xs text-neutral-700">{v.receipt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager page={page} to={to} total={log.total} href={href} />
        </>
      );
  }

  return (
    <div className="flex flex-col">
      <PageHeader eyebrow="Every check and every staff action" title="Verification log" />
      <LogFilters />
      {body}
    </div>
  );
}

function Pager({ page, to, total, href }: { page: number; to: number; total: number; href: (p: number) => string }) {
  const from = total === 0 ? 0 : page * SIZE + 1;
  return (
    <div className="flex items-center gap-2 px-5 pb-8 pt-1 text-[13px] text-neutral-800 lg:px-9">
      <span>Showing {from}–{to} of {total.toLocaleString("en-GB")}</span>
      <div className="ml-auto flex gap-1.5">
        {page > 0 ? <Link className="btn btn-secondary" href={href(page - 1)}>Previous</Link> : <button className="btn btn-secondary" disabled>Previous</button>}
        {to < total ? <Link className="btn btn-secondary" href={href(page + 1)}>Next</Link> : <button className="btn btn-secondary" disabled>Next</button>}
      </div>
    </div>
  );
}
