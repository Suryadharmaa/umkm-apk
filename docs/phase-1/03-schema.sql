-- Phase 1 SQLite reference, not a Room migration.
PRAGMA foreign_keys = ON;

CREATE TABLE shop (
  singleton INTEGER PRIMARY KEY CHECK(singleton = 1),
  name TEXT NOT NULL,
  time_zone TEXT NOT NULL
);
CREATE TABLE category (
  id TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  name_key TEXT NOT NULL UNIQUE,
  archived_at INTEGER
);
CREATE TABLE product (
  id TEXT PRIMARY KEY NOT NULL,
  category_id TEXT REFERENCES category(id),
  name TEXT NOT NULL,
  unit_idr INTEGER NOT NULL CHECK(typeof(unit_idr) = 'integer' AND unit_idr BETWEEN 1 AND 999999999),
  archived_at INTEGER
);
CREATE INDEX product_category ON product(category_id);
CREATE TABLE qris_profile (
  id TEXT PRIMARY KEY NOT NULL,
  provider_namespace TEXT NOT NULL,
  merchant_account_key TEXT NOT NULL,
  display_name TEXT NOT NULL,
  asset BLOB NOT NULL CHECK(length(asset) BETWEEN 1 AND 2097152),
  active INTEGER NOT NULL CHECK(active IN (0,1))
);
CREATE UNIQUE INDEX one_qris_profile ON qris_profile(active) WHERE active = 1;

CREATE TABLE sale (
  id TEXT PRIMARY KEY NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('DRAFT','PENDING_QRIS','PAID','CANCELLED','REVERSED')),
  replaces_sale_id TEXT UNIQUE REFERENCES sale(id) CHECK(replaces_sale_id <> id),
  total_idr INTEGER NOT NULL CHECK(typeof(total_idr) = 'integer' AND total_idr BETWEEN 0 AND 999999999999),
  created_at INTEGER NOT NULL
);
CREATE INDEX sale_status ON sale(status, created_at);
CREATE TABLE sale_line (
  sale_id TEXT NOT NULL REFERENCES sale(id),
  line_no INTEGER NOT NULL CHECK(line_no > 0),
  product_id TEXT NOT NULL REFERENCES product(id),
  name_snapshot TEXT NOT NULL,
  unit_idr INTEGER NOT NULL CHECK(typeof(unit_idr) = 'integer' AND unit_idr BETWEEN 1 AND 999999999),
  qty INTEGER NOT NULL CHECK(typeof(qty) = 'integer' AND qty BETWEEN 1 AND 9999),
  PRIMARY KEY(sale_id, line_no),
  UNIQUE(sale_id, product_id)
);
CREATE TABLE payment (
  sale_id TEXT PRIMARY KEY NOT NULL REFERENCES sale(id),
  method TEXT NOT NULL CHECK(method IN ('CASH','QRIS')),
  amount_idr INTEGER NOT NULL CHECK(typeof(amount_idr) = 'integer' AND amount_idr BETWEEN 1 AND 999999999999),
  tender_idr INTEGER,
  profile_id TEXT REFERENCES qris_profile(id),
  CHECK((method = 'CASH' AND profile_id IS NULL AND typeof(tender_idr) = 'integer'
          AND tender_idr BETWEEN amount_idr AND 999999999999)
     OR (method = 'QRIS' AND profile_id IS NOT NULL AND tender_idr IS NULL))
);
CREATE TABLE qris_confirmation (
  sale_id TEXT PRIMARY KEY NOT NULL REFERENCES payment(sale_id),
  provider_namespace TEXT NOT NULL,
  merchant_account_key TEXT NOT NULL,
  reference_key TEXT NOT NULL CHECK(length(trim(reference_key)) > 0),
  observed_amount_idr INTEGER NOT NULL CHECK(typeof(observed_amount_idr) = 'integer' AND observed_amount_idr BETWEEN 1 AND 999999999999),
  provider_paid_at INTEGER NOT NULL,
  confirmed_at INTEGER NOT NULL,
  late_match_note TEXT,
  UNIQUE(provider_namespace, merchant_account_key, reference_key)
);

