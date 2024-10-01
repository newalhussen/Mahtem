const TZ = "Africa/Addis_Ababa";

function parts(d: Date, opts: Intl.DateTimeFormatOptions) {
  return new Intl.DateTimeFormat("en-GB", { timeZone: TZ, ...opts }).format(d);
}

/** 22 Jul 2026 */
export function dateShort(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso.length === 10 ? `${iso}T12:00:00+03:00` : iso);
  return parts(d, { day: "numeric", month: "short", year: "numeric" });
}

/** 22 July 2026 */
export function dateLong(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso.length === 10 ? `${iso}T12:00:00+03:00` : iso);
  return parts(d, { day: "numeric", month: "long", year: "numeric" });
}

/** 6 Oct 2026, 14:32 EAT */
export function stamp(iso: string): string {
  const d = new Date(iso);
  return `${parts(d, { day: "numeric", month: "short", year: "numeric" })}, ${parts(d, { hour: "2-digit", minute: "2-digit", hour12: false })} EAT`;
}

/** 06 Oct 14:32 */
export function logTime(iso: string): string {
  const d = new Date(iso);
  return `${parts(d, { day: "2-digit", month: "short" })} ${parts(d, { hour: "2-digit", minute: "2-digit", hour12: false })}`;
}

/** 14:32:07 */
export function clock(iso: string): string {
  return parts(new Date(iso), { hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false });
}

/** Tuesday, 6 October 2026 */
export function today(): string {
  return parts(new Date(), { weekday: "long", day: "numeric", month: "long", year: "numeric" });
}

export function ago(iso: string | null | undefined): string {
  if (!iso) return "Never";
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 90) return "Now";
  if (s < 3600) return `${Math.round(s / 60)} min ago`;
  if (s < 86400) return `${Math.round(s / 3600)} h ago`;
  if (s < 86400 * 14) return `${Math.round(s / 86400)} days ago`;
  return dateShort(iso);
}

export function initialsOf(name: string): string {
  const p = name.replace(/^(dr|prof|eng)\.?\s+/i, "").split(/\s+/).filter(Boolean);
  return ((p[0]?.[0] ?? "") + (p.length > 1 ? p[p.length - 1][0] : "")).toUpperCase();
}

export const ROLE_LABEL = { ADMIN: "Admin", APPROVER: "Approver", ISSUER: "Issuer", VIEWER: "Viewer" } as const;
export const METHOD_LABEL = { QR: "QR scan", ID_LOOKUP: "ID lookup", API: "API" } as const;
export const RESULT_LABEL = { VALID: "Verified", REVOKED: "Revoked", INVALID: "Not found" } as const;
