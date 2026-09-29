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

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.eclipse.openvsx.entities.Setting;
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

    @Cacheable(value = CACHE_SETTING, key = "#key")
    public Boolean getBoolean(String key, boolean defaultValue) {
        return repository
                .findByKey(key)
                .map(Setting::getValue)
                .map(Boolean::parseBoolean)
                .orElse(defaultValue);
    }

    @Transactional
    @CacheEvict(value = CACHE_SETTING, key = "#key")
    public void setBoolean(String key, boolean value) {
        repository.upsert(key, String.valueOf(value), TimeUtil.getCurrentUTC());
    }

    @CacheEvict(value = CACHE_SETTING, allEntries = true)
    public void clear() {
    }
}
