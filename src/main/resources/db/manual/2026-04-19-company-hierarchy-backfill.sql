-- Idempotent PostgreSQL migration/backfill for company hierarchy + alarm receiver fields.
-- Safe to run manually in SQL editor before starting updated backend.

BEGIN;

CREATE TABLE IF NOT EXISTS companies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(160) NOT NULL UNIQUE,
    details VARCHAR(255),
    address VARCHAR(255),
    city VARCHAR(120),
    state VARCHAR(120),
    postal_code VARCHAR(32),
    country VARCHAR(120),
    phone VARCHAR(32),
    is_alarm_receiver_included BOOLEAN NOT NULL DEFAULT FALSE,
    alarm_receiver_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    alarm_receiver_config TEXT,
    whitelisted_dns TEXT,
    whitelisted_ips TEXT
);

ALTER TABLE companies ADD COLUMN IF NOT EXISTS details VARCHAR(255);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS address VARCHAR(255);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS city VARCHAR(120);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS state VARCHAR(120);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS postal_code VARCHAR(32);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS country VARCHAR(120);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS phone VARCHAR(32);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS is_alarm_receiver_included BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS alarm_receiver_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS alarm_receiver_config TEXT;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS whitelisted_dns TEXT;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS whitelisted_ips TEXT;

ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE locations ADD COLUMN IF NOT EXISTS alarm_receiver_account_number VARCHAR(80);
ALTER TABLE locations ADD COLUMN IF NOT EXISTS alarm_receiver_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE locations ADD COLUMN IF NOT EXISTS alarm_receiver_users VARCHAR(2000);

ALTER TABLE app_users ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS all_company_locations BOOLEAN NOT NULL DEFAULT TRUE;

-- manager_id is no longer used in user model
DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'app_users'::regclass
          AND pg_get_constraintdef(oid) ILIKE '%manager_id%'
    LOOP
        EXECUTE format('ALTER TABLE app_users DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE app_users DROP COLUMN IF EXISTS manager_id;


CREATE TABLE IF NOT EXISTS company_admin_locations (
    app_user_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    PRIMARY KEY (app_user_id, location_id)
);

INSERT INTO companies (name, details)
SELECT 'Default Company', 'Auto-created during migration'
WHERE NOT EXISTS (SELECT 1 FROM companies WHERE name = 'Default Company');

UPDATE locations
SET company_id = (SELECT id FROM companies WHERE name = 'Default Company')
WHERE company_id IS NULL;

UPDATE app_users
SET company_id = (
    SELECT l.company_id
    FROM locations l
    WHERE l.id = app_users.location_id
)
WHERE company_id IS NULL
  AND location_id IS NOT NULL;

UPDATE app_users
SET company_id = (SELECT id FROM companies WHERE name = 'Default Company')
WHERE company_id IS NULL;

UPDATE app_users SET role = 'COMPANY_ADMIN' WHERE role = 'MANAGER';
UPDATE app_users SET role = 'PORTAL_USER'  WHERE role = 'USER';


-- Replace *any* legacy role check constraints (constraint names may vary by DB)
DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'app_users'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%role%'
    LOOP
        EXECUTE format('ALTER TABLE app_users DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE app_users
ADD CONSTRAINT app_users_role_check
CHECK (role IN ('SUPER_ADMIN', 'COMPANY_ADMIN', 'PORTAL_USER', 'MOBILE_APP_USER'));


ALTER TABLE locations
    ALTER COLUMN company_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_locations_company'
    ) THEN
        ALTER TABLE locations
        ADD CONSTRAINT fk_locations_company
        FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_app_users_company'
    ) THEN
        ALTER TABLE app_users
        ADD CONSTRAINT fk_app_users_company
        FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_company_admin_locations_user'
    ) THEN
        ALTER TABLE company_admin_locations
        ADD CONSTRAINT fk_company_admin_locations_user
        FOREIGN KEY (app_user_id) REFERENCES app_users(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_company_admin_locations_location'
    ) THEN
        ALTER TABLE company_admin_locations
        ADD CONSTRAINT fk_company_admin_locations_location
        FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT indexname
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename = 'locations'
          AND indexdef ILIKE '%UNIQUE%'
          AND indexdef ILIKE '%(name)%'
    LOOP
        EXECUTE format('DROP INDEX IF EXISTS %I', r.indexname);
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_locations_company_name
ON locations (company_id, lower(name));

COMMIT;
