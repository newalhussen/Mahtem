-- Mahtem schema. Every tenant-owned table carries organization_id; the API always filters by the
-- organization in the caller's token and never trusts an organization id sent by the client.

CREATE TABLE organization (
    id                     UUID PRIMARY KEY,
    code                   VARCHAR(8)   NOT NULL UNIQUE,           -- short issuer code used in credential IDs, e.g. AAU
    name                   VARCHAR(200) NOT NULL,
    name_am                VARCHAR(200),
    initials               VARCHAR(8)   NOT NULL,
    department             VARCHAR(200),                           -- college / faculty / unit printed under the name
    accreditation_body     VARCHAR(200),
    country                VARCHAR(80)  NOT NULL DEFAULT 'Ethiopia',
    verifier_contact       VARCHAR(200),
    domain                 VARCHAR(200) NOT NULL,
    domain_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    domain_verified_at     TIMESTAMPTZ,
    domain_token           VARCHAR(64)  NOT NULL,
    signatories            TEXT         NOT NULL DEFAULT '[]',          -- JSON [{name, title}] printed on certificates
    show_details_publicly  BOOLEAN      NOT NULL DEFAULT TRUE,    -- honors, grades
    show_revocation_reason BOOLEAN      NOT NULL DEFAULT TRUE,
    allow_pdf_download     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    email            VARCHAR(254) NOT NULL,
    full_name        VARCHAR(160) NOT NULL,
    password_hash    VARCHAR(100),
    role             VARCHAR(16)  NOT NULL CHECK (role IN ('ADMIN', 'APPROVER', 'ISSUER', 'VIEWER')),
    status           VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'INVITED', 'DISABLED')),
    invite_token_hash VARCHAR(64),
    invite_expires_at TIMESTAMPTZ,
    totp_secret_enc  BYTEA,
    totp_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_attempts  INT          NOT NULL DEFAULT 0,
    locked_until     TIMESTAMPTZ,
    last_login_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_app_user_email ON app_user (lower(email));
CREATE INDEX ix_app_user_org ON app_user (organization_id);

