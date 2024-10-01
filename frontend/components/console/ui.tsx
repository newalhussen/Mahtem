import type { CredentialStatus } from "@/lib/types";

export function PageHeader({ eyebrow, title, children, titleAside }: { eyebrow?: React.ReactNode; title: React.ReactNode; children?: React.ReactNode; titleAside?: React.ReactNode }) {
  return (
    <div className="flex flex-wrap items-end gap-3 border-b-2 border-divider px-5 pb-5 pt-7 lg:px-9">
      <div className="min-w-[240px] flex-1">
        {eyebrow && <div className="text-xs text-neutral-700">{eyebrow}</div>}
        <div className="mt-0.5 flex flex-wrap items-center gap-3">
          <h1 className="m-0 text-[32px]">{title}</h1>
          {titleAside}
        </div>
      </div>
      {children}
    </div>
  );
}

const STATUS: Record<CredentialStatus, { label: string; cls: string }> = {
  VALID: { label: "Valid", cls: "tag-ok" },
  REVOKED: { label: "Revoked", cls: "tag-revoked" },
  PENDING: { label: "Pending signature", cls: "tag-neutral" },
};

export function StatusTag({ status, approval, large }: { status: CredentialStatus; approval?: boolean; large?: boolean }) {
  const s = STATUS[status];
  const label = status === "PENDING" ? (approval ? "Awaiting approval" : "Draft") : s.label;
  return <span className={`tag ${s.cls} ${large ? "!text-xs" : ""}`}>{label}</span>;
}

export function EmptyState({ title, children }: { title: string; children?: React.ReactNode }) {
  return (
    <div className="mx-5 my-10 flex max-w-[560px] flex-col gap-2.5 border-2 border-dashed border-divider p-10 lg:mx-9">
      <div className="text-xl font-extrabold">{title}</div>
      {children}
    </div>
  );
}

export function Alert({ children, tone = "error" }: { children: React.ReactNode; tone?: "error" | "ok" }) {
  return (
    <div role={tone === "error" ? "alert" : "status"} className={`border-2 p-3 text-sm ${tone === "error" ? "border-seal bg-accent-100 text-accent-900" : "border-ok bg-ok-tint text-ok-ink"}`}>
      {children}
    </div>
  );
}
