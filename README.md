# Mahtem

Secure digital credentials: an organization issues a signed credential with a QR code, anyone scans it and sees
**VERIFIED**, **REVOKED** or **NOT VERIFIED** in their browser. No account or app for the verifier.

```
Next.js (frontend/)  ──/api/* proxy──▶  Spring Boot REST API (backend/)  ──▶  PostgreSQL
```

All business logic, authentication, signing, verification and persistence live in the Spring Boot service. The
Next.js app renders the UI (taken from `Mahtem credential issuance platform/`) and forwards `/api/*` to the API, so the
session cookie is first-party and httpOnly.

## Run it

Requirements: JDK 21+, Node 20+, PostgreSQL 16 (`docker compose up -d` starts one).

```bash
# 1. database
docker compose up -d

# 2. API  (http://localhost:8080)
cd backend
cp .env.example .env        # then fill in the three secrets, see below
./mvnw spring-boot:run

# 3. web  (http://localhost:3000)
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

Secrets for `backend/.env` (generate each with `openssl rand -base64 32`):
`MAHTEM_MASTER_KEY` (exactly 32 bytes, base64), `MAHTEM_JWT_SECRET`, `MAHTEM_IP_SALT`. Set `DB_URL`, `DB_USER`,
`DB_PASSWORD` if you are not using the compose database (defaults: `mahtem` / `mahtem` / `mahtem` on 5432).

With `MAHTEM_SEED_DEMO=true` an empty database is filled with four institutions and ~40 genuinely signed credentials.
Sign in at <http://localhost:3000/login> as `hirut.gebre@aau.edu.et` (Admin), `meron.abebe@aau.edu.et` (Approver),
`bereket.assefa@aau.edu.et` (Issuer) or `selam.h@aau.edu.et` (Viewer); password `Mahtem#2026`.
Try verifying `MHT-AAU-26-K7Q4-8TZ2` (valid) and `MHT-UOG-26-3MPH-41AC` (revoked) at `/verify/<id>`.
**Never enable seeding in production.**

Tests: `./mvnw test` (crypto vectors) and `./mvnw test -Dtest=LifecycleIT` (full lifecycle against a scratch database
`mahtem_test` on `localhost:5433`: edit the URL in the test to match yours).

## What it does

- **Issue**: single or bulk CSV (up to 1,000 rows, validated all-or-nothing), seven credential types, optional second approver,
  drafts. Sealing signs the record and renders the certificate PDF (QR + credential ID) once; it is stored and never regenerated.
- **Verify** (`/verify/{id}`, public, mobile-first): signature, revocation, issuer identity and record integrity are checked
  live. Each check gets a receipt. Visitors can compare a PDF they received against the SHA-256 recorded at issuance,
  computed in the browser.
- **Revoke**: reason, optional note, confirmation by typing the ID's last chunk. Effective immediately for every copy.
- **Console**: overview, credentials, verification log, staff action log (hash-chained), settings, signing keys, team and roles
  (Admin / Approver / Issuer / Viewer), two-step sign-in (TOTP), invitations.

## Security model

- Ed25519 signatures over a canonical (sorted-key) JSON record; SHA-256 record hash and PDF hash. On every verification the
  hash and signature are recomputed *and* the stored columns are compared with the signed payload, so editing the database
  turns a credential INVALID.
- Private signing keys and TOTP secrets are encrypted at rest with AES-256-GCM (master key from the environment, bound to the
  owning organization/user).
- Sessions: JWT in an httpOnly, SameSite=Lax cookie; the user is reloaded from the database on every request; organization is
  taken from the session only (tenant isolation). Origin check on state-changing requests. BCrypt(12), 10-character minimum,
  lockout after repeated failures, rate limits on login and verification.
- Verification logs store a salted hash of the visitor's IP, never the address. The public page shows only what is printed on
  the certificate. Unsealed credentials are indistinguishable from nonexistent ones.
- Issuers must prove control of their domain (DNS TXT `_mahtem.<domain>`) before sealing.

## Known limits (read before production)

- **Keys are not in an HSM.** They are encrypted in the database with a master key from the environment; anyone who controls the
  API process and that key can sign. Moving signing to a KMS/HSM is the main hardening step.
- Email is not wired: `LoggingMailer` logs messages and the console shows invitation links to copy. Plug a real `Mailer`.
- Rate limiting is in-memory (per instance) and trusts `X-Forwarded-For`; run the API only behind a proxy you control and move
  limits to the edge or Redis when scaling out.
- Design features that need extra services were left out rather than faked: institutional SSO, recovery codes, "trust this
  device", crest upload, signed-report export, PNG export.
- Placeholder contact (`hello@mahtem.et`) on the landing page and `verify.mahtem.et` text on the sample certificate need your
  real values; set `MAHTEM_PUBLIC_BASE_URL` so QR codes point at your public site.
- Spring Boot 4 / JDK 27 builds were verified; the bytecode targets Java 21.
