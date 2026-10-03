# Extension Size Overrides Admin Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give admins a Size overrides page in the admin dashboard to list, create, edit and delete per-namespace and per-extension upload size overrides.

**Architecture:** A new `size-overrides/` folder under `webui/src/pages/admin-dashboard/`, cloned in shape from the neighbouring `tiers/` folder: a TanStack Query hook module, a `DataGrid` list page, a form dialog and a delete-confirmation dialog. The service layer gains four admin calls against the `/admin/size-overrides` endpoints that step 3 shipped. **No server changes.**

**Tech Stack:** React 18 + TypeScript, MUI + `@mui/x-data-grid`, TanStack Query, vitest + React Testing Library.

**Spec:** `docs/superpowers/specs/2026-10-03-extension-size-limits-design.md` (step 4, "Webui admin page cloned from `tiers/`"). Steps 1-3 are merged: `GET /admin/size-overrides`, `POST /admin/size-overrides/create`, `PUT /admin/size-overrides/{id}` and `DELETE /admin/size-overrides/{id}` all exist and are admin-guarded.

## Global Constraints

- **`yarn lint` must pass**, on full output. Fix every error, warning and info. Never disable a rule.
- **Every behavioral change ships a unit test and a `CHANGELOG.md` entry** under `## [next]`.
- Tests are **vitest** under `webui/test/unit/`, mirroring the source path. Run with `yarn test`.
- **No `any`** unless unavoidable. **No inline/dynamic imports** — top-level only.
- Prefer a `styled` component over a long `sx` block; prefer `rem` over `px`.
- Data fetching goes through a hook in the feature's own folder, never the service straight from a component.
- New files need the EPL-2.0 licence header — copy it from a neighbouring file.
- **`yarn` is not on PATH in this sandbox.** Run it as `node .yarn/releases/yarn-4.9.1.cjs <cmd>` from `webui/`.
- Stage only files you changed (`git add <path>`), never `git add -A`.

## Decisions taken in this plan

1. **No `created at` column and no verified-namespace badge.** The spec's column list (§121) names both, but `SizeOverrideJson` carries only `id`, `namespace`, `extension` and `maxSize`. Adding them means reopening the server, which this phase deliberately does not do. Columns are namespace, extension, max size, actions. The spec is updated to match in Task 3.
2. **The namespace is a lookup field, not an autocomplete; the extension is a real autocomplete.** Spec §122 asks for both as autocompletes, but **nothing lists or searches namespaces** — `RepositoryService` has only `findConflictingNamespaces` and `findSimilarNamespacesByLevenshtein`, and no admin endpoint exposes a namespace list. A namespace autocomplete would need a new endpoint. Instead the dialog mirrors `namespace-admin.tsx`'s existing UX: type the namespace, look it up with the existing `service.admin.getNamespace`, which both validates it exists and returns `extensions` (a name → url map) to populate the extension autocomplete.
3. **Rows are identified by `id`.** `tiers.tsx` uses `getRowId={row => row.name}`; overrides have no unique name, so `getRowId={row => row.id}`, and update/delete address the numeric id.
4. **The size field uses the value + unit pattern** from `tier-form-dialog.tsx` (which splits a duration into value + unit), so an admin types "100" + "MB" rather than a raw byte count.
5. **No `createMultiSelectFilterOperators`.** `tiers.tsx` uses it for columns with a small fixed value set. Namespace and extension are free text, so the default filter operators are right.

## File Structure

| File | Responsibility |
|---|---|
| `webui/src/extension-registry-types.ts` | Modify: `SizeOverride`, `SizeOverrideList` types |
| `webui/src/extension-registry-service.ts` | Modify: four admin calls |
| `webui/src/pages/admin-dashboard/size-overrides/use-size-overrides.ts` | New: query + three mutations |
| `webui/src/pages/admin-dashboard/size-overrides/size-overrides.tsx` | New: DataGrid list page |
| `webui/src/pages/admin-dashboard/size-overrides/size-override-form-dialog.tsx` | New: create/edit dialog |
| `webui/src/pages/admin-dashboard/size-overrides/delete-size-override-dialog.tsx` | New: delete confirmation |
| `webui/src/pages/admin-dashboard/admin-dashboard-routes.ts` | Modify: route constant |
| `webui/src/pages/admin-dashboard/admin-dashboard.tsx` | Modify: nav entry + route |
| `webui/CHANGELOG.md` | Modify: entry under `## [next]` |

---

### Task 1: Types and service calls

**Files:**
- Modify: `webui/src/extension-registry-types.ts`
- Modify: `webui/src/extension-registry-service.ts`
- Test: `webui/test/unit/admin-size-override-request.spec.ts` (new)

**Interfaces:**
- Produces: `SizeOverride { id: number; namespace: string; extension?: string; maxSize: number }` and `SizeOverrideList { sizeOverrides: SizeOverride[] }`; `service.admin.getSizeOverrides(abortController)`, `createSizeOverride(override)`, `updateSizeOverride(id, override)`, `deleteSizeOverride(id)`. Tasks 2 and 3 consume all of them.
- Consumes: the `/admin/size-overrides` endpoints from step 3.

