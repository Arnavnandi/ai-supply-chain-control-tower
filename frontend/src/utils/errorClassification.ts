export interface ClassifiedError {
  isTimeout: boolean;
  isAuthError: boolean;
  message: string;
}

export const classifyApiError = (err: any, fallbackMessage = 'Failed to load data.'): ClassifiedError => {
  const isTimeout =
    err?.code === 'ECONNABORTED' ||
    Boolean(err?.message && /timeout/i.test(err.message)) ||
    err?.response?.status === 504;

  const status = err?.response?.status;

  if (isTimeout) {
    return {
      isTimeout: true,
      isAuthError: false,
      message: 'Request timed out after 15s. The backend service may be spinning up from idle (cold start). Please click Retry in a few moments.',
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
