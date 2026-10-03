# Default Extension Size Setting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the default extension upload size limit admin-configurable at runtime, falling back to the existing `ovsx.publishing.max-content-size` config value until an admin sets it.

**Architecture:** Add a new `max-extension-size` key to the existing flat `Setting` key/value table (no schema change). `SettingsCache` gains `getLong`/`setLong` mirroring its existing `getBoolean`/`setBoolean`. `SettingsService.getMaxExtensionSize()` reads the cached value, falling back to `PublishingConfig.getMaxContentSize()` when no row exists. The existing generic `/admin/settings` GET/PUT endpoints and webui settings page are extended to carry the new field — no new endpoint, no new page.

**Tech Stack:** Spring Boot (Java), JUnit 5 + Mockito + AssertJ, MockMvc; React + MUI, vitest + React Testing Library.

**Spec:** `docs/superpowers/specs/2026-10-03-extension-size-limits-design.md` (Phase 1 of the suggested implementation order: "`max-extension-size` Setting key + settings API/UI field").

## Global Constraints

- No schema/migration changes in this plan — the `Setting` table's existing flat `key`/`value` shape is reused as-is.
- Behavior must be unchanged for any deployment that never touches the new admin UI field: `getMaxExtensionSize()` must return exactly `publishingConfig.getMaxContentSize()` until a `Setting` row exists.
- `ExtensionService`/`LocalRegistryService` enforcement is explicitly **out of scope** for this plan (Phase 2 of the spec) — do not wire `getMaxExtensionSize()` into the publish path here.
- Server: `./gradlew test` and `./gradlew spotlessCheck` must pass before committing.
- Webui: `yarn lint` must pass; every behavioral change needs a test and a `CHANGELOG.md` entry under `## [next]`.

---

### Task 1: Default size setting storage (`SettingsCache`, `SettingsService`, `SettingsJson`)

**Files:**
- Modify: `server/src/main/java/org/eclipse/openvsx/settings/SettingsCache.java`
- Modify: `server/src/main/java/org/eclipse/openvsx/settings/SettingsService.java`
- Modify: `server/src/main/java/org/eclipse/openvsx/json/SettingsJson.java`
- Test: `server/src/test/java/org/eclipse/openvsx/settings/SettingsServiceTest.java` (new)

**Interfaces:**
- Produces: `SettingsService.getMaxExtensionSize(): long` — the resolved default (DB override if present, else `PublishingConfig.getMaxContentSize()`). `SettingsService.SETTING_MAX_EXTENSION_SIZE: String` (constant `"max-extension-size"`). `SettingsJson.getMaxExtensionSize()/setMaxExtensionSize(long)`. Task 2 and Task 3 consume all three.
- Consumes: `PublishingConfig.getMaxContentSize(): long` (`server/src/main/java/org/eclipse/openvsx/publish/PublishingConfig.java`, already exists, no change).

- [ ] **Step 1: Write the failing test**

Create `server/src/test/java/org/eclipse/openvsx/settings/SettingsServiceTest.java`:

