export interface ClassifiedError {
  isTimeout: boolean;
  isAuthError: boolean;
  message: string;
}

export const getTimeoutDurationDisplay = (mode?: string): string => {
  const activeMode = mode !== undefined
    ? mode
    : (typeof import.meta !== 'undefined' ? import.meta.env?.MODE : undefined);
  return activeMode === 'staging' ? '60s' : '15s';
};

export const classifyApiError = (
  err: any,
  fallbackMessage = 'Failed to load data.',
  mode?: string
): ClassifiedError => {
  const isTimeout =
    err?.code === 'ECONNABORTED' ||
    Boolean(err?.message && /timeout/i.test(err.message)) ||
    err?.response?.status === 504;

  const status = err?.response?.status;

  if (isTimeout) {
    const duration = getTimeoutDurationDisplay(mode);
    return {
      isTimeout: true,
      isAuthError: false,
      message: `Request timed out after ${duration}. The backend service may be spinning up from idle (cold start). Please click Retry in a few moments.`,
    };
  }

  if (status === 401) {
    return {
      isTimeout: false,
      isAuthError: true,
      message: err?.response?.data?.message || 'Authentication session expired. Please sign in again.',
    };
  }

  if (status === 403) {
    return {
      isTimeout: false,
      isAuthError: true,
      message: err?.response?.data?.message || 'Access denied. You do not have permission to view this resource.',
    };
  }

  return {
    isTimeout: false,
    isAuthError: false,
    message: err?.response?.data?.message || err?.message || fallbackMessage,
  };
};
