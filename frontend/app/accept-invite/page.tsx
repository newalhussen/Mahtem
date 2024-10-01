import type { Metadata } from "next";
import Link from "next/link";
import { AcceptInviteForm } from "@/components/auth/accept-invite-form";
import { LogoMark } from "@/components/mahtem/logo";

export const metadata: Metadata = { title: "Join your team", robots: { index: false } };

export default async function AcceptInvite({ searchParams }: PageProps<"/accept-invite">) {
  const sp = await searchParams;
  const token = typeof sp.token === "string" ? sp.token : "";
  return (
    <div className="flex min-h-screen flex-col px-6 py-8 lg:px-12">
      <Link href="/" className="inline-flex items-center gap-2.5 !text-ink no-underline">
        <LogoMark size={28} />
        <span className="text-lg font-extrabold">Mahtem</span>
      </Link>
      <main className="my-auto flex w-full max-w-[400px] flex-col gap-5 py-12">
        <AcceptInviteForm token={token} />
      </main>
    </div>
  );
}
