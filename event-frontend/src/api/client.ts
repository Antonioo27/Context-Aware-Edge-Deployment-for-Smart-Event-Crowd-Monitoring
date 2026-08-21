const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

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

  // Se la risposta è vuota o testo, non possiamo parsarla come JSON sempre
  const text = await response.text();
  try {
    return text ? JSON.parse(text) : ({} as T);
  } catch {
    return text as unknown as T;
  }
}

export default fetchClient;
