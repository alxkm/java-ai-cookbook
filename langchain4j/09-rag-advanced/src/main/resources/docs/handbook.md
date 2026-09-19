# Acme Engineering Handbook (excerpt)

## Deployments

Production deployments happen on Tuesdays and Thursdays between 10:00 and 16:00 CET.
Deployments are frozen from 20 December to 5 January, and during any active Sev-1 incident.
A deployment needs two approvals: one from the owning team and one from the on-call engineer.

## On-call

On-call rotations last one week and start on Monday at 09:00 CET.
The on-call engineer acknowledges a page within 15 minutes during business hours and within
30 minutes outside them. Escalation goes to the team lead, then to the engineering manager.

## Incident severities

Sev-1 means customer-facing outage or data loss. It pages immediately and requires a written
post-mortem within five working days.
Sev-2 means degraded service with a workaround. It pages during business hours only.
Sev-3 means an internal issue with no customer impact. It becomes a normal ticket.

## Code review

Every change needs one approving review. Changes to the payments module need two, one of which
must come from the payments team. Reviews are expected within one working day.
