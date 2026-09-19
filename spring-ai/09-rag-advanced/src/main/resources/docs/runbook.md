# Acme Runbook (excerpt)

## Rollback

A rollback is a normal deployment of the previous release tag and does not need a freeze exception.
The on-call engineer can roll back without a second approval during a Sev-1.

## Database migrations

Migrations run before the new version starts. Backwards-incompatible migrations are split into two
releases: add the new column first, remove the old one a release later.

## Feature flags

Flags default to off in production. A flag left on for more than 30 days is reported to the owning
team and removed.
