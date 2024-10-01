"use client";

import Link from "next/link";
import { LogoMark } from "@/components/mahtem/logo";

/** If the check itself fails, say so plainly. This page must never look like a verification result. */
export default function VerifyError({ reset }: { error: Error; reset: () => void }) {
  return (
    <main className="min-h-screen bg-neutral-300 lg:py-10">
      <div className="mx-auto flex w-full max-w-[480px] flex-col bg-background sm:shadow-lg">
        <div className="flex items-center gap-2.5 border-b-2 border-divider px-5 py-3.5">
          <LogoMark size={24} />
          <span className="text-base font-extrabold tracking-[-0.01em]">Mahtem</span>
        </div>
        <div className="flex flex-col gap-3 border-b-[6px] border-b-neutral-500 bg-neutral-700 px-5 pb-6 pt-7 text-[#f8f4f4]">
          <h1 className="m-0 text-[28px] font-extrabold tracking-[-0.02em]">We couldn’t complete the check</h1>
          <p className="m-0 text-base font-semibold leading-[1.4]">This is a problem on our side. It does not mean the credential is genuine or fake.</p>
        </div>
        <div className="flex flex-col gap-3 p-5">
          <button type="button" className="btn btn-primary !min-h-12 !px-4 !text-[15px]" onClick={reset}>Try again</button>
          <Link className="btn btn-secondary !min-h-12 !px-4 !text-[15px]" href="/">Back to Mahtem</Link>
        </div>
      </div>
    </main>
  );
}