```java
/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/
package org.eclipse.openvsx.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.eclipse.openvsx.json.SettingsJson;
import org.eclipse.openvsx.publish.PublishingConfig;
import org.eclipse.openvsx.util.ErrorResultException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsServiceTest {

    private SettingsCache cache;
    private PublishingConfig publishingConfig;
    private SettingsService settings;

    @BeforeEach
    void setUp() {
        cache = Mockito.mock(SettingsCache.class);
        publishingConfig = Mockito.mock(PublishingConfig.class);
        // isReadOnly() unboxes SettingsCache#getBoolean's Boolean return value; an unstubbed mock
        // would hand back null here and NPE on every call that reaches it.
        when(cache.getBoolean(anyString(), anyBoolean())).thenReturn(false);
        settings = new SettingsService(null, cache, publishingConfig);
    }

    @Test
    void getMaxExtensionSizeFallsBackToPublishingConfigWhenNoOverrideStored() {
        when(publishingConfig.getMaxContentSize()).thenReturn(512L * 1024 * 1024);
        // SettingsCache#getLong returns its own defaultValue argument when no row exists;
        // simulate that by echoing it back.
        when(cache.getLong(eq(SettingsService.SETTING_MAX_EXTENSION_SIZE), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        assertThat(settings.getMaxExtensionSize()).isEqualTo(512L * 1024 * 1024);
    }

    @Test
    void getMaxExtensionSizeReturnsStoredOverrideWhenPresent() {
        when(cache.getLong(eq(SettingsService.SETTING_MAX_EXTENSION_SIZE), anyLong()))
                .thenReturn(1024L * 1024 * 1024);

        assertThat(settings.getMaxExtensionSize()).isEqualTo(1024L * 1024 * 1024);
    }

    @Test
    void updateFromJsonStoresNewMaxExtensionSizeAndReportsTheChange() {
        when(cache.getLong(eq(SettingsService.SETTING_MAX_EXTENSION_SIZE), anyLong()))
                .thenReturn(512L * 1024 * 1024);

        var newSettings = new SettingsJson();
        newSettings.setMaxExtensionSize(1024L * 1024 * 1024);

        var changes = settings.updateFromJson(newSettings);

        verify(cache).setLong(SettingsService.SETTING_MAX_EXTENSION_SIZE, 1024L * 1024 * 1024);
        assertThat(changes).contains("maxExtensionSize -> 1073741824");
    }

    @Test
    void updateFromJsonSkipsWritingWhenMaxExtensionSizeIsUnchanged() {
        when(cache.getLong(eq(SettingsService.SETTING_MAX_EXTENSION_SIZE), anyLong()))
                .thenReturn(512L * 1024 * 1024);

        var newSettings = new SettingsJson();
        newSettings.setMaxExtensionSize(512L * 1024 * 1024);

        var changes = settings.updateFromJson(newSettings);

        verify(cache, Mockito.never()).setLong(eq(SettingsService.SETTING_MAX_EXTENSION_SIZE), anyLong());
        assertThat(changes).doesNotContain("maxExtensionSize");
    }

    @Test
    void updateFromJsonRejectsNonPositiveMaxExtensionSize() {
        var newSettings = new SettingsJson();
        newSettings.setMaxExtensionSize(0);

        assertThatThrownBy(() -> settings.updateFromJson(newSettings)).isInstanceOf(ErrorResultException.class);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests org.eclipse.openvsx.settings.SettingsServiceTest`

Expected: compile failure — `SettingsService` has no `(RedisClusterClient, SettingsCache, PublishingConfig)` constructor, no `SETTING_MAX_EXTENSION_SIZE` constant, no `getMaxExtensionSize()` method; `SettingsCache` has no `getLong`/`setLong`; `SettingsJson` has no `getMaxExtensionSize`/`setMaxExtensionSize`.

- [ ] **Step 3: Add `getLong`/`setLong` to `SettingsCache`**

In `server/src/main/java/org/eclipse/openvsx/settings/SettingsCache.java`, add after the existing `setBoolean` method (before `clear()`):

```java
    @Cacheable(value = CACHE_SETTING, key = "#key")
    public Long getLong(String key, long defaultValue) {
        return repository.findByKey(key).map(Setting::getValue).map(Long::parseLong).orElse(defaultValue);
    }

    @Transactional
    @CacheEvict(value = CACHE_SETTING, key = "#key")
    public void setLong(String key, long value) {
        repository.upsert(key, String.valueOf(value), TimeUtil.getCurrentUTC());
    }
```

- [ ] **Step 4: Add `maxExtensionSize` to `SettingsJson`**

Replace the full contents of `server/src/main/java/org/eclipse/openvsx/json/SettingsJson.java` (keep the existing license header) with:

```java
package org.eclipse.openvsx.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class SettingsJson extends ResultJson {

    private boolean readOnly;
    private long maxExtensionSize;

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public long getMaxExtensionSize() {
        return maxExtensionSize;
    }

    public void setMaxExtensionSize(long maxExtensionSize) {
        this.maxExtensionSize = maxExtensionSize;
    }
}
```

- [ ] **Step 5: Wire `max-extension-size` through `SettingsService`**

Replace the full contents of `server/src/main/java/org/eclipse/openvsx/settings/SettingsService.java` (keep the existing license header) with:

