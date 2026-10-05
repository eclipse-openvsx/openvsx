# Admin Roles and Permissions

Open VSX has two layers of admin access: a global **role** and, on top of it, individually
granted **permissions**. Both are managed on the admin dashboard's **Access Control** page, or
through the admin REST API.

## Roles

| Role | Grants |
|------|--------|
| `admin` | Every permission below, including ones added in a later release. A superuser flag, not a bundle - it is never out of date. |
| `privileged` | Nothing covered by this page. It only bypasses per-namespace publisher verification (see [`RepositoryService.isVerifiedPublisher`](../server/src/main/java/org/eclipse/openvsx/repositories/RepositoryService.java)) and carries no admin dashboard access by itself. |
| *(none)* | No admin access. |

## Permissions

A user with no role can still be granted one or more of these individually, without making them a
full admin. Each one gates a specific admin dashboard page and its underlying REST endpoints.

| Permission | Admin dashboard page | What it unlocks |
|------------|----------------------|------------------|
| `manage_namespaces` | Namespaces | Create/delete namespaces, schedule namespace renames, view and edit namespace membership (owner/contributor roles) |
| `manage_extensions` | Extensions, Size overrides | Look up any extension, delete/purge extension versions, delete reviews, manage per-namespace and per-extension upload size overrides |
| `manage_publishers` | Publisher | Search users, view a user's publish info, revoke a publisher's contributions or access tokens (single or bulk), forget a user (GDPR erasure) |
| `manage_scans` | Scans | View security scan results and counts, retry failed scanner jobs, make scan decisions, manage the file allow/block list |
| `manage_consistency` | Data Consistency | List consistency checks, view their findings, fix one finding or all of them |
| `manage_rate_limits` | Rate Limiting (Tiers, Customers, Usage Stats) | Manage rate-limit tiers, customers, customer memberships and rate-limit tokens, view usage stats |
| `manage_caches` | Caches | View cache statistics, clear one or all caches |
| `manage_search_index` | Search Index, Search Explain | View search index statistics, trigger a rebuild, run search-explain queries |
| `manage_settings` | Settings | View and update runtime registry settings |
| `view_reports` | Logs, Statistics | View the admin report/statistics, browse the activity log |

A user's own granted permissions are included in the response of `GET /user`, so the webui knows
which admin dashboard pages to show them without needing the full `admin` role.

## What stays `admin`-only

Changing a user's role or permissions - the Access Control page itself - is not covered by any
permission and cannot be delegated. Granting access is itself a privilege-escalation action, so
only a user who already has the `admin` role can perform it, regardless of which permissions they
hold.

## Managing roles and permissions via the API

One plain admin REST endpoint, callable only from a logged-in admin session - unlike most other
admin endpoints, it does not accept a personal access token:

```
PUT /admin/user/{provider}/{loginName}/access
{ "role": "admin" | "privileged" | "none", "permissions": ["manage_extensions", ...] }
```

The body is the access the user should end up with, not a delta: the permissions listed replace
whatever they hold now, and an empty list revokes all of them. `"role": "none"` removes the role
entirely. Role and permissions are applied in one transaction, so a request that is rejected -
an unknown permission name, say - leaves the user exactly as they were.

Both fields are required. A partial body is rejected with 400 rather than treated as a request to
strip what it leaves out, so forgetting `role` cannot quietly demote the user.