CREATE TABLE signing_key (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    label            VARCHAR(120) NOT NULL,
    algorithm        VARCHAR(16)  NOT NULL DEFAULT 'Ed25519',
    public_key       BYTEA        NOT NULL,                       -- X.509 SubjectPublicKeyInfo
    private_key_enc  BYTEA        NOT NULL,                       -- AES-256-GCM(PKCS#8), see KeyVault
    fingerprint      VARCHAR(64)  NOT NULL,                       -- SHA-256 of public_key, hex
    status           VARCHAR(8)   NOT NULL CHECK (status IN ('ACTIVE', 'RETIRED')),
    valid_from       DATE         NOT NULL,
    valid_to         DATE         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Exactly one active key per organization.
CREATE UNIQUE INDEX uq_signing_key_active ON signing_key (organization_id) WHERE status = 'ACTIVE';

CREATE TABLE credential_batch (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    filename         VARCHAR(255) NOT NULL,
    total_rows       INT          NOT NULL,
    sealed_rows      INT          NOT NULL DEFAULT 0,
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('AWAITING_APPROVAL', 'SEALING', 'SEALED', 'FAILED')),
    created_by       UUID         NOT NULL REFERENCES app_user (id),
    approved_by      UUID         REFERENCES app_user (id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sealed_at        TIMESTAMPTZ
);
CREATE INDEX ix_batch_org ON credential_batch (organization_id, created_at DESC);

CREATE TABLE credential (
    id                  UUID PRIMARY KEY,
    public_id           VARCHAR(40)  NOT NULL UNIQUE,             -- MHT-AAU-26-K7Q4-8TZ2, printed under the QR code
    organization_id     UUID         NOT NULL REFERENCES organization (id),
    type                VARCHAR(16)  NOT NULL CHECK (type IN ('DEGREE', 'DIPLOMA', 'TRAINING', 'CERTIFICATION', 'EMPLOYMENT', 'AWARD', 'LICENSE')),
    title               VARCHAR(240) NOT NULL,
    details             VARCHAR(240),                              -- honors, grade, hours, position, license class
    statement           VARCHAR(300),                              -- optional custom wording printed on the certificate
    recipient_name      VARCHAR(160) NOT NULL,
    recipient_name_am   VARCHAR(160),
    recipient_email     VARCHAR(254),
    recipient_ref       VARCHAR(80),                               -- student / employee / licence number
    conferred_on        DATE         NOT NULL,
    status              VARCHAR(8)   NOT NULL CHECK (status IN ('PENDING', 'VALID', 'REVOKED')),
    approval_required   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by          UUID         NOT NULL REFERENCES app_user (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    batch_id            UUID         REFERENCES credential_batch (id),
    -- Set when sealed:
    issued_at           TIMESTAMPTZ,
    sealed_by           UUID         REFERENCES app_user (id),
    signing_key_id      UUID         REFERENCES signing_key (id),
    payload             TEXT,                                      -- canonical JSON that was signed
    record_hash         VARCHAR(64),                               -- SHA-256(payload), hex
    signature           BYTEA,                                     -- Ed25519(payload)
    pdf_sha256          VARCHAR(64),                               -- SHA-256 of the issued PDF, recorded at sealing
    -- Set when revoked:
    revoked_at          TIMESTAMPTZ,
    revoke_reason       VARCHAR(120),
    revoke_note         VARCHAR(500),
    revoked_by          UUID         REFERENCES app_user (id),
    CONSTRAINT ck_sealed_fields CHECK (
        status = 'PENDING' OR (payload IS NOT NULL AND record_hash IS NOT NULL AND signature IS NOT NULL AND signing_key_id IS NOT NULL AND issued_at IS NOT NULL)
    )
);
CREATE INDEX ix_credential_org_created ON credential (organization_id, created_at DESC);
CREATE INDEX ix_credential_org_status ON credential (organization_id, status);
CREATE INDEX ix_credential_batch ON credential (batch_id);

-- The issued PDF is kept apart from the credential row so list queries never load it.
CREATE TABLE credential_document (
    credential_id  UUID PRIMARY KEY REFERENCES credential (id),
    pdf            BYTEA NOT NULL
);

CREATE TABLE verification_log (
    id               UUID PRIMARY KEY,
    credential_id    UUID REFERENCES credential (id),
    organization_id  UUID REFERENCES organization (id),           -- issuer, when the ID matched a credential
    queried_id       VARCHAR(60)  NOT NULL,
    result           VARCHAR(8)   NOT NULL CHECK (result IN ('VALID', 'REVOKED', 'INVALID')),
    method           VARCHAR(12)  NOT NULL CHECK (method IN ('QR', 'ID_LOOKUP', 'API')),
    location         VARCHAR(120),
    ip_hash          VARCHAR(64),                                  -- salted hash; raw addresses are never stored
    receipt          VARCHAR(32)  NOT NULL UNIQUE,
    checked_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_verif_org_time ON verification_log (organization_id, checked_at DESC);
CREATE INDEX ix_verif_credential ON verification_log (credential_id, checked_at DESC);
CREATE INDEX ix_verif_time ON verification_log (checked_at DESC);

-- Staff actions, hash-chained per organization: each row commits to the previous row's hash,
-- so editing or deleting history is detectable (see AuditService.verifyChain).
CREATE TABLE audit_event (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    seq              BIGINT       NOT NULL,
    actor_id         UUID REFERENCES app_user (id),
    actor_name       VARCHAR(160) NOT NULL,
    action           VARCHAR(60)  NOT NULL,
    detail           VARCHAR(500) NOT NULL,
    ip               VARCHAR(64),
    occurred_at      TIMESTAMPTZ  NOT NULL,
    prev_hash        VARCHAR(64)  NOT NULL,
    hash             VARCHAR(64)  NOT NULL,
    UNIQUE (organization_id, seq)
);