- [ ] **Step 1: Write the failing test**

Create `webui/test/unit/admin-size-override-request.spec.ts`, with the EPL-2.0 header used by `test/unit/admin-search-explain-request.spec.ts`, then:

```ts
import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, stubFetch } from './support/fetch';
import { ExtensionRegistryService } from '../../src/extension-registry-service';

describe('admin size override requests', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('lists overrides from the admin endpoint', async () => {
        const fetchMock = stubFetch(jsonResponse({ sizeOverrides: [] }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.admin.getSizeOverrides(new AbortController());

        expect(String(fetchMock.mock.calls[0][0])).toBe('https://registry.test/admin/size-overrides');
    });

    it('posts a new override to the create endpoint', async () => {
        const fetchMock = stubFetch(jsonResponse({ success: 'ok' }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.admin.createSizeOverride({ id: 0, namespace: 'foo', maxSize: 100 });

        expect(String(fetchMock.mock.calls.at(-1)?.[0]))
            .toBe('https://registry.test/admin/size-overrides/create');
    });

    // The id has to reach the URL. Addressing the wrong row is silent: the request succeeds and
    // edits somebody else's override.
    it('puts an update to the override its id names', async () => {
        const fetchMock = stubFetch(jsonResponse({ success: 'ok' }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.admin.updateSizeOverride(42, { id: 42, namespace: 'foo', maxSize: 200 });

        expect(String(fetchMock.mock.calls.at(-1)?.[0])).toBe('https://registry.test/admin/size-overrides/42');
    });

    it('deletes the override its id names', async () => {
        const fetchMock = stubFetch(jsonResponse({ success: 'ok' }));
        const service = new ExtensionRegistryService('https://registry.test');

        await service.admin.deleteSizeOverride(42);

        expect(String(fetchMock.mock.calls.at(-1)?.[0])).toBe('https://registry.test/admin/size-overrides/42');
    });
});
```

The mutators fetch a CSRF token first, so assert on the **last** call, not the first — that is what `calls.at(-1)` is for.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd webui && node .yarn/releases/yarn-4.9.1.cjs test test/unit/admin-size-override-request.spec.ts`

Expected: FAIL — `service.admin.getSizeOverrides` is not a function.

- [ ] **Step 3: Add the types**

In `webui/src/extension-registry-types.ts`, add next to the `Tier`/`TierList` declarations:

```ts
export interface SizeOverride {
    id: number;
    namespace: string;
    /** Absent means the override applies to the whole namespace. */
    extension?: string;
    /** Maximum package size in bytes. */
    maxSize: number;
}

export interface SizeOverrideList {
    sizeOverrides: SizeOverride[];
}
```

- [ ] **Step 4: Add the service calls**

In `webui/src/extension-registry-service.ts`, add `SizeOverride` and `SizeOverrideList` to the existing import from `./extension-registry-types`.

Add to the admin interface, next to the tier declarations:

```ts
    getSizeOverrides(abortController: AbortController): Promise<Readonly<SizeOverrideList>>;
    createSizeOverride(override: SizeOverride): Promise<Readonly<SizeOverride>>;
    updateSizeOverride(id: number, override: SizeOverride): Promise<Readonly<SizeOverride>>;
    deleteSizeOverride(id: number): Promise<Readonly<SuccessResult>>;
```

Add the implementations next to the tier implementations. These mirror `getTiers`/`createTier`/`updateTier`/`deleteTier` exactly — the CSRF preamble is inlined in each mutator (there is no shared helper), and the mutators take no `abortController`:

```ts
    async getSizeOverrides(abortController: AbortController): Promise<Readonly<SizeOverrideList>> {
        return sendNonRetriableRequest({
            abortController,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'size-overrides']),
            credentials: true
        });
    }

    async createSizeOverride(override: SizeOverride): Promise<Readonly<SizeOverride>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendNonRetriableRequest({
            method: 'POST',
            payload: override,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'size-overrides', 'create']),
            headers
        });
    }

    async updateSizeOverride(id: number, override: SizeOverride): Promise<Readonly<SizeOverride>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendNonRetriableRequest({
            method: 'PUT',
            payload: override,
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'size-overrides', String(id)]),
            headers
        });
    }

    async deleteSizeOverride(id: number): Promise<Readonly<SuccessResult>> {
        const csrfResponse = await this.registry.getCsrfToken();
        const headers: Record<string, string> = {
            'Content-Type': 'application/json;charset=UTF-8'
        };
        if (!isError(csrfResponse)) {
            const csrfToken = csrfResponse as CsrfTokenJson;
            headers[csrfToken.header] = csrfToken.value;
        }
        return sendStrictRequest({
            method: 'DELETE',
            credentials: true,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'size-overrides', String(id)]),
            headers
        });
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd webui && node .yarn/releases/yarn-4.9.1.cjs test test/unit/admin-size-override-request.spec.ts`

Expected: PASS (4 tests).

- [ ] **Step 6: Lint and commit**

Run: `cd webui && node .yarn/releases/yarn-4.9.1.cjs lint`

```bash
git add webui/src/extension-registry-types.ts \
        webui/src/extension-registry-service.ts \
        webui/test/unit/admin-size-override-request.spec.ts
