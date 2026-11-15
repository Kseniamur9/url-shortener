CREATE TABLE urls (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(10) NOT NULL,
    original_url TEXT        NOT NULL,
    click_count  BIGINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMP   NOT NULL,
    expires_at   TIMESTAMP,
    CONSTRAINT uq_urls_code UNIQUE (code)
);