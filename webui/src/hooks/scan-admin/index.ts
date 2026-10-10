/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

// Core hooks
export { useTabNavigation, TAB_DEFINITIONS } from './use-tab-navigation';
export type { UseTabNavigationReturn, TabIndex, TabName } from './use-tab-navigation';

export { useScanFilters } from './use-scan-filters';
export type { UseScanFiltersReturn } from './use-scan-filters';

export { usePagination } from './use-pagination';
export type { UsePaginationReturn } from './use-pagination';

export { useSearch } from './use-search';
export type { UseSearchReturn } from './use-search';

export { useDialogs } from './use-dialogs';
export type { UseDialogsReturn } from './use-dialogs';

// Tab-specific hooks
export { useScansTab } from './use-scans-tab';
export type { UseScansTabReturn } from './use-scans-tab';

export { useQuarantinedTab } from './use-quarantined-tab';
export type { UseQuarantinedTabReturn } from './use-quarantined-tab';

export { useErrorsTab } from './use-errors-tab';
export type { UseErrorsTabReturn } from './use-errors-tab';

export { useAutoRejectedTab } from './use-auto-rejected-tab';
export type { UseAutoRejectedTabReturn } from './use-auto-rejected-tab';

export { useFileListTab, useAllowListTab, useBlockListTab } from './use-file-list-tab';
export type { UseFileListTabReturn, UseAllowListTabReturn, UseBlockListTabReturn } from './use-file-list-tab';

// URL sync hook
export { useUrlSync } from './use-url-sync';
export type { UseUrlSyncReturn } from './use-url-sync';

// Component-specific hooks
export { useScanCardState } from './use-scan-card-state';
