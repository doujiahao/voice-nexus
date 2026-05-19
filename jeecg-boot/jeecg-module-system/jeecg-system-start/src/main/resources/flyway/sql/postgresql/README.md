# PostgreSQL Flyway history compatibility

Files `V3.8.0_1` through `V3.9.1_1` are intentionally kept byte-for-byte identical to the historical scripts recorded in existing PostgreSQL `flyway_schema_history` tables.

They include upstream MySQL-style SQL and are present to satisfy Flyway validation for databases that already have these versions marked as applied. Do not reformat or edit these files, because any content change will alter Flyway checksums and break validation on existing deployments.

New PostgreSQL migrations should be added as PostgreSQL-compatible SQL after the current latest version.
