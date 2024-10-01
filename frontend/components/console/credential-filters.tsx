"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, useTransition } from "react";
import { Search } from "lucide-react";

const STATUSES = [
  { value: "", label: "All" },
  { value: "VALID", label: "Valid" },
  { value: "PENDING", label: "Pending" },
  { value: "REVOKED", label: "Revoked" },
];

/** Search, status, programme and year filters. They live in the URL so a filtered list can be bookmarked or shared. */
export function CredentialFilters({ titles, years }: { titles: string[]; years: number[] }) {
  const router = useRouter();
  const pathname = usePathname();
  const sp = useSearchParams();
  const [, startTransition] = useTransition();
  const [q, setQ] = useState(sp.get("q") ?? "");
  const status = sp.get("status") ?? "";

  function push(next: Record<string, string>) {
    const p = new URLSearchParams(sp.toString());
    for (const [k, v] of Object.entries(next)) {
      if (v) p.set(k, v);
      else p.delete(k);
    }
    p.delete("page");
    startTransition(() => router.replace(`${pathname}${p.toString() ? `?${p}` : ""}`));
  }

  useEffect(() => {
    const t = setTimeout(() => {
      if ((sp.get("q") ?? "") !== q.trim()) push({ q: q.trim() });
    }, 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q]);

  return (
    <div className="flex flex-wrap items-center gap-3 border-b border-divider px-5 py-4 lg:px-9">
      <div className="relative min-w-[260px] max-w-[420px] flex-1">
        <Search size={16} aria-hidden="true" className="absolute left-2.5 top-2.5 text-neutral-700" />
        <input
          className="input !pl-[34px]"
          placeholder="Search name, credential ID or student ID"
          aria-label="Search credentials"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </div>
      <div className="seg" role="group" aria-label="Status">
        {STATUSES.map((s) => (
          <button key={s.value} type="button" aria-pressed={status === s.value} onClick={() => push({ status: s.value })}>
            {s.label}
          </button>
        ))}
      </div>
      <select className="input !w-auto min-w-[200px]" aria-label="Programme" value={sp.get("title") ?? ""} onChange={(e) => push({ title: e.target.value })}>
        <option value="">All programmes</option>
        {titles.map((t) => (
          <option key={t} value={t}>{t}</option>
        ))}
      </select>
      <select className="input !w-auto" aria-label="Year" value={sp.get("year") ?? ""} onChange={(e) => push({ year: e.target.value })}>
        <option value="">All years</option>
        {years.map((y) => (
          <option key={y} value={y}>Class of {y}</option>
        ))}
      </select>
    </div>
  );
}
