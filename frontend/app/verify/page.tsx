import type { Metadata } from "next";
import Link from "next/link";
import { LogoMark } from "@/components/mahtem/logo";
import { VerifyIdForm } from "@/components/mahtem/verify-id-form";

export const metadata: Metadata = { title: "Verify a credential" };

export default function VerifyIndex() {
  return (
    <main className="min-h-screen bg-neutral-300 lg:py-10">
      <div className="mx-auto flex w-full max-w-[480px] flex-col bg-background sm:shadow-lg">
        <div className="flex items-center gap-2.5 border-b-2 border-divider px-5 py-3.5">
          <Link href="/" className="inline-flex items-center gap-2.5 !text-ink no-underline">
            <LogoMark size={24} />
            <span className="text-base font-extrabold tracking-[-0.01em]">Mahtem</span>
          </Link>
          <span className="ml-auto text-xs text-neutral-700">Credential verification</span>
        </div>
        <div className="flex flex-col gap-4 p-5 pb-8">
          <h1 className="m-0 mt-3 text-[28px]">Verify a credential</h1>
          <p className="m-0 text-sm leading-normal text-neutral-800">
            Scan the QR code on the certificate with your phone camera, or type the credential ID printed under it. No account needed.
          </p>
          <div className="bg-surface p-4">
            <VerifyIdForm id="vid" label="Credential ID" cta="Verify" buttonClass="btn btn-primary" inputStyle={{ minHeight: 48, fontSize: 15, background: "var(--bg)" }} />
          </div>
        </div>
      </div>
    </main>
  );
}