git commit -m "feat(webui): add admin service calls for extension size overrides"
```

---

### Task 2: The list page

**Files:**
- Create: `webui/src/pages/admin-dashboard/size-overrides/use-size-overrides.ts`
- Create: `webui/src/pages/admin-dashboard/size-overrides/size-overrides.tsx`
- Modify: `webui/src/pages/admin-dashboard/admin-dashboard-routes.ts`
- Modify: `webui/src/pages/admin-dashboard/admin-dashboard.tsx`
- Test: `webui/test/unit/pages/admin-dashboard/size-overrides.spec.tsx` (new)

**Interfaces:**
- Produces: `SizeOverrides` page component; `useSizeOverrides`, `useCreateSizeOverride`, `useUpdateSizeOverride`, `useDeleteSizeOverride`; route `AdminDashboardRoutes.SIZE_OVERRIDES`. Task 3 consumes the hooks and mounts its dialogs here.
- Consumes: Task 1's service calls and types.

**Note:** this task renders the dialogs Task 3 creates. To keep the page compiling on its own, build it with the dialogs' props wired but the components imported from Task 3's files — so do Task 3's two files first if you are executing out of order. The commit at the end of Task 3 is what makes the feature whole.

- [ ] **Step 1: Write the hook module**

Create `webui/src/pages/admin-dashboard/size-overrides/use-size-overrides.ts`, with the EPL-2.0 header, mirroring `../tiers/use-tiers.ts`:

```ts
import { useContext } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MainContext } from '../../../context';
import type { SizeOverride } from '../../../extension-registry-types';
import { controllerFromSignal } from '../../../query-client';

export const sizeOverridesQueryKey = ['admin', 'sizeOverrides'] as const;

export const useSizeOverrides = () => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: sizeOverridesQueryKey,
        queryFn: ({ signal }) => service.admin.getSizeOverrides(controllerFromSignal(signal))
    });
};

export const useCreateSizeOverride = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (override: SizeOverride) => service.admin.createSizeOverride(override),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: sizeOverridesQueryKey });
        }
    });
};

export const useUpdateSizeOverride = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ id, override }: { id: number; override: SizeOverride }) =>
            service.admin.updateSizeOverride(id, override),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: sizeOverridesQueryKey });
        }
    });
};

export const useDeleteSizeOverride = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (id: number) => service.admin.deleteSizeOverride(id),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: sizeOverridesQueryKey });
        }
    });
};
```

- [ ] **Step 2: Write the failing page test**

Create `webui/test/unit/pages/admin-dashboard/size-overrides.spec.tsx`, with the EPL-2.0 header, following `caches.spec.tsx`:

```tsx
import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SizeOverrides } from '../../../../src/pages/admin-dashboard/size-overrides/size-overrides';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { SizeOverride } from '../../../../src/extension-registry-types';
import { renderWithProviders } from '../../support/test-providers';

// deliberately not named render*, so the testing-library lint rule does not treat the service stub
// it returns as a render result
const mountPage = (sizeOverrides: SizeOverride[], overrides: Record<string, unknown> = {}) => {
    const admin = {
        getSizeOverrides: vi.fn().mockResolvedValue({ sizeOverrides }),
        createSizeOverride: vi.fn().mockResolvedValue({ success: 'ok' }),
        updateSizeOverride: vi.fn().mockResolvedValue({ success: 'ok' }),
        deleteSizeOverride: vi.fn().mockResolvedValue({ success: 'ok' }),
        getNamespace: vi.fn().mockResolvedValue({ name: 'foo', extensions: { bar: 'url' } }),
        ...overrides
    };
    renderWithProviders(<SizeOverrides />, {
        mainContext: { service: { admin } as unknown as ExtensionRegistryService }
    });
    return admin;
};

