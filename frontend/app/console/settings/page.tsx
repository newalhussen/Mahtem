import type { Metadata } from "next";
import Link from "next/link";
import { AccountPanel } from "@/components/console/settings-account";
import { SettingsOrg } from "@/components/console/settings-org";
import { KeysPanel, TeamPanel } from "@/components/console/settings-team";
import { apiGet, requireUser } from "@/lib/api.server";
import type { KeyRow, Member, OrgProfile, TemplateRow } from "@/lib/types";

export const metadata: Metadata = { title: "Settings" };

const TABS = [
  { id: "org", label: "Organization" },
  { id: "templates", label: "Templates" },
  { id: "keys", label: "Signing keys" },
  { id: "team", label: "Team" },
  { id: "account", label: "Your account" },
] as const;

export default async function SettingsPage({ searchParams }: PageProps<"/console/settings">) {
  const me = await requireUser();
  const sp = await searchParams;
  const requested = typeof sp.tab === "string" ? sp.tab : "org";
  const tab = TABS.some((t) => t.id === requested) ? requested : "org";
  const isAdmin = me.role === "ADMIN";

  let panel: React.ReactNode;
  if (tab === "org") {
    panel = <SettingsOrg profile={await apiGet<OrgProfile>("/api/org/profile")} isAdmin={isAdmin} />;
  } else if (tab === "keys") {
    panel = <KeysPanel keys={await apiGet<KeyRow[]>("/api/org/keys")} isAdmin={isAdmin} />;
  } else if (tab === "team") {
    panel = <TeamPanel members={await apiGet<Member[]>("/api/org/team")} meId={me.id} isAdmin={isAdmin} />;
  } else if (tab === "account") {
    panel = <AccountPanel me={me} />;
  } else {
    const [templates, org] = await Promise.all([apiGet<TemplateRow[]>("/api/org/templates"), apiGet<OrgProfile>("/api/org/profile")]);
    panel = (
      <div className="grid gap-6 px-5 pb-10 pt-6 lg:px-9" style={{ gridTemplateColumns: "repeat(auto-fill,minmax(300px,1fr))" }}>
        {templates.map((t) => (
          <div key={t.type} className="flex flex-col gap-2 border-t-2 border-divider pt-3">
            <div className="relative aspect-[1.414] border border-neutral-300 bg-paper p-3.5">
              <div className="absolute inset-1.5 border-[1.5px] border-seal" />
              <div className="text-[8px] font-semibold uppercase tracking-[0.14em] text-seal">{org.name}</div>
              <div className="mt-[28%] h-2.5 w-3/5 bg-ink" />
              <div className="mt-1.5 h-[5px] w-2/5 bg-seal" />
            </div>
            <div className="flex items-baseline gap-2">
              <span className="text-sm font-semibold">{t.label}</span>
              <span className="ml-auto text-xs text-neutral-700">{t.used === 0 ? "Not used yet" : `Used ${t.used.toLocaleString("en-GB")}×`}</span>
            </div>
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="flex flex-col">
      <div className="flex flex-col gap-4 border-b-2 border-divider px-5 pt-7 lg:px-9">
        <h1 className="m-0 text-[32px]">Settings</h1>
        <nav className="flex gap-6 overflow-x-auto" aria-label="Settings sections">
          {TABS.map((t) => (
            <Link
              key={t.id}
              href={`/console/settings?tab=${t.id}`}
              aria-current={tab === t.id ? "page" : undefined}
              className={`-mb-0.5 whitespace-nowrap border-b-[3px] pb-2.5 text-sm no-underline ${tab === t.id ? "border-seal font-semibold !text-ink" : "border-transparent !text-ink"}`}
            >
              {t.label}
            </Link>
          ))}
        </nav>
      </div>
      {panel}
    </div>
  );
}
