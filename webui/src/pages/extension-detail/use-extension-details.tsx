import { useContext, useState, useEffect, useCallback } from 'react';
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
  const { handleError, service } = useContext(MainContext);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error>();
  const [extension, setExtension] = useState<Extension>();
  const [icon, setIcon] = useState<string>();
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!namespace || !name) return;

    const abortController = new AbortController();
    let iconUrl: string | undefined;

    // We're declaring a function to take advantage of async/await syntax for better readability and error handling over Promise chains.
    const fetchExtension = async () => {
      setLoading(true);
      setExtension(undefined);
      setError(undefined);
      setIcon(undefined);
      try {
        const extensionUrl = service.getExtensionApiUrl({ namespace, name, target, version });
        const response = await service.getExtensionDetail(abortController, extensionUrl);
        if (isError(response)) throw response;

        const ext = response as Extension;
        iconUrl = await service.getExtensionIcon(abortController, ext);
        if (abortController.signal.aborted) return;

        setExtension(ext);
        setIcon(iconUrl);
        setLoading(false);
      } catch (err) {
        if (abortController.signal.aborted) return;

        const errorResponse = err as Partial<ErrorResponse>;
        if (errorResponse?.status === 404) {
          setError(new Error(`Extension Not Found: ${namespace}.${name}`));
        } else {
          setError(err instanceof Error ? err : new Error('Failed to load extension details'));
          handleError(err as Error | Partial<ErrorResponse>);
        }
        setLoading(false);
      }
    };

    fetchExtension();
    return () => {
      abortController.abort();
      if (iconUrl) URL.revokeObjectURL(iconUrl);
    };
  }, [namespace, name, target, version, reloadKey]);

  // This function updates the reloadKey state to trigger a refetch of the extension details.
  const reload = useCallback(() => setReloadKey(k => k + 1), []);

  return { loading, extension, error, icon, reload };
};
