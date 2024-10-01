"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Activity, FilePlus2, FileText, KeyRound, LayoutGrid, LogOut, SlidersHorizontal } from "lucide-react";
import { LogoMark } from "@/components/mahtem/logo";
import { api } from "@/lib/api.client";
import { initialsOf, ROLE_LABEL } from "@/lib/format";
import type { Me } from "@/lib/types";

const NAV = [
  { href: "/console", label: "Overview", icon: LayoutGrid, match: (p: string) => p === "/console" },
  { href: "/console/credentials", label: "Credentials", icon: FileText, match: (p: string) => p.startsWith("/console/credentials") && p !== "/console/credentials/new" },
  { href: "/console/credentials/new", label: "Issue credential", icon: FilePlus2, match: (p: string) => p === "/console/credentials/new", issuerOnly: true },
  { href: "/console/log", label: "Verification log", icon: Activity, match: (p: string) => p.startsWith("/console/log") },
  { href: "/console/settings", label: "Settings", icon: SlidersHorizontal, match: (p: string) => p.startsWith("/console/settings") },
];

function keyNote(me: Me): string {
  const o = me.organization;
  if (!o.activeKeyFingerprint) return "No active signing key";
  const short = o.activeKeyFingerprint.split(" ");
  const fp = short.length > 2 ? `${short[0]}…${short[short.length - 1]}` : o.activeKeyFingerprint;
  return `${o.activeKeyLabel ?? "Signing key"} ${fp}`;
}

export function Sidebar({ me, count }: { me: Me; count: number }) {
  const pathname = usePathname();
  const router = useRouter();
  const org = me.organization;
  const items = NAV.filter((n) => !n.issuerOnly || org.canIssue);

  async function signOut() {
    try {
      await api("/auth/logout", { method: "POST" });
    } finally {
      router.replace("/login");
      router.refresh();
    }
  }

  return (
    <aside className="no-print flex flex-col border-b-2 border-divider lg:sticky lg:top-0 lg:h-screen lg:border-b-0 lg:border-r-2">
      <div className="flex items-center gap-2.5 border-b-2 border-divider px-[18px] py-4">
        <Link href="/console" className="inline-flex items-center gap-2.5 !text-ink no-underline">
          <LogoMark size={26} />
          <span className="text-[17px] font-extrabold">Mahtem</span>
        </Link>
        <button type="button" className="btn btn-ghost btn-icon ml-auto !text-ink lg:hidden" onClick={signOut} aria-label="Sign out">
          <LogOut size={16} />
        </button>
      </div>
      <div className="hidden items-center gap-2.5 border-b border-divider px-[18px] py-3.5 lg:flex">
        <div className="grid size-8 flex-none place-items-center border-[1.5px] border-ink text-[10px] font-extrabold">{org.initials}</div>
        <div className="flex min-w-0 flex-1 flex-col">
          <span className="truncate text-[13px] font-semibold">{org.name}</span>
          {org.department && <span className="truncate text-[11px] text-neutral-700">{org.department}</span>}
        </div>
      </div>
      <nav aria-label="Console" className="flex gap-0.5 overflow-x-auto px-2.5 py-2 lg:flex-col lg:py-3">
        {items.map((n) => {
          const active = n.match(pathname);
          const Icon = n.icon;
          return (
            <Link
              key={n.href}
              href={n.href}
              aria-current={active ? "page" : undefined}
              className={`flex flex-none items-center gap-2.5 whitespace-nowrap px-2.5 py-[9px] text-sm no-underline hover:bg-surface ${active ? "bg-surface font-semibold !text-seal" : "!text-ink"}`}
            >
              <Icon size={16} aria-hidden="true" />
              {n.label}
              {n.label === "Credentials" && <span className="ml-auto pl-2 text-[11px] font-normal text-neutral-700">{count.toLocaleString("en-GB")}</span>}
            </Link>
          );
        })}
      </nav>
      <div className="mx-2.5 mb-2.5 mt-auto hidden flex-col gap-1 bg-surface p-3 text-xs lg:flex">
        <div className="flex items-center gap-1.5 font-semibold">
          <KeyRound size={13} aria-hidden="true" />
          {org.activeKeyFingerprint ? "Signing key active" : "No signing key"}
        </div>
        <div className="text-neutral-700">{keyNote(me)}</div>
      </div>
      <div className="hidden items-center gap-2.5 border-t-2 border-divider px-[18px] py-3 lg:flex">
        <div className="grid size-[30px] flex-none place-items-center bg-ink text-[11px] font-semibold text-background">{initialsOf(me.name)}</div>
        <div className="flex min-w-0 flex-1 flex-col">
          <span className="truncate text-[13px] font-semibold">{me.name}</span>
          <span className="text-[11px] text-neutral-700">{ROLE_LABEL[me.role]}</span>
        </div>
        <button type="button" className="btn btn-ghost btn-icon !text-ink" onClick={signOut} aria-label="Sign out">
          <LogOut size={16} />
        </button>
      </div>
    </aside>
  );
}