```java
package org.eclipse.openvsx.settings;

import java.util.ArrayList;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.util.Strings;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import redis.clients.jedis.RedisClusterClient;

import org.eclipse.openvsx.cache.jedis.JedisClusterChannelListener;
import org.eclipse.openvsx.json.SettingsJson;
import org.eclipse.openvsx.publish.PublishingConfig;
import org.eclipse.openvsx.util.ErrorResultException;

@Service
public class SettingsService {

    public static final String SETTING_REGISTRY_READ_ONLY = "read-only";
    public static final String SETTING_MAX_EXTENSION_SIZE = "max-extension-size";
    private static final String SETTINGS_UPDATE_CHANNEL = "settings.update";

    private final Logger logger = LoggerFactory.getLogger(SettingsService.class);

    private final @Nullable RedisClusterClient redisClusterClient;
    private final SettingsUpdateListener settingsUpdateListener;
    private final SettingsCache cache;
    private final PublishingConfig publishingConfig;

    public SettingsService(
            @Nullable RedisClusterClient redisClusterClient, SettingsCache cache, PublishingConfig publishingConfig) {
        this.redisClusterClient = redisClusterClient;
        this.cache = cache;
        this.publishingConfig = publishingConfig;

        if (redisClusterClient != null) {
            settingsUpdateListener = new SettingsUpdateListener(redisClusterClient);
            logger.info("SettingsService initialized with Redis update listener");
        } else {
            settingsUpdateListener = null;
        }
    }

    @PostConstruct
    public void initialize() {
        if (settingsUpdateListener != null) {
            settingsUpdateListener.startSubscriber();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (settingsUpdateListener != null) {
            settingsUpdateListener.shutdown();
        }
    }

    public boolean isReadOnly() {
        return cache.getBoolean(SETTING_REGISTRY_READ_ONLY, false);
    }

    public long getMaxExtensionSize() {
        return cache.getLong(SETTING_MAX_EXTENSION_SIZE, publishingConfig.getMaxContentSize());
    }

    public SettingsJson getCurrentSettings() {
        var json = new SettingsJson();
        json.setReadOnly(isReadOnly());
        json.setMaxExtensionSize(getMaxExtensionSize());
        return json;
    }

    public String updateFromJson(SettingsJson newSettings) {
        if (newSettings.getMaxExtensionSize() <= 0) {
            throw new ErrorResultException("Max extension size must be greater than zero.");
        }

        var changes = new ArrayList<>();
        if (newSettings.isReadOnly() != isReadOnly()) {
            changes.add("readOnly -> " + newSettings.isReadOnly());
            cache.setBoolean(SETTING_REGISTRY_READ_ONLY, newSettings.isReadOnly());
        }
        if (newSettings.getMaxExtensionSize() != getMaxExtensionSize()) {
            changes.add("maxExtensionSize -> " + newSettings.getMaxExtensionSize());
            cache.setLong(SETTING_MAX_EXTENSION_SIZE, newSettings.getMaxExtensionSize());
        }
        publishSettingsUpdate();
        return Strings.join(changes, ',');
    }

    private void publishSettingsUpdate() {
        if (redisClusterClient != null) {
            logger.debug("Publish settings update");
            String version = String.valueOf(System.currentTimeMillis());
            redisClusterClient.publish(SETTINGS_UPDATE_CHANNEL, version);
        }
    }

    private class SettingsUpdateListener extends JedisClusterChannelListener {
        SettingsUpdateListener(RedisClusterClient redisClusterClient) {
            super(redisClusterClient, SETTINGS_UPDATE_CHANNEL, "SettingsUpdate");
        }

        @Override
        public void onMessage(String channel, String message) {
            if (SETTINGS_UPDATE_CHANNEL.equals(channel)) {
                logger.debug("received settings update");
                cache.clear();
            }
        }
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew test --tests org.eclipse.openvsx.settings.SettingsServiceTest`

Expected: PASS (4 tests).

- [ ] **Step 7: Format and commit**

Run: `./gradlew spotlessCheck` (run `./gradlew spotlessApply` first if it fails, then re-check that it only touched the files above).

```bash
git add server/src/main/java/org/eclipse/openvsx/settings/SettingsCache.java \
        server/src/main/java/org/eclipse/openvsx/settings/SettingsService.java \
        server/src/main/java/org/eclipse/openvsx/json/SettingsJson.java \
        server/src/test/java/org/eclipse/openvsx/settings/SettingsServiceTest.java
git commit -m "feat: add max-extension-size runtime setting with config fallback"
```