describe('SizeOverrides', () => {
    it('lists a namespace-wide override with a placeholder for the extension', async () => {
        mountPage([{ id: 1, namespace: 'foo', maxSize: 100 * 1024 * 1024 }]);

        expect(await screen.findByText('foo')).toBeInTheDocument();
        expect(screen.getByText('100 MB')).toBeInTheDocument();
    });

    it('lists an extension-scoped override with its extension name', async () => {
        mountPage([{ id: 2, namespace: 'foo', extension: 'bar', maxSize: 1024 * 1024 }]);

        expect(await screen.findByText('bar')).toBeInTheDocument();
        expect(screen.getByText('1 MB')).toBeInTheDocument();
    });

    it('shows an empty state when there are no overrides', async () => {
        mountPage([]);

        expect(await screen.findByText(/no size overrides/i)).toBeInTheDocument();
    });

    it('deletes the override whose row action was used', async () => {
        const user = userEvent.setup();
        const admin = mountPage([{ id: 7, namespace: 'foo', maxSize: 100 }]);

        await user.click(await screen.findByTitle('Delete size override'));
        await user.click(await screen.findByRole('button', { name: /^delete$/i }));

        await waitFor(() => expect(admin.deleteSizeOverride).toHaveBeenCalledWith(7));
    });
});
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd webui && node .yarn/releases/yarn-4.9.1.cjs test test/unit/pages/admin-dashboard/size-overrides.spec.tsx`

Expected: FAIL — the page module does not exist.

- [ ] **Step 4: Write the page**

Create `webui/src/pages/admin-dashboard/size-overrides/size-overrides.tsx`, with the EPL-2.0 header:

```tsx
import { FC, useEffect, useState } from 'react';
import { Alert, Box, Button, CircularProgress, IconButton, Paper, Typography } from '@mui/material';
import { DataGrid, GridColDef, GridRenderCellParams } from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import type { SizeOverride } from '../../../extension-registry-types';
import { handleError } from '../../../utils';
import { DeleteSizeOverrideDialog } from './delete-size-override-dialog';
import { SizeOverrideFormDialog } from './size-override-form-dialog';
import {
    useCreateSizeOverride,
    useDeleteSizeOverride,
    useSizeOverrides,
    useUpdateSizeOverride
} from './use-size-overrides';

const UNITS: ReadonlyArray<{ label: string; bytes: number }> = [
    { label: 'GB', bytes: 1024 * 1024 * 1024 },
    { label: 'MB', bytes: 1024 * 1024 },
    { label: 'KB', bytes: 1024 },
    { label: 'bytes', bytes: 1 }
];

export const formatSize = (bytes: number): string => {
    const unit = UNITS.find(candidate => bytes >= candidate.bytes && bytes % candidate.bytes === 0);
    return unit ? `${bytes / unit.bytes} ${unit.label}` : `${bytes} bytes`;
};

