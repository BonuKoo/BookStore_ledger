-- M3 ledger-worker — 수동 적용 DDL
-- 대상 DB: core2_spa (settlement-worker/core-spa와 공유하는 PC1 MySQL 인스턴스, ddl-auto=none)
--
-- core2_spa에는 과거 실험 흔적인 `accounts`/`ledger_entries`/`ledger_transactions`
-- 테이블이 이미 존재했으나(전부 0 rows, name UNIQUE 없음·idempotency_key nullable+
-- 비UNIQUE라 사실상 멱등 보장이 안 되는 스키마 — 사용자 확인 후 DROP 승인받음),
-- M3는 UNIQUE 멱등 키 기반으로 재설계하므로 아래로 교체한다.
--
-- 설계 메모:
--  - accounts.name UNIQUE — REVENUE/ITEM_BUYER 두 계정을 이 파일에서 함께 시드한다.
--  - ledger_transactions: UNIQUE(order_id, seller_id, product_id) — 결제 주문의
--    판매자-상품 한 줄은 한 번만 기록된다. 원본의 reference_id(항목 원본 DB 행 id)는
--    컨슈머가 받는 JSON 메시지에 없어 제외 — 이 복합키가 이미 각 줄을 유일하게 식별한다.
--  - ledger_entries: transaction_id/account_id FK. 한 transaction마다 CREDIT 1행 +
--    DEBIT 1행이 쌍으로 들어간다(애플리케이션이 강제, DB 제약은 아님).

DROP TABLE IF EXISTS ledger_entries;
DROP TABLE IF EXISTS ledger_transactions;
DROP TABLE IF EXISTS accounts;

CREATE TABLE accounts (
    id   BIGINT       NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_accounts_name UNIQUE (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO accounts (name) VALUES ('REVENUE'), ('ITEM_BUYER');

CREATE TABLE ledger_transactions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    VARCHAR(255) NOT NULL,
    seller_id   BIGINT       NOT NULL,
    product_id  VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ledger_tx_order_seller_product UNIQUE (order_id, seller_id, product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE ledger_entries (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    transaction_id BIGINT        NOT NULL,
    account_id     BIGINT        NOT NULL,
    amount         DECIMAL(19,4) NOT NULL,
    type           VARCHAR(20)   NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ledger_entries_transaction FOREIGN KEY (transaction_id) REFERENCES ledger_transactions (id),
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (account_id) REFERENCES accounts (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