---

### Task 2: Prove the existing `/admin/settings` endpoint carries the new field

**Files:**
- Modify: `server/src/test/java/org/eclipse/openvsx/admin/AdminAPITest.java`

**Interfaces:**
- Consumes: `SettingsService.getCurrentSettings()/updateFromJson(SettingsJson)` (Task 1), already injected into `AdminAPI` and already wired to `GET /admin/settings` / `PUT /admin/settings` (`server/src/main/java/org/eclipse/openvsx/admin/AdminAPI.java:1459-1519`, no changes needed there — the endpoints are generic over `SettingsJson`).
- Produces: nothing new (test-only task); confirms no `AdminAPI` code change is required for this field.

- [ ] **Step 1: Write the regression tests**

In `server/src/test/java/org/eclipse/openvsx/admin/AdminAPITest.java`:

Add to the import block (alphabetical position, near the other `org.eclipse.openvsx.json.*` imports):
```java
import org.eclipse.openvsx.json.SettingsJson;
```

Add to the static imports (near the other `MockMvcRequestBuilders` imports):
```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
```

Add two `@Autowired` fields next to the existing ones at lines 198-206 (same pattern as `caches`/`search`, which are also `@MockitoBean` types exposed this way):
```java
    // registered as a mock through the @MockitoBean types list above
    @Autowired
    SettingsService settings;

    // registered as a mock through the @MockitoBean types list above
    @Autowired
    LogService logs;
```

Add two test methods (anywhere among the other `@Test` methods, e.g. right after `testGetSearchIndexWithoutAnIndex`):

```java
    @Test
    void testGetSettingsReportsMaxExtensionSize() throws Exception {
        mockAdminUser();
        var currentSettings = new SettingsJson();
        currentSettings.setReadOnly(false);
        currentSettings.setMaxExtensionSize(536_870_912L);
        Mockito.when(settings.getCurrentSettings()).thenReturn(currentSettings);

        mockMvc.perform(
                        get("/admin/settings")
                                .with(user("admin_user").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                                .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readOnly").value(false))
                .andExpect(jsonPath("$.maxExtensionSize").value(536870912));
    }

    @Test
    void testUpdateSettingsAppliesMaxExtensionSizeAndLogsIt() throws Exception {
        var admin = mockAdminUser();
        Mockito.when(settings.updateFromJson(Mockito.any())).thenReturn("maxExtensionSize -> 1073741824");
        var updatedSettings = new SettingsJson();
        updatedSettings.setReadOnly(false);
        updatedSettings.setMaxExtensionSize(1_073_741_824L);
        Mockito.when(settings.getCurrentSettings()).thenReturn(updatedSettings);

        mockMvc.perform(
                        put("/admin/settings")
                                .with(user("admin_user").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"readOnly\":false,\"maxExtensionSize\":1073741824}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxExtensionSize").value(1073741824));

        Mockito.verify(logs).logAction(Mockito.eq(admin), Mockito.any());
    }
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew test --tests org.eclipse.openvsx.admin.AdminAPITest.testGetSettingsReportsMaxExtensionSize --tests org.eclipse.openvsx.admin.AdminAPITest.testUpdateSettingsAppliesMaxExtensionSizeAndLogsIt`

Expected: PASS (2 tests) immediately — this task adds no production code. `AdminAPI.getSettings`/`updateSettings` (`AdminAPI.java:1459-1519`) are already generic over `SettingsJson`, so once Task 1 lands the `maxExtensionSize` field on `SettingsJson`, these endpoints carry it with no further change. These tests exist to pin that down with an explicit regression test, not to drive new code — there is no red step here.

- [ ] **Step 3: Commit**

```bash
git add server/src/test/java/org/eclipse/openvsx/admin/AdminAPITest.java
git commit -m "test: cover maxExtensionSize in the admin settings endpoint"
```

---

### Task 3: Webui — default max extension size field on the admin Settings page

**Files:**
- Modify: `webui/src/extension-registry-types.ts:629-631`
- Modify: `webui/src/pages/admin-dashboard/settings.tsx`
- Modify: `webui/CHANGELOG.md`
- Test: `webui/test/unit/pages/admin-dashboard/settings.spec.tsx` (new)