export const SizeOverrides: FC = () => {
    const [formDialogOpen, setFormDialogOpen] = useState(false);
    const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
    const [selected, setSelected] = useState<SizeOverride | undefined>();
    const [errorDismissed, setErrorDismissed] = useState(false);

    const { data, isFetching: loading, error: loadError } = useSizeOverrides();
    const { mutateAsync: createSizeOverride } = useCreateSizeOverride();
    const { mutateAsync: updateSizeOverride } = useUpdateSizeOverride();
    const { mutateAsync: deleteSizeOverride } = useDeleteSizeOverride();

    useEffect(() => {
        setErrorDismissed(false);
    }, [loadError]);

    const error = loadError && !errorDismissed ? handleError(loadError as Error) : null;
    const sizeOverrides: readonly SizeOverride[] = data?.sizeOverrides ?? [];

    const handleCreateClick = () => {
        setSelected(undefined);
        setFormDialogOpen(true);
    };

    const handleEditClick = (override: SizeOverride) => {
        setSelected(override);
        setFormDialogOpen(true);
    };

    const handleDeleteClick = (override: SizeOverride) => {
        setSelected(override);
        setDeleteDialogOpen(true);
    };

    const handleFormSubmit = async (override: SizeOverride) => {
        if (selected) {
            await updateSizeOverride({ id: selected.id, override });
        } else {
            await createSizeOverride(override);
        }
    };

    const handleDeleteConfirm = async () => {
        if (selected) {
            await deleteSizeOverride(selected.id);
        }
    };

    const columns: GridColDef[] = [
        { field: 'namespace', headerName: 'Namespace', flex: 1, minWidth: 160 },
        {
            field: 'extension',
            headerName: 'Extension',
            flex: 1,
            minWidth: 160,
            valueGetter: (value: string) => value || 'All extensions'
        },
        {
            field: 'maxSize',
            headerName: 'Max size',
            width: 160,
            valueFormatter: (value: number) => formatSize(value)
        },
        {
            field: 'actions',
            headerName: '',
            width: 110,
            sortable: false,
            filterable: false,
            renderCell: (params: GridRenderCellParams<SizeOverride>) => (
                <>
                    <IconButton
                        color='primary'
                        title='Edit size override'
                        onClick={() => handleEditClick(params.row)}
                    >
                        <EditIcon />
                    </IconButton>
                    <IconButton
                        color='error'
                        title='Delete size override'
                        onClick={() => handleDeleteClick(params.row)}
                    >
                        <DeleteIcon />
                    </IconButton>
                </>
            )
        }
    ];

    return (
        <Box sx={{ p: 3, height: '100%', display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                <Typography variant='h4' component='h1'>
                    Size overrides
                </Typography>
                <Button variant='contained' startIcon={<AddIcon />} onClick={handleCreateClick}>
                    Create override
                </Button>
            </Box>
            {error && (
                <Alert severity='error' sx={{ mb: 2 }} onClose={() => setErrorDismissed(true)}>
                    {error}
                </Alert>
            )}
            {loading && sizeOverrides.length === 0 ? (
                <CircularProgress />
            ) : sizeOverrides.length === 0 ? (
                <Paper variant='outlined' sx={{ p: 3 }}>
                    <Typography>
                        No size overrides are configured. Every namespace uses the default limit from the
                        Settings page.
                    </Typography>
                </Paper>
            ) : (
                <Paper variant='outlined' sx={{ flex: 1 }}>
                    <DataGrid
                        rows={sizeOverrides}
                        columns={columns}
                        getRowId={row => row.id}
                        pageSizeOptions={[20, 35, 50]}
                        initialState={{ pagination: { paginationModel: { pageSize: 20 } } }}
                        disableRowSelectionOnClick
                    />
                </Paper>
            )}
            <SizeOverrideFormDialog
                open={formDialogOpen}
                sizeOverride={selected}
                onClose={() => {
                    setFormDialogOpen(false);
                    setSelected(undefined);
                }}
                onSubmit={handleFormSubmit}
            />
            <DeleteSizeOverrideDialog
                open={deleteDialogOpen}
                sizeOverride={selected}
                onClose={() => {
                    setDeleteDialogOpen(false);
                    setSelected(undefined);
                }}
                onConfirm={handleDeleteConfirm}
            />
        </Box>
    );
};
```

If `yarn lint` objects to the nested ternary or the inline `sx` blocks, extract a `styled` wrapper and an early-return for the loading state rather than disabling the rule.

- [ ] **Step 5: Register the route and nav entry**

In `webui/src/pages/admin-dashboard/admin-dashboard-routes.ts`, add after the `TIERS` line:

```ts
    export const SIZE_OVERRIDES = createRoute([ROOT, 'size-overrides']);
```

In `webui/src/pages/admin-dashboard/admin-dashboard.tsx`:

Add the import next to the other page imports:
```tsx
import { SizeOverrides } from './size-overrides/size-overrides';
```

Add the icon import next to the file's other MUI icon imports:
```tsx
import StorageIcon from '@mui/icons-material/Storage';
```

Add a top-level nav entry next to the existing flat `RouteEntry` objects (the ones shaped `{ path, name, icon, description }`):
```tsx
{
    path: AdminDashboardRoutes.SIZE_OVERRIDES,
    name: 'Size overrides',
    icon: <StorageIcon />,
    description: 'Per-namespace extension size limits'
},
```

Add the route next to the others:
```tsx
<Route path='/size-overrides' element={<SizeOverrides />} />
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd webui && node .yarn/releases/yarn-4.9.1.cjs test test/unit/pages/admin-dashboard/size-overrides.spec.tsx`

Expected: PASS (4 tests). The delete test needs Task 3's dialog, so if you are executing strictly in order, expect that one to fail until Task 3 lands and re-run it there.

- [ ] **Step 7: Lint and commit**

Run: `cd webui && node .yarn/releases/yarn-4.9.1.cjs lint`

```bash
git add webui/src/pages/admin-dashboard/size-overrides/use-size-overrides.ts \
        webui/src/pages/admin-dashboard/size-overrides/size-overrides.tsx \
        webui/src/pages/admin-dashboard/admin-dashboard-routes.ts \
        webui/src/pages/admin-dashboard/admin-dashboard.tsx \
        webui/test/unit/pages/admin-dashboard/size-overrides.spec.tsx
git commit -m "feat(webui): add the size overrides admin page"
```

---

### Task 3: The dialogs

**Files:**
- Create: `webui/src/pages/admin-dashboard/size-overrides/size-override-form-dialog.tsx`
- Create: `webui/src/pages/admin-dashboard/size-overrides/delete-size-override-dialog.tsx`
- Modify: `webui/CHANGELOG.md`
- Modify: `docs/superpowers/specs/2026-10-03-extension-size-limits-design.md`
- Test: `webui/test/unit/pages/admin-dashboard/size-override-form-dialog.spec.tsx` (new)

**Interfaces:**
- Produces: `SizeOverrideFormDialog` with props `{ open: boolean; sizeOverride?: SizeOverride; onClose: () => void; onSubmit: (override: SizeOverride) => Promise<void> }`, and `DeleteSizeOverrideDialog` with props `{ open: boolean; sizeOverride?: SizeOverride; onClose: () => void; onConfirm: () => Promise<void> }`. Task 2's page consumes both with exactly those prop names.
- Consumes: `service.admin.getNamespace(abortController, name)` (already exists) for the namespace lookup and the extension list.

- [ ] **Step 1: Write the failing test**

Create `webui/test/unit/pages/admin-dashboard/size-override-form-dialog.spec.tsx`, with the EPL-2.0 header:

```tsx
import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SizeOverrideFormDialog } from '../../../../src/pages/admin-dashboard/size-overrides/size-override-form-dialog';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { renderWithProviders } from '../../support/test-providers';

const mountDialog = (onSubmit = vi.fn().mockResolvedValue(undefined), getNamespace = vi.fn()) => {
    const admin = { getNamespace };
    renderWithProviders(
        <SizeOverrideFormDialog open sizeOverride={undefined} onClose={vi.fn()} onSubmit={onSubmit} />,
        { mainContext: { service: { admin } as unknown as ExtensionRegistryService } }
    );
    return { admin, onSubmit };
};

describe('SizeOverrideFormDialog', () => {
    it('converts the typed value and unit to bytes on submit', async () => {
        const user = userEvent.setup();
        const getNamespace = vi.fn().mockResolvedValue({ name: 'foo', extensions: {} });
        const { onSubmit } = mountDialog(vi.fn().mockResolvedValue(undefined), getNamespace);

        await user.type(screen.getByLabelText(/namespace/i), 'foo');
        await user.click(screen.getByRole('button', { name: /look up/i }));
        await waitFor(() => expect(getNamespace).toHaveBeenCalled());

        await user.clear(screen.getByLabelText(/max size/i));
        await user.type(screen.getByLabelText(/max size/i), '100');

        await user.click(screen.getByRole('button', { name: /create/i }));

        await waitFor(() =>
            expect(onSubmit).toHaveBeenCalledWith(
                expect.objectContaining({ namespace: 'foo', maxSize: 100 * 1024 * 1024 })
            )
        );
    });

    it('offers the looked-up namespace extensions to choose from', async () => {
        const user = userEvent.setup();
        const getNamespace = vi.fn().mockResolvedValue({ name: 'foo', extensions: { bar: 'u', baz: 'u' } });
        mountDialog(vi.fn().mockResolvedValue(undefined), getNamespace);

        await user.type(screen.getByLabelText(/namespace/i), 'foo');
        await user.click(screen.getByRole('button', { name: /look up/i }));

        await user.click(await screen.findByLabelText(/extension/i));

        expect(await screen.findByText('bar')).toBeInTheDocument();
        expect(screen.getByText('baz')).toBeInTheDocument();
    });

    it('reports an unknown namespace instead of submitting', async () => {
        const user = userEvent.setup();
        const getNamespace = vi.fn().mockRejectedValue(new Error('Namespace not found: nope'));
        const { onSubmit } = mountDialog(vi.fn().mockResolvedValue(undefined), getNamespace);

        await user.type(screen.getByLabelText(/namespace/i), 'nope');
        await user.click(screen.getByRole('button', { name: /look up/i }));

        expect(await screen.findByText(/not found/i)).toBeInTheDocument();
        expect(onSubmit).not.toHaveBeenCalled();
    });

    it('will not submit without a namespace', async () => {
        const user = userEvent.setup();
        const { onSubmit } = mountDialog();

        await user.click(screen.getByRole('button', { name: /create/i }));

        expect(onSubmit).not.toHaveBeenCalled();
    });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd webui && node .yarn/releases/yarn-4.9.1.cjs test test/unit/pages/admin-dashboard/size-override-form-dialog.spec.tsx`

Expected: FAIL — the dialog module does not exist.

- [ ] **Step 3: Write the form dialog**

Create `webui/src/pages/admin-dashboard/size-overrides/size-override-form-dialog.tsx`, with the EPL-2.0 header:

```tsx
import { FC, useContext, useEffect, useState } from 'react';
import {
    Alert,
    Autocomplete,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    MenuItem,
    Stack,
    TextField
} from '@mui/material';
import { MainContext } from '../../../context';
import type { SizeOverride } from '../../../extension-registry-types';
import { handleError } from '../../../utils';

const UNIT_MULTIPLIERS: Record<string, number> = {
    bytes: 1,
    KB: 1024,
    MB: 1024 * 1024,
    GB: 1024 * 1024 * 1024
};

const splitSize = (bytes: number): { value: number; unit: string } => {
    const unit = ['GB', 'MB', 'KB'].find(
        candidate => bytes >= UNIT_MULTIPLIERS[candidate] && bytes % UNIT_MULTIPLIERS[candidate] === 0
    );
    return unit ? { value: bytes / UNIT_MULTIPLIERS[unit], unit } : { value: bytes, unit: 'bytes' };
};

export interface SizeOverrideFormDialogProps {
    open: boolean;
    sizeOverride?: SizeOverride;
    onClose: () => void;
    onSubmit: (override: SizeOverride) => Promise<void>;
}

export const SizeOverrideFormDialog: FC<SizeOverrideFormDialogProps> = ({
    open,
    sizeOverride,
    onClose,
    onSubmit
}) => {
    const { service } = useContext(MainContext);
    const isEditMode = sizeOverride !== undefined;

    const [namespace, setNamespace] = useState('');
    const [extension, setExtension] = useState<string | null>(null);
    const [extensionNames, setExtensionNames] = useState<string[]>([]);
    const [namespaceConfirmed, setNamespaceConfirmed] = useState(false);
    const [sizeValue, setSizeValue] = useState('100');
    const [sizeUnit, setSizeUnit] = useState('MB');
    const [lookingUp, setLookingUp] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | undefined>();

    useEffect(() => {
        if (!open) {
            return;
        }
        setError(undefined);
        if (sizeOverride) {
            const split = splitSize(sizeOverride.maxSize);
            setNamespace(sizeOverride.namespace);
            setExtension(sizeOverride.extension ?? null);
            setExtensionNames(sizeOverride.extension ? [sizeOverride.extension] : []);
            setNamespaceConfirmed(true);
            setSizeValue(String(split.value));
            setSizeUnit(split.unit);
        } else {
            setNamespace('');
            setExtension(null);
            setExtensionNames([]);
            setNamespaceConfirmed(false);
            setSizeValue('100');
            setSizeUnit('MB');
        }
    }, [open, sizeOverride]);

    const handleLookup = async () => {
        setLookingUp(true);
        setError(undefined);
        try {
            const found = await service.admin.getNamespace(new AbortController(), namespace);
            setExtensionNames(Object.keys(found.extensions));
            setNamespaceConfirmed(true);
        } catch (err) {
            setExtensionNames([]);
            setNamespaceConfirmed(false);
            setError(handleError(err as Error));
        } finally {
            setLookingUp(false);
        }
    };

    const maxSize = Number.parseInt(sizeValue, 10) * UNIT_MULTIPLIERS[sizeUnit];
    const canSubmit = namespaceConfirmed && Number.isFinite(maxSize) && maxSize > 0 && !saving;

    const handleSubmit = async () => {
        if (!canSubmit) {
            return;
        }
        setSaving(true);
        setError(undefined);
        try {
            await onSubmit({
                id: sizeOverride?.id ?? 0,
                namespace,
                extension: extension ?? undefined,
                maxSize
            });
            onClose();
        } catch (err) {
            setError(handleError(err as Error));
        } finally {
            setSaving(false);
        }
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth='sm' fullWidth>
            <DialogTitle>{isEditMode ? 'Edit size override' : 'Create size override'}</DialogTitle>
            <DialogContent>
                <Stack spacing={2} sx={{ mt: 1 }}>
                    {error && <Alert severity='error'>{error}</Alert>}
                    <Stack direction='row' spacing={1}>
                        <TextField
                            label='Namespace'
                            value={namespace}
                            fullWidth
                            disabled={isEditMode}
                            onChange={event => {
                                setNamespace(event.target.value);
                                setNamespaceConfirmed(false);
                                setExtension(null);
                                setExtensionNames([]);
                            }}
                        />
                        <Button
                            onClick={handleLookup}
                            disabled={isEditMode || namespace.length === 0 || lookingUp}
                            startIcon={lookingUp ? <CircularProgress size={20} /> : undefined}
                        >
                            Look up
                        </Button>
                    </Stack>
                    <Autocomplete
                        options={extensionNames}
                        value={extension}
                        disabled={!namespaceConfirmed || isEditMode}
                        onChange={(_event, value) => setExtension(value)}
                        renderInput={params => (
                            <TextField
                                {...params}
                                label='Extension (optional)'
                                helperText='Leave empty to apply the override to the whole namespace'
                            />
                        )}
                    />
                    <Stack direction='row' spacing={1}>
                        <TextField
                            label='Max size'
                            type='number'
                            value={sizeValue}
                            fullWidth
                            inputProps={{ min: '1' }}
                            onChange={event => setSizeValue(event.target.value)}
                        />
                        <TextField
                            select
                            label='Unit'
                            value={sizeUnit}
                            sx={{ minWidth: '8rem' }}
                            onChange={event => setSizeUnit(event.target.value)}
                        >
                            {Object.keys(UNIT_MULTIPLIERS).map(unit => (
                                <MenuItem key={unit} value={unit}>
                                    {unit}
                                </MenuItem>
                            ))}
                        </TextField>
                    </Stack>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Cancel</Button>
                <Button
                    variant='contained'
                    onClick={handleSubmit}
                    disabled={!canSubmit}
                    startIcon={saving ? <CircularProgress size={20} /> : undefined}
                >
                    {isEditMode ? 'Update' : 'Create'}
                </Button>
            </DialogActions>
        </Dialog>
    );
};
```

The namespace and extension are disabled in edit mode because the server's `PUT /admin/size-overrides/{id}` only reads `maxSize` — changing the scope means deleting and recreating the override.

- [ ] **Step 4: Write the delete dialog**

Create `webui/src/pages/admin-dashboard/size-overrides/delete-size-override-dialog.tsx`, with the EPL-2.0 header, mirroring `../tiers/delete-tier-dialog.tsx`:

```tsx
import { FC, useState } from 'react';
import {
    Alert,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Typography
} from '@mui/material';
import type { SizeOverride } from '../../../extension-registry-types';
import { handleError } from '../../../utils';

export interface DeleteSizeOverrideDialogProps {
    open: boolean;
    sizeOverride?: SizeOverride;
    onClose: () => void;
    onConfirm: () => Promise<void>;
}

export const DeleteSizeOverrideDialog: FC<DeleteSizeOverrideDialogProps> = ({
    open,
    sizeOverride,
    onClose,
    onConfirm
}) => {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | undefined>();

    const scope = sizeOverride?.extension
        ? `${sizeOverride.namespace}.${sizeOverride.extension}`
        : sizeOverride?.namespace;

    const handleConfirm = async () => {
        setLoading(true);
        setError(undefined);
        try {
            await onConfirm();
            onClose();
        } catch (err) {
            setError(handleError(err as Error));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth='sm' fullWidth>
            <DialogTitle>Delete size override</DialogTitle>
            <DialogContent>
                {error && <Alert severity='error'>{error}</Alert>}
                <Typography>
                    Delete the size override for <strong>{scope}</strong>?
                </Typography>
                <Typography sx={{ mt: 1 }} color='warning.main'>
                    Future uploads will use the registry default limit again.
                </Typography>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Cancel</Button>
                <Button
                    variant='contained'
                    color='error'
                    onClick={handleConfirm}
                    startIcon={loading ? <CircularProgress size={20} /> : undefined}
                >
                    Delete
                </Button>
            </DialogActions>
        </Dialog>
    );
};
```

- [ ] **Step 5: Run both page and dialog tests**

Run: `cd webui && node .yarn/releases/yarn-4.9.1.cjs test test/unit/pages/admin-dashboard/size-overrides.spec.tsx test/unit/pages/admin-dashboard/size-override-form-dialog.spec.tsx`

Expected: PASS (4 + 4 tests).

- [ ] **Step 6: Run the whole webui suite**

Run: `cd webui && node .yarn/releases/yarn-4.9.1.cjs test`

Expected: PASS. The baseline before this plan is 346 tests across 66 files.

- [ ] **Step 7: Add the changelog entry**

In `webui/CHANGELOG.md`, append one line to the existing `### Added` subsection under `## [next] (unreleased)`:

```markdown
- Add a Size overrides page to the admin dashboard for managing per-namespace and per-extension upload size limits ([#2129](https://github.com/eclipse-openvsx/openvsx/issues/2129))
```

- [ ] **Step 8: Correct the spec's column list**

The spec's webui section still describes columns this plan deliberately does not build. In `docs/superpowers/specs/2026-10-03-extension-size-limits-design.md`, in the `size-overrides.tsx` bullet, replace the column list and the autocomplete description so they read as built: columns are namespace, extension (blank = namespace-wide), max size and actions; the namespace is a lookup field validated through `admin.getNamespace`, and the extension is an autocomplete populated from that lookup. Note in the same bullet that there is no created-at column and no verified-namespace badge, because `SizeOverrideJson` carries neither and this phase makes no server change.

- [ ] **Step 9: Lint and commit**

Run: `cd webui && node .yarn/releases/yarn-4.9.1.cjs lint`

```bash
git add webui/src/pages/admin-dashboard/size-overrides/size-override-form-dialog.tsx \
        webui/src/pages/admin-dashboard/size-overrides/delete-size-override-dialog.tsx \
        webui/test/unit/pages/admin-dashboard/size-override-form-dialog.spec.tsx \
        webui/CHANGELOG.md \
        docs/superpowers/specs/2026-10-03-extension-size-limits-design.md
git commit -m "feat(webui): add create, edit and delete dialogs for size overrides"
```

---

## Self-Review Notes

- **Spec coverage.** Step 4 of the spec's order is the webui admin page cloned from `tiers/`. The hook module, list page, form dialog and delete dialog all map onto the spec's four named files. Two deviations — no created-at/verified columns, and a namespace lookup rather than an autocomplete — are recorded in "Decisions taken in this plan" and written back into the spec in Task 3, Step 8.
- **Why no namespace autocomplete.** Verified by reading the code, not assumed: `RepositoryService` has no namespace search beyond `findConflictingNamespaces` and `findSimilarNamespacesByLevenshtein`, and `AdminAPI` exposes only `GET /admin/namespace/{name}`. A namespace autocomplete is a new endpoint, which this phase excludes. The extension autocomplete needs no new endpoint because `Namespace.extensions` is already a name → url map on the existing lookup response.
- **Type consistency.** `SizeOverride`/`SizeOverrideList` are defined in Task 1 and used under those names in Tasks 2 and 3. The dialog props (`sizeOverride`, `onClose`, `onSubmit`/`onConfirm`) are declared in Task 3 and consumed with exactly those names by Task 2's page.
- **Task ordering caveat.** Task 2's page imports Task 3's dialogs, so the tree does not compile between the two commits if they are landed separately and built in between. That is stated in Task 2's note; execute them back to back, or squash before merging.
- **Not in scope.** The CLI advisory change (step 5) and the public documentation (step 6) remain separate plans. Nothing here touches the server.
