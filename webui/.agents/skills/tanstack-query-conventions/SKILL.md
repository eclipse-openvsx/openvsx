---
name: tanstack-query-conventions
description: How TanStack Query is used in the webui — hook shape, query keys, retries, mutations, options objects, and how consumers read results. Use when writing or reviewing any useQuery/useMutation/useInfiniteQuery code.
---

# TanStack Query conventions

## Hooks, not raw queries

- Never call `useQuery` / `useMutation` / `useInfiniteQuery` directly in a component — wrap each endpoint in a `use*` hook.
- The hook returns the **react-query result object unchanged**. Don't return just `data` or a hand-picked subset; consumers destructure what they need.
- Co-locate a new hook in the feature's folder; promote to `src/hooks/` only when a second place needs it (see AGENTS.md).

## Query hook shape

```ts
export const useUserExtension = (target: UserExtensionTarget) => {
    const { service } = useContext(MainContext);
    return useQuery({
        queryKey: ['user', 'extension', target.namespace, target.extension],
        queryFn: ({ signal }) => service.getExtension(signal, target.namespace, target.extension)
    });
};
```

- The `queryFn` is a one-liner because the service method rejects on failure — see "Errors belong to the service" below. Never re-check `isError` in a hook.
- Service query methods take the `queryFn`'s `signal` as their first argument, so TanStack cancels the request with the query.
- `useQuery` forbids `undefined`; normalise a "no result" case to `null`.
- Query keys are hierarchical arrays (`['admin', 'namespace', name]`). When a key is reused for invalidation, export a small `*Keys` helper next to the hook.

## Mutation hook shape

```ts
export const useCreateNamespace = () => {
    const { service } = useContext(MainContext);
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (name: string) => service.admin.createNamespace({ name }),
        onSuccess: (_result, name) => queryClient.invalidateQueries({ queryKey: namespaceAdminKeys.detail(name) })
    });
};
```