**Interfaces:**
- Consumes: `service.admin.getSettings`/`updateSettings` (`webui/src/extension-registry-service.ts:799-800,1598-1623`, unchanged signatures — `Settings` is passed through generically, so no service-layer change is needed).
- Produces: `Settings.maxExtensionSize: number` (bytes) on the shared `Settings` type — the type Task 1's server-side `SettingsJson` serializes to/from.

- [ ] **Step 1: Write the failing test**

Create `webui/test/unit/pages/admin-dashboard/settings.spec.tsx`:

```tsx
/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/

import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RuntimeSettingsPage } from '../../../../src/pages/admin-dashboard/settings';
import { ExtensionRegistryService } from '../../../../src/extension-registry-service';
import { Settings } from '../../../../src/extension-registry-types';
import { renderWithProviders } from '../../support/test-providers';

const settings = (overrides: Partial<Settings> = {}): Settings => ({
    readOnly: false,
    maxExtensionSize: 512 * 1024 * 1024,
    ...overrides
});

const mountPage = (data: Settings, updateSettings = vi.fn().mockResolvedValue(data)) => {
    const admin = {
        getSettings: vi.fn().mockResolvedValue(data),
        updateSettings
    };
    renderWithProviders(<RuntimeSettingsPage />, {
        mainContext: { service: { admin } as unknown as ExtensionRegistryService }
    });
    return admin;
};

describe('RuntimeSettingsPage', () => {
    it('shows the current default max extension size in MB', async () => {
        mountPage(settings({ maxExtensionSize: 512 * 1024 * 1024 }));

        expect(await screen.findByLabelText('Max extension size (MB)')).toHaveValue(512);
    });

    it('disables save once the typed size is not greater than zero', async () => {
        const user = userEvent.setup();
        mountPage(settings());

        const input = await screen.findByLabelText('Max extension size (MB)');
        await user.clear(input);
        await user.type(input, '0');

        expect(screen.getByRole('button', { name: /save/i })).toBeDisabled();
    });

    it('converts the typed MB value to bytes and saves it', async () => {
        const user = userEvent.setup();
        const admin = mountPage(settings({ maxExtensionSize: 512 * 1024 * 1024 }));

        const input = await screen.findByLabelText('Max extension size (MB)');
        await user.clear(input);
        await user.type(input, '1024');

        await user.click(screen.getByRole('button', { name: /save/i }));
        await user.click(await screen.findByRole('button', { name: 'Apply' }));

        await waitFor(() =>
            expect(admin.updateSettings).toHaveBeenCalledWith(
                expect.objectContaining({ maxExtensionSize: 1024 * 1024 * 1024 })
            )
        );
    });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `yarn test test/unit/pages/admin-dashboard/settings.spec.tsx`

Expected: FAIL — `Settings` has no `maxExtensionSize` field yet and the page renders no "Max extension size (MB)" input.

- [ ] **Step 3: Add `maxExtensionSize` to the `Settings` type**

In `webui/src/extension-registry-types.ts`, replace:
```ts
export interface Settings {
    readOnly: boolean;
}
```
with:
```ts
export interface Settings {
    readOnly: boolean;
    /** Default max extension package size in bytes, applied when no namespace/extension override exists. */
    maxExtensionSize: number;
}
```

- [ ] **Step 4: Add the field to the Settings page**

In `webui/src/pages/admin-dashboard/settings.tsx`:

Replace the MUI import block:
```ts
import {
    Alert,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    Paper,
    Stack,
    Typography
} from '@mui/material';
```
with:
```ts
import {
    Alert,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    Paper,
    Stack,
    TextField,
    Typography
} from '@mui/material';
```

Add this constant right after the `NOTIFICATION_TIMEOUT` constant:
```ts
const BYTES_PER_MB = 1024 * 1024;
```

Replace the `hasChanges` computation:
```ts
    const hasChanges =
        draftSettings !== null &&
        settings != null &&
        (Object.keys(SETTINGS) as (keyof Settings)[]).some(k => draftSettings[k] !== settings[k]);
```
with:
```ts
    const hasChanges =
        draftSettings !== null &&
        settings != null &&
        ((Object.keys(SETTINGS) as (keyof Settings)[]).some(k => draftSettings[k] !== settings[k]) ||
            draftSettings.maxExtensionSize !== settings.maxExtensionSize);

    const maxExtensionSizeValid = draftSettings !== null && draftSettings.maxExtensionSize > 0;
