import type { Metadata } from "next";
import { Sidebar } from "@/components/console/sidebar";
import { ToastProvider } from "@/components/console/toast";
import { apiGet, requireUser } from "@/lib/api.server";
import type { CredentialSummary, PageOf } from "@/lib/types";

export const metadata: Metadata = { title: { default: "Issuer console", template: "%s · Mahtem console" }, robots: { index: false, follow: false } };
export const dynamic = "force-dynamic";

export default async function ConsoleLayout({ children }: LayoutProps<"/console">) {
  const me = await requireUser();
  const first = await apiGet<PageOf<CredentialSummary>>("/api/org/credentials?size=1");
  return (
    <ToastProvider>
      <div className="grid min-h-screen lg:grid-cols-[240px_minmax(0,1fr)]">
        <Sidebar me={me} count={first.total} />
        <main className="flex min-w-0 flex-col">{children}</main>
      </div>
    </ToastProvider>
  );
}
