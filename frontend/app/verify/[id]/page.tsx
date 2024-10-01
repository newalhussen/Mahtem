import type { Metadata } from "next";
import { VerificationView } from "@/components/verify/view";
import { ApiFailure, apiGet } from "@/lib/api.server";
import type { VerificationResponse } from "@/lib/types";

// Result pages are never indexed or cached: every view is a live check that is logged for the issuer.
export const metadata: Metadata = { title: "Credential verification", robots: { index: false, follow: false } };
export const dynamic = "force-dynamic";

export default async function VerifyPage({ params, searchParams }: PageProps<"/verify/[id]">) {
  const { id } = await params;
  const sp = await searchParams;
  const via = sp.s === "qr" ? "qr" : "lookup";
  let result: VerificationResponse;
  try {
    result = await apiGet<VerificationResponse>(`/api/public/verify/${encodeURIComponent(decodeURIComponent(id))}?via=${via}`, { auth: false });
  } catch (e) {
    if (e instanceof ApiFailure && e.status === 429) {
      return (
        <main className="mx-auto flex max-w-[480px] flex-col gap-3 px-5 py-16">
          <h1 className="m-0 text-3xl">Too many checks</h1>
          <p className="m-0 text-neutral-800">Please wait a minute and try again. This limit protects issuers from automated guessing.</p>
        </main>
      );
    }
    throw e;
  }
  return (
    <main className="min-h-screen bg-neutral-300 lg:py-10">
      <VerificationView r={result} />
    </main>
  );
}
