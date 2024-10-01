/** Shapes returned by the Mahtem Spring Boot API (see backend web/dto). */

export type CredentialType = "DEGREE" | "DIPLOMA" | "TRAINING" | "CERTIFICATION" | "EMPLOYMENT" | "AWARD" | "LICENSE";
export type CredentialStatus = "PENDING" | "VALID" | "REVOKED";
export type VerificationResult = "VALID" | "REVOKED" | "INVALID";
export type VerifyMethod = "QR" | "ID_LOOKUP" | "API";
export type Role = "ADMIN" | "APPROVER" | "ISSUER" | "VIEWER";
export type IssueMode = "SEAL" | "DRAFT" | "REQUIRE_APPROVAL";

export const CREDENTIAL_TYPES: { value: CredentialType; label: string; dateLabel: string; detailsLabel: string; detailsHint: string }[] = [
  { value: "DEGREE", label: "Degree", dateLabel: "Date conferred", detailsLabel: "Honors / classification", detailsHint: "e.g. With Great Distinction · CGPA 3.81" },
  { value: "DIPLOMA", label: "Diploma", dateLabel: "Date conferred", detailsLabel: "Honors / classification", detailsHint: "e.g. With Distinction" },
  { value: "TRAINING", label: "Training certificate", dateLabel: "Date completed", detailsLabel: "Hours and grade", detailsHint: "e.g. 120 contact hours · Grade A" },
  { value: "CERTIFICATION", label: "Professional certification", dateLabel: "Date certified", detailsLabel: "Level or validity", detailsHint: "e.g. Level 2 · valid for 3 years" },
  { value: "EMPLOYMENT", label: "Employment credential", dateLabel: "Start date", detailsLabel: "Terms", detailsHint: "e.g. Full-time · Since 2022" },
  { value: "AWARD", label: "Award", dateLabel: "Date awarded", detailsLabel: "Citation", detailsHint: "e.g. Zero incidents across 210,000 km" },
  { value: "LICENSE", label: "License", dateLabel: "Date licensed", detailsLabel: "Class or scope", detailsHint: "e.g. Class C · heavy vehicles" },
];

export type Check = {
  key: string;
  label: string;
  state: "PASS" | "FAIL" | "WARN";
  value: string;
  note: string;
};

export type PublicCredential = {
  id: string;
  type: CredentialType;
  typeLabel: string;
  title: string;
  details: string | null;
  recipientName: string;
  recipientNameAm: string | null;
  conferredOn: string;
  dateLabel: string;
  issuedAt: string;
};

export type PublicIssuer = {
  name: string;
  nameAm: string | null;
  initials: string;
  department: string | null;
  domain: string;
  domainVerified: boolean;
  contact: string | null;
};

export type VerificationResponse = {
  status: VerificationResult;
  checkedAt: string;
  receipt: string;
  queriedId: string;
  credential: PublicCredential | null;
  issuer: PublicIssuer | null;
  checks: Check[];
  revocation: { revokedAt: string; reason: string | null; note: string | null; revokedBy: string | null } | null;
  document: { downloadable: boolean; sha256: string | null } | null;
  invalidCause: "NOT_FOUND" | "TAMPERED" | null;
};

export type LedgerEntry = { at: string; id: string; result: VerificationResult };

export type OrgSummary = {
  code: string;
  name: string;
  initials: string;
  department: string | null;
  domain: string;
  domainVerified: boolean;
  activeKeyFingerprint: string | null;
  activeKeyLabel: string | null;
  activeKeyValidTo: string | null;
  canIssue: boolean;
  canApprove: boolean;
};

export type Me = { id: string; name: string; email: string; role: Role; twoFactor: boolean; organization: OrgSummary };

export type Kpi = { label: string; value: string; note: string };
export type CredentialSummary = {
  id: string;
  type: CredentialType;
  typeLabel: string;
  title: string;
  details: string | null;
  recipientName: string;
  recipientNameAm: string | null;
  recipientEmail: string | null;
  recipientRef: string | null;
  conferredOn: string;
  issuedAt: string | null;
  status: CredentialStatus;
  approvalRequired: boolean;
  checks: number;
  batchId: string | null;
};
export type Overview = {
  kpis: Kpi[];
  verifications: { day: string; ok: number; bad: number }[];
  checks30d: number;
  verifiedPct: number;
  attention: { title: string; detail: string; cta: string; href: string; tone: "alert" | "neutral" }[];
  recent: CredentialSummary[];
};
export type PageOf<T> = { items: T[]; total: number; page: number; size: number };
export type Proof = {
  signature: string | null;
  keyLabel: string | null;
  keyFingerprint: string | null;
  recordHash: string | null;
  pdfSha256: string | null;
  sealedAt: string | null;
  sealedBy: string | null;
  algorithm: string;
};
export type CredentialDetail = {
  summary: CredentialSummary;
  statement: string | null;
  dateLabel: string;
  createdBy: string;
  proof: Proof;
  revocation: { revokedAt: string; reason: string; note: string | null; revokedBy: string } | null;
  history: { title: string; detail: string; at: string; kind: string }[];
  verifyUrl: string;
  canSeal: boolean;
  canRevoke: boolean;
};
export type VerificationRow = {
  at: string;
  result: VerificationResult;
  credentialId: string;
  recipient: string | null;
  method: VerifyMethod;
  location: string | null;
  receipt: string;
};
export type ActionLog = {
  items: { seq: number; at: string; action: string; detail: string; staff: string; ip: string | null }[];
  total: number;
  page: number;
  size: number;
  chainIntact: boolean;
  brokenAtSeq: number | null;
  chainEntries: number;
};
export type OrgProfile = {
  code: string;
  name: string;
  nameAm: string | null;
  initials: string;
  department: string | null;
  accreditationBody: string | null;
  country: string;
  verifierContact: string | null;
  domain: string;
  domainVerified: boolean;
  domainVerifiedAt: string | null;
  domainTxtName: string;
  domainTxtValue: string;
  showDetailsPublicly: boolean;
  showRevocationReason: boolean;
  allowPdfDownload: boolean;
  signatories: { name: string; title: string }[];
};
export type KeyRow = { id: string; label: string; fingerprint: string; algorithm: string; validFrom: string; validTo: string | null; status: string };
export type Member = { id: string; name: string; email: string; role: Role; status: string; twoFactor: boolean; lastActive: string | null };
export type TemplateRow = { type: CredentialType; label: string; used: number };
export type BatchRow = { id: string; filename: string; rows: number; sealed: number; status: string; uploadedBy: string; uploadedAt: string };
export type BatchPreview = { batch: BatchRow | null; errors: { row: number; field: string; message: string }[]; validRows: number };
export type StatusCounts = { total: number; valid: number; pending: number; revoked: number };
