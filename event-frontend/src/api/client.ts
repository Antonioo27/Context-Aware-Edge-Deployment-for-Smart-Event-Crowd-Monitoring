/**
 * Core HTTP client utility for the Event Frontend application.
 * Provides a standardized wrapper around the Fetch API with JSON header handling,
 * error checking, and flexible response deserialization.
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

/**
 * Executes an HTTP request to the specified API endpoint.
 * Automatically injects application/json headers, validates HTTP status codes,
 * and deserializes the body as JSON, returning raw text or an empty object if JSON parsing fails.
 *
 * @param endpoint The relative API endpoint path.
 * @param options Standard RequestInit options for the fetch request.
 * @returns A Promise resolving to the deserialized response body of type T.
 * @throws Error if the HTTP response status is not OK (2xx).
 */
async function fetchClient<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  if (!response.ok) {
    throw new Error(`API Error: ${response.status} ${response.statusText}`);
  }

  const text = await response.text();
  try {
    return text ? JSON.parse(text) : ({} as T);
  } catch {
    return text as unknown as T;
  }
}

export default fetchClient;
