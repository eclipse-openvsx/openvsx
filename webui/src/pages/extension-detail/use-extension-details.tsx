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

import { useContext, useState, useEffect, useCallback, useRef } from 'react';
import { MainContext } from '../../context';
import { Extension, isError } from '../../extension-registry-types';
import { ErrorResponse } from '../../server-request';

interface UseExtensionDetailResult {
  loading: boolean;
  extension: Extension | undefined;
  error: Error | undefined;
  icon: string | undefined;
  reload: () => void;
}

export const useExtensionDetail = (
  namespace: string,
  name: string,
  target: string,
  version: string
): UseExtensionDetailResult => {
  const abortController = useRef<AbortController>(new AbortController());

  const { handleError, service } = useContext(MainContext);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error>();
  const [extension, setExtension] = useState<Extension>();
  const [icon, setIcon] = useState<string>();
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    return () => {
      abortController.current.abort();
      if (icon) {
        URL.revokeObjectURL(icon);
      }
    };
  }, []);

  useEffect(() => {
    if (!namespace || !name) return;

    const fetchExtension = async () => {
      setExtension(undefined);
      setError(undefined);
      setIcon(undefined);

      const extensionUrl = service.getExtensionApiUrl({ namespace, name, target, version });
      const response = await service.getExtensionDetail(abortController.current, extensionUrl);
      if (isError(response)) throw response;

      const ext = response as Extension;
      const iconUrl = await service.getExtensionIcon(abortController.current, ext);
      if (abortController.current.signal.aborted) {
        if (iconUrl) {
          URL.revokeObjectURL(iconUrl);
        }
      } else {
        setExtension(ext);
        setIcon(iconUrl);
      }
    };

    setLoading(true);
    fetchExtension()
      .then(() => {
        setLoading(false);
      })
      .catch(err => {
          if (abortController.current.signal.aborted) {
            setLoading(false);
            return;
          }

          const errorResponse = err as Partial<ErrorResponse>;
          if (errorResponse?.status === 404) {
            setError(new Error(`Extension Not Found: ${namespace}.${name}`));
          } else {
            setError(err instanceof Error ? err : new Error('Failed to load extension details'));
            handleError(err as Error | Partial<ErrorResponse>);
          }
          setLoading(false);
      });
  }, [namespace, name, target, version, reloadKey]);

  // This function updates the reloadKey state to trigger a refetch of the extension details.
  const reload = useCallback(() => setReloadKey(k => k + 1), []);

  return { loading, extension, error, icon, reload };
};
