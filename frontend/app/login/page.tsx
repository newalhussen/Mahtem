import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { LoginForm } from "@/components/auth/login-form";
import { LogoMark } from "@/components/mahtem/logo";
import { apiGet, currentUser } from "@/lib/api.server";
import { clock, RESULT_LABEL } from "@/lib/format";
import type { LedgerEntry } from "@/lib/types";

export const metadata: Metadata = { title: "Sign in" };
export const dynamic = "force-dynamic";

export default async function LoginPage() {
  if (await currentUser()) redirect("/console");
  let ledger: LedgerEntry[] = [];
  try {
    ledger = (await apiGet<LedgerEntry[]>("/api/public/ledger", { auth: false })).slice(0, 5);
  } catch {
    /* the ledger is decoration; sign-in must work without it */
  }
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <div className="flex flex-col px-6 py-8 lg:px-12">
        <div className="flex items-center gap-2.5">
          <Link href="/" className="inline-flex items-center gap-2.5 !text-ink no-underline">
            <LogoMark size={28} />
            <span className="text-lg font-extrabold">Mahtem</span>
          </Link>
          <span className="border-l border-divider pl-2.5 text-[13px] text-neutral-700">Issuer console</span>
        </div>
        <main className="my-auto flex w-full max-w-[400px] flex-col gap-5 py-12">
          <LoginForm />
        </main>
        <div className="flex gap-4 text-xs text-neutral-700">
          <span>© 2026 Mahtem</span>
          <Link href="/#sec">Security</Link>
        </div>
      </div>
      <aside className="hidden flex-col justify-end gap-7 bg-accent-900 p-12 text-[#f8f4f4] lg:flex" aria-label="Live verification ledger">
        <div className="text-[11px] uppercase tracking-[0.14em] opacity-80">Live verification ledger</div>
        <div className="flex flex-col border-t-2 border-[rgba(248,244,244,.5)]">
          {ledger.length === 0 && <div className="py-3 text-[13px] opacity-75">Checks will appear here as employers verify credentials.</div>}
          {ledger.map((l, i) => (
            <div key={i} className="grid grid-cols-[72px_1fr_auto] gap-3 border-b border-[rgba(248,244,244,.25)] py-3 text-[13px]">
              <span className="mono opacity-75">{clock(l.at)}</span>
              <span className="mono">{l.id}</span>
              <span className="font-semibold">{RESULT_LABEL[l.result] === "Not found" ? "Not found" : RESULT_LABEL[l.result]}</span>
            </div>
          ))}
        </div>
        <div className="max-w-[460px] text-[30px] font-extrabold leading-[1.15] tracking-[-0.015em]">
          Every credential you issue carries your institution’s seal, and can be checked by anyone in seconds.
        </div>
      </aside>
    </div>
  );
}
