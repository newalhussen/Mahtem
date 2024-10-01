import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { IssueForm } from "@/components/console/issue-form";
import { Alert } from "@/components/console/ui";
import { apiGet, requireUser } from "@/lib/api.server";
import type { OrgProfile } from "@/lib/types";

export const metadata: Metadata = { title: "Issue credential" };

export default async function NewCredentialPage() {
  const me = await requireUser();
  if (!me.organization.canIssue) redirect("/console/credentials");
  const org = await apiGet<OrgProfile>("/api/org/profile");
  return (
    <div className="flex flex-col">
      {!org.domainVerified && (
        <div className="mx-5 mt-5 lg:mx-9">
          <Alert>
            Your domain <b>{org.domain}</b> isn’t verified yet, so credentials can only be saved as drafts. <Link href="/console/settings?tab=org">Verify your domain</Link> to start sealing.
          </Alert>
        </div>
      )}
      <IssueForm me={me} org={org} />
    </div>
  );
}
