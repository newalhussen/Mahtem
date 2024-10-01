"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, useTransition } from "react";

export function LogFilters() {
  const router = useRouter();
  const pathname = usePathname();
  const sp = useSearchParams();
  const [, start] = useTransition();
  const tab = sp.get("tab") === "actions" ? "actions" : "verifications";
  const [id, setId] = useState(sp.get("id") ?? "");

  function push(next: Record<string, string>) {
    const p = new URLSearchParams(sp.toString());
    for (const [k, v] of Object.entries(next)) {
      if (v) p.set(k, v);
      else p.delete(k);
    }
    p.delete("page");
    start(() => router.replace(`${pathname}${p.toString() ? `?${p}` : ""}`));
  }

  useEffect(() => {
    const t = setTimeout(() => {
      if ((sp.get("id") ?? "") !== id.trim()) push({ id: id.trim().toUpperCase() });
    }, 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  return (
    <div className="flex flex-wrap items-center gap-3 border-b border-divider px-5 py-4 lg:px-9">
      <div className="seg" role="group" aria-label="Log type">
        <button type="button" aria-pressed={tab === "verifications"} onClick={() => push({ tab: "", result: "", id: "" })} style={{ padding: "7px 14px" }}>Verifications</button>
        <button type="button" aria-pressed={tab === "actions"} onClick={() => push({ tab: "actions", result: "", id: "" })} style={{ padding: "7px 14px" }}>Staff actions</button>
      </div>
      {tab === "verifications" && (
        <>
          <input className="input mono !max-w-[280px]" placeholder="Filter by credential ID" aria-label="Filter by credential ID" value={id} onChange={(e) => setId(e.target.value)} />
          <select className="input !w-auto" aria-label="Result" value={sp.get("result") ?? ""} onChange={(e) => push({ result: e.target.value })}>
            <option value="">All results</option>
            <option value="VALID">Verified</option>
            <option value="REVOKED">Revoked</option>
            <option value="INVALID">Not found</option>
          </select>
          <select className="input !w-auto" aria-label="Period" value={sp.get("days") ?? "30"} onChange={(e) => push({ days: e.target.value === "30" ? "" : e.target.value })}>
            <option value="7">Last 7 days</option>
            <option value="30">Last 30 days</option>
            <option value="90">Last 90 days</option>
            <option value="365">Last 12 months</option>
          </select>
        </>
      )}
    </div>
  );
}
