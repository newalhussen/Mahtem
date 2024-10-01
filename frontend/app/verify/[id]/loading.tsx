import { LogoMark } from "@/components/mahtem/logo";

const STEPS = ["Locating record", "Verifying digital signature", "Checking revocation registry", "Confirming issuer identity"];

export default function Loading() {
  return (
    <main className="min-h-screen bg-neutral-300 lg:py-10">
      <div className="mx-auto flex min-h-[560px] w-full max-w-[480px] flex-col bg-background sm:shadow-lg" aria-busy="true">
        <div className="flex items-center gap-2.5 border-b-2 border-divider px-5 py-3.5">
          <LogoMark size={24} />
          <span className="text-base font-extrabold tracking-[-0.01em]">Mahtem</span>
        </div>
        <div className="flex flex-col gap-3.5 bg-surface px-5 py-7" role="status" aria-live="polite">
          <h1 className="m-0 text-[28px] font-extrabold tracking-[-0.02em]">Checking credential…</h1>
          <div className="relative h-1 overflow-hidden bg-neutral-300">
            <div className="sweep absolute inset-y-0 left-0 w-[40%] bg-ink" />
          </div>
        </div>
        <ul className="m-0 flex list-none flex-col px-5 py-2">
          {STEPS.map((s) => (
            <li key={s} className="flex justify-between border-b border-divider py-3 text-sm">
              <span>{s}</span>
              <span className="font-semibold text-neutral-500">…</span>
            </li>
          ))}
        </ul>
        <div className="mt-auto px-5 py-4 text-xs text-neutral-700">Secured connection to Mahtem</div>
      </div>
    </main>
  );
}