CREATE TABLE account (
  code TEXT PRIMARY KEY NOT NULL CHECK(code IN ('CASH','QRIS_CLEARING','BANK','SALES','SALES_RETURNS',
    'OTHER_INCOME','OPERATING_EXPENSE','SETTLEMENT_FEE','OWNER_EQUITY'))
);
INSERT INTO account(code) VALUES ('CASH'),('QRIS_CLEARING'),('BANK'),('SALES'),('SALES_RETURNS'),
  ('OTHER_INCOME'),('OPERATING_EXPENSE'),('SETTLEMENT_FEE'),('OWNER_EQUITY');

CREATE TABLE journal (
  id TEXT PRIMARY KEY NOT NULL,
  kind TEXT NOT NULL CHECK(kind IN ('SALE','REFUND','OPENING','OTHER_INCOME','OPERATING_EXPENSE',
    'OWNER_IN','OWNER_OUT','TRANSFER','SETTLEMENT','REVERSAL')),
  sale_id TEXT REFERENCES sale(id),
  reverses_id TEXT UNIQUE REFERENCES journal(id),
  opening_account TEXT REFERENCES account(code),
  occurred_at INTEGER NOT NULL,
  business_date TEXT NOT NULL,
  recorded_at INTEGER NOT NULL,
  note TEXT,
  CHECK((kind IN ('SALE','REFUND')) = (sale_id IS NOT NULL)),
  CHECK((kind = 'REVERSAL') = (reverses_id IS NOT NULL)),
  CHECK((kind = 'OPENING') = (opening_account IS NOT NULL)),
  CHECK(opening_account IS NULL OR opening_account IN ('CASH','BANK'))
);
CREATE UNIQUE INDEX journal_sale_kind ON journal(sale_id, kind);
CREATE UNIQUE INDEX journal_opening ON journal(opening_account);
CREATE INDEX journal_period ON journal(business_date,kind);
CREATE TABLE journal_line (
  journal_id TEXT NOT NULL REFERENCES journal(id),
  account_code TEXT NOT NULL REFERENCES account(code),
  signed_idr INTEGER NOT NULL CHECK(typeof(signed_idr) = 'integer' AND signed_idr BETWEEN -999999999999 AND 999999999999),
  PRIMARY KEY(journal_id,account_code)
);
CREATE INDEX journal_account ON journal_line(account_code,journal_id);

CREATE TABLE settlement (
  journal_id TEXT PRIMARY KEY NOT NULL REFERENCES journal(id),
  provider_namespace TEXT NOT NULL,
  merchant_account_key TEXT NOT NULL,
  payout_reference_key TEXT NOT NULL CHECK(length(trim(payout_reference_key)) > 0),
  gross_idr INTEGER NOT NULL CHECK(typeof(gross_idr) = 'integer' AND gross_idr BETWEEN 1 AND 999999999999),
  fee_idr INTEGER NOT NULL CHECK(typeof(fee_idr) = 'integer' AND fee_idr BETWEEN 0 AND gross_idr),
  UNIQUE(provider_namespace,merchant_account_key,payout_reference_key)
);
CREATE TABLE settlement_payment (
  settlement_id TEXT NOT NULL REFERENCES settlement(journal_id),
  sale_id TEXT NOT NULL REFERENCES qris_confirmation(sale_id),
  PRIMARY KEY(settlement_id,sale_id)
);
CREATE INDEX settlement_payment_sale ON settlement_payment(sale_id);
CREATE TABLE refund_evidence (
  journal_id TEXT PRIMARY KEY NOT NULL REFERENCES journal(id),
  settlement_id TEXT REFERENCES settlement(journal_id),
  provider_namespace TEXT NOT NULL,
  merchant_account_key TEXT NOT NULL,
  reference_key TEXT NOT NULL CHECK(length(trim(reference_key)) > 0),
  UNIQUE(provider_namespace,merchant_account_key,reference_key)
);
CREATE TABLE operation (
  id TEXT PRIMARY KEY NOT NULL,
  kind TEXT NOT NULL,
  request_hash TEXT NOT NULL,
  result_id TEXT NOT NULL,
  committed_at INTEGER NOT NULL
);
CREATE TABLE sale_event (
  operation_id TEXT PRIMARY KEY NOT NULL REFERENCES operation(id),
  sale_id TEXT NOT NULL REFERENCES sale(id),
  from_state TEXT,
  to_state TEXT NOT NULL CHECK(to_state IN ('DRAFT','PENDING_QRIS','PAID','CANCELLED','REVERSED')),
  occurred_at INTEGER NOT NULL,
  reason TEXT
);
CREATE INDEX sale_event_history ON sale_event(sale_id,occurred_at);

