CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE tenants (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL REFERENCES tenants (id),
    email          VARCHAR(320) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_tenant ON users (tenant_id);

CREATE TABLE contracts (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL REFERENCES tenants (id),
    original_filename   VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    size_bytes          BIGINT       NOT NULL,
    storage_path        VARCHAR(500) NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    extraction_status   VARCHAR(20)  NOT NULL,
    page_count          INTEGER,
    chunk_count         INTEGER,
    error_message       VARCHAR(1000),
    uploaded_by         BIGINT       NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at        TIMESTAMPTZ
);
CREATE INDEX idx_contracts_tenant ON contracts (tenant_id);

-- Every chunk/vector carries tenant_id + contract_id so retrieval can always be tenant-filtered.
CREATE TABLE contract_chunks (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT  NOT NULL REFERENCES tenants (id),
    contract_id  BIGINT  NOT NULL REFERENCES contracts (id) ON DELETE CASCADE,
    chunk_index  INTEGER NOT NULL,
    page_number  INTEGER,
    text         TEXT    NOT NULL,
    embedding    vector(1536)
);
CREATE INDEX idx_chunks_tenant_contract ON contract_chunks (tenant_id, contract_id);
CREATE INDEX idx_chunks_embedding ON contract_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE extracted_contract_data (
    id                       BIGSERIAL PRIMARY KEY,
    tenant_id                BIGINT NOT NULL REFERENCES tenants (id),
    contract_id              BIGINT NOT NULL UNIQUE REFERENCES contracts (id) ON DELETE CASCADE,
    parties                  TEXT,
    effective_date           DATE,
    expiration_date          DATE,
    renewal_period           VARCHAR(255),
    auto_renewal             BOOLEAN,
    renewal_term_months      INTEGER,
    termination_notice_days  INTEGER,
    governing_law            VARCHAR(255),
    liability_cap            VARCHAR(500),
    payment_terms            VARCHAR(500),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_extracted_tenant ON extracted_contract_data (tenant_id);

CREATE TABLE risk_results (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL REFERENCES tenants (id),
    contract_id  BIGINT       NOT NULL REFERENCES contracts (id) ON DELETE CASCADE,
    type         VARCHAR(40)  NOT NULL,
    severity     VARCHAR(10)  NOT NULL,
    description  VARCHAR(1000) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_risks_tenant_contract ON risk_results (tenant_id, contract_id);