- **No signal in mutations** — writes are never aborted, so service write methods don't take one.
- Mutations never retry (TanStack's default `retry: 0`), not even on a 429: writes aren't idempotent.
- The `mutationFn` forwards the service call directly; the service rejects on failure, so `mutateAsync` callers get their `catch` / `onError` path for free.
- Invalidate or remove affected queries in `onSuccess`.

## Errors belong to the service, not the hook

The registry answers some failures with a `200` carrying an `{ error: '…' }` body instead of a non-2xx status. `sendRequest` (`server-request.ts`) is the only request function and the single place that normalises this: it rejects on such a body, tagged with its status, so a service method resolves with data or rejects — never both.

```ts
// extension-registry-service.ts
async getNamespace(signal: AbortSignal, name: string): Promise<Readonly<Namespace>> {
    return sendRequest({ signal, credentials: true, endpoint: /* … */ });
}
```

- Service return types never include `| ErrorResult`, and hooks never contain `if (isError(result)) throw result`.
- Where a 200 error result is an expected answer, the caller handles the rejection rather than the service recovering from it: `Main` treats `getUser`'s `status: 200` rejection as "not logged in" and reports any other failure.
- Same rule in tests: stub a failing service method with `mockRejectedValue({ error: '…' })`, not `mockResolvedValue`.

## Errors are reported where the query is defined

- A hook whose errors belong in the global error dialog wraps its result in `useReportedQuery` at the definition — `return useReportedQuery(useQuery({ … }), { action: 'load the namespace members' })`, and the same for `useMutation`. Always pass `action`: it titles the dialog "Couldn’t load the namespace members", so the user knows what failed. Callers never report by hand: no `useEffect` on `error`, no `onError: handleError`, no `catch` that calls `handleError`.
- A page that renders an error itself passes a stable `ignore` predicate, e.g. `{ ignore: isNotFound }` (`server-request.ts`) for a 404 shown as a not-found state.
- Errors shown inline (an `Alert`, `ListLoadError`) are not reported, so nothing is shown twice. `handleError` from `utils.ts` only formats a message; reporting goes through `MainContext.handleError`.
- Mutation callers use `mutate(vars, { onSuccess })` for follow-up work; `isPending` drives spinners.

## Retries and caching are owned by the shared client

- One singleton `queryClient` (`query-client.ts`) owns every retry; `sendRequest` sends once. Queries retry network/5xx with backoff, wait out a 429 for the `retryAfter` on the error (up to 10 times), and never retry another 4xx.
- `Main` builds `MainContext` above the `QueryClientProvider`, so its startup queries (`use-main-queries.ts`) take the service as an argument and pass the singleton client to `useQuery` explicitly.
- Defaults: `refetchOnWindowFocus: false`, `staleTime: 60s`. Override per hook only with reason — `staleTime: 0` / `gcTime: 0` when data must always be fresh (right after publish/delete), `retry: false` to let a 404 surface immediately.

## Options objects, not positional flags

When a hook needs optional behaviour, take an options object that forwards TanStack's options — never a bare positional flag:

```ts
export const useThing = (id: string, options?: Omit<UseQueryOptions<Thing>, 'queryKey' | 'queryFn'>) =>
    useQuery({ queryKey: ['thing', id], queryFn: /* … */, ...options });
```

## Naming

- `use<Thing>`, `useCreate*`, `useDelete*`, `useChange*`; infinite scroll → `useInfinite*` backed by `useInfiniteQuery`.
- Don't prefix admin hooks with "admin" — the `service.admin.*` namespace and the `admin-dashboard/` folder already convey it.

## Consumer side — always destructure and rename

```ts
const { data: user, error: userError, isFetching } = useUserExtension(target);
const { mutateAsync: createNamespace, isPending: creating } = useCreateNamespace();
```

Never read `result.data` or call `result.mutate` off an undestructured object. Destructure and give the fields meaningful names — especially for mutations, where a bare `mutate` / `isPending` says nothing at the call site.

The one exception: if you need to **forward** the query result to another function or component, pass the whole result object — don't destructure and re-pass individual fields, which hands over a point-in-time snapshot instead of the live result.

## Best practices

- **`queryOptions` for shared queries** — when the same query is read from more than one place, define it once with TanStack's `queryOptions({ queryKey, queryFn })` and spread it into the hook, for type-safe reuse, as `namespaceAdminQuery` is by the admin search view and the size-override form.
- **`staleTime`** — the shared client sets `60s`; raise it for rarely-changing data, drop to `0` for reads that must always refetch on mount. Keep `staleTime <= gcTime` (see pitfalls).
- **Pagination** keeps the previous page with `placeholderData: keepPreviousData` (as `use-infinite-search` does), never `initialData`.
- **`enabled` for dependent queries** — gate a query through the options object (`{ enabled: !!id }`), never by calling the hook conditionally.
- **`select` for derived data** — transform in `select` inside the query config, not in the component. The hook still returns the whole result (only `data`'s value changes), so this doesn't break "return the result unchanged".
- **Pure `queryFn`** — fetch and normalise only, no side effects. Deliberate exception: the extension-icon query produces an object URL that `query-client.ts` revokes on cache eviction.
- **Suspense** — only when a component sits under a Suspense boundary, `useSuspenseQuery` drops the loading/`undefined` branches. Our hooks default to plain `useQuery` with explicit loading/error handling; don't switch unless the boundary exists.
- **Mutations invalidate, they don't rely on optimism** — always invalidate affected queries after a mutation (see the mutation shape above); optimistic updates are an addition, not a replacement. If you do optimistic updates, `cancelQueries` in `onMutate` before writing and snapshot for rollback in `onError`.
- **Tests** — render through the `test-providers` harness: a fresh client per render with `retry: false` and `gcTime: Infinity` (so a query isn't collected mid-assertion). That covers "retry: false in tests", "gcTime: Infinity in tests", and "never share one client across tests"; don't hand-roll a client.

## Common pitfalls

- **`initialData` when you mean `placeholderData`** — `initialData` is treated as fresh (subject to `staleTime`) and written to cache; `placeholderData` is display-only.
- **Infinite query without `initialPageParam`** — required in v5.
- **Conditional hook calls** — gate with `enabled`, never wrap the hook in an `if`.
- **Optimistic update without cancelling first** — an in-flight refetch lands after your write and clobbers it; `cancelQueries` in `onMutate`.
- **`staleTime` > `gcTime`** — data is garbage-collected while still "fresh", causing surprise refetches.
- **Fire-and-forget invalidation when order matters** — the mutation-shape example doesn't await its `invalidateQueries`; **return** the promise from `onSuccess` when the mutation should stay pending until the refetch completes.
