-- =============================================================================
-- SEMANTIC SEARCH ENGINE — POSTGRESQL 17 BOOTSTRAP / INITIALIZATION SCRIPT
-- =============================================================================
-- Bu scripti uzaktaki PostgreSQL 17 sunucunuzda superuser ('postgres') yetkisiyle
-- (pgAdmin Query Tool, psql CLI veya DBeaver üzerinden) çalıştırın.
-- =============================================================================

-- 1. Uygulama Kullanıcısını Oluştur (Eğer mevcut değilse):
DO
$do$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles WHERE rolname = 'semantic_search'
   ) THEN
      CREATE USER semantic_search WITH PASSWORD 'SearchDev2026';
   END IF;
END
$do$;

-- 2. Veritabanını Oluştur:
-- NOT: PostgreSQL kuralı gereği 'CREATE DATABASE' bir transaction bloğu içinde çalıştırılamaz.
-- Eğer psql CLI veya pgAdmin'de çalıştırıyorsanız aşağıdaki komutu tek başına çalıştırın:
SELECT 'CREATE DATABASE semantic_search WITH OWNER semantic_search ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'semantic_search')\gexec

-- 3. Veritabanı ve Şema Yetkilerini Ata:
GRANT ALL PRIVILEGES ON DATABASE semantic_search TO semantic_search;

-- =============================================================================
-- AŞAĞIDAKİ ADIMLARI 'semantic_search' VERİTABANINA BAĞLANDIKTAN SONRA ÇALIŞTIRIN:
-- psql komutu: \c semantic_search
-- =============================================================================
\c semantic_search

GRANT ALL ON SCHEMA public TO semantic_search;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO semantic_search;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO semantic_search;
