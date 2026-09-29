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

import java.util.LinkedHashMap;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import org.eclipse.openvsx.repositories.SettingRepository;
import org.eclipse.openvsx.util.TimeUtil;

import static org.eclipse.openvsx.cache.CacheService.CACHE_SETTING;

@Component
@CacheConfig(cacheManager = "localCacheManager")
public class SettingsCache {

    private final SettingRepository repository;

    public SettingsCache(SettingRepository repository) {
        this.repository = repository;
    }

    /**
     * The whole store in one cache entry, read in a single query. Values keep their stored JSON
     * type: a boolean comes back a Boolean, a string a String. A JSON {@code null} is skipped,
     * since no setting is meaningfully absent-but-present.
     */
    @Cacheable(CACHE_SETTING)
    public SettingRows snapshot() {
        var settings = new LinkedHashMap<String, Object>();
        for (var setting : repository.findAll()) {
            var value = JsonMapper.shared().readValue(setting.getValue(), Object.class);
            if (value != null) {
                settings.put(setting.getKey(), value);
            }
        }
        return new SettingRows(settings);
    }

    /** Jackson does the encoding, since the value column is jsonb rather than text. */
    @Transactional
    @CacheEvict(value = CACHE_SETTING, allEntries = true)
    public void set(String key, Object value) {
        repository.upsert(key, JsonMapper.shared().writeValueAsString(value), TimeUtil.getCurrentUTC());
    }

    @CacheEvict(value = CACHE_SETTING, allEntries = true)
    public void clear() {
    }
}