```

Add this handler right after `handleFlagChange`'s closing `);`:
```ts
    const handleMaxExtensionSizeChange = useCallback(
        (event: ChangeEvent<HTMLInputElement>) => {
            const mb = Math.max(0, Number.parseInt(event.target.value, 10) || 0);
            setDraftSettings(current => (current ? { ...current, maxExtensionSize: mb * BYTES_PER_MB } : current));
            clearSaved();
        },
        [clearSaved]
    );
```

Replace the `SaveButton` element:
```tsx
                    <SaveButton
                        size='large'
                        saved={saveSuccess}
                        disabled={!hasChanges || saving}
                        onClick={handleSaveClick}
                    />
```
with:
```tsx
                    <SaveButton
                        size='large'
                        saved={saveSuccess}
                        disabled={!hasChanges || saving || !maxExtensionSizeValid}
                        onClick={handleSaveClick}
                    />
```

Insert a new `Paper` block right after the closing `</Paper>` of the boolean-toggle settings list, and before the `<Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>` that holds the Save button:
```tsx
                <Paper variant='outlined' elevation={0} sx={{ p: 3 }}>
                    <Typography variant='subtitle1' gutterBottom>
                        Default max extension size
                    </Typography>
                    <Typography variant='body2' color='text.secondary' sx={{ mb: 2 }}>
                        The largest extension package accepted for publishing when no namespace or extension
                        override applies.
                    </Typography>
                    <TextField
                        label='Max extension size (MB)'
                        type='number'
                        value={draftSettings ? Math.round(draftSettings.maxExtensionSize / BYTES_PER_MB) : ''}
                        onChange={handleMaxExtensionSizeChange}
                        disabled={loading || saving || !draftSettings}
                        error={!maxExtensionSizeValid}
                        helperText={maxExtensionSizeValid ? undefined : 'Must be greater than 0'}
                        inputProps={{ min: '1' }}
                        sx={{ maxWidth: 240 }}
                    />
                </Paper>
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `yarn test test/unit/pages/admin-dashboard/settings.spec.tsx`

Expected: PASS (3 tests).

- [ ] **Step 6: Add the changelog entry**

In `webui/CHANGELOG.md`, add this bullet to the existing `### Added` list under `## [next] (unreleased)` (append after the last existing bullet in that subsection):

```markdown
- Add a default max extension size field to the admin dashboard's Settings page, backed by a new `max-extension-size` runtime setting; falls back to the server's configured `ovsx.publishing.max-content-size` until an admin sets it explicitly ([#2129](https://github.com/eclipse-openvsx/openvsx/issues/2129))
```

- [ ] **Step 7: Lint and commit**

Run: `yarn lint` (fix anything it reports).

```bash
git add webui/src/extension-registry-types.ts \
        webui/src/pages/admin-dashboard/settings.tsx \
        webui/test/unit/pages/admin-dashboard/settings.spec.tsx \
        webui/CHANGELOG.md
git commit -m "feat(webui): add default max extension size field to admin settings"
```

---

## Self-Review Notes

- **Spec coverage:** This plan implements exactly the spec's Phase 1 ("`max-extension-size` Setting key + `SettingsService.getMaxExtensionSize()` + settings API/UI field"). Phases 2-6 (override entity/migration, enforcement, admin CRUD for overrides, webui override page, CLI change, docs) are separate plans, written after this one lands, per the user's chosen "one plan per phase" approach.
- **Precedence behavior:** Task 1's `getMaxExtensionSize()` falls back to `PublishingConfig.getMaxContentSize()` exactly as the spec requires, and the unit tests in Task 1 directly assert both the fallback and the override-wins cases.
- **No AdminAPI code change:** confirmed by reading `AdminAPI.java:1459-1519` — `getSettings`/`updateSettings` are already generic over `SettingsJson`, so Task 2 is test-only, proving rather than building that pass-through.
- **No other Settings-object construction site breaks:** confirmed by searching `webui/src` — only `extension-registry-service.ts` (generic pass-through) and `settings.tsx` construct/consume `Settings`, so adding a required `maxExtensionSize` field doesn't break any other call site.
