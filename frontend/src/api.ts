import type { HybridExplainRequest, HybridExplainResponse } from "./types";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");

export async function explainHybridSearch(
  request: HybridExplainRequest,
  signal?: AbortSignal
): Promise<HybridExplainResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/search/explain`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify(request),
    signal
  });

  if (!response.ok) {
    let message = `Arama servisi ${response.status} hatası döndürdü.`;
    try {
      const body = (await response.json()) as { message?: string };
      if (body.message) message = body.message;
    } catch {
      // Yanıt JSON değilse durum kodunu gösteririz.
    }
    throw new Error(message);
  }

  return response.json() as Promise<HybridExplainResponse>;
}

export async function getOlaylarMeta(): Promise<{
  totalRecords: number;
  types: string[];
  birims: string[];
}> {
  const response = await fetch(`${API_BASE_URL}/api/v1/olaylar/meta`);
  if (!response.ok) throw new Error("Metadata alınamadı");
  return response.json();
}

export async function importOlaylar(limit = 1000, enableDense = false): Promise<any> {
  const response = await fetch(`${API_BASE_URL}/api/v1/olaylar/import?limit=${limit}&enableDenseEmbedding=${enableDense}`, {
    method: "POST"
  });
  if (!response.ok) throw new Error("Yükleme başarısız");
  return response.json();
}

export interface KafkaSimulationStatus {
  running: boolean;
  publishedCount: number;
  targetLimit: number;
  delayMs: number;
  topic: string;
  bootstrapServers: string;
  status: string;
  lastEventSummary: string;
  dbRecordedCount: number;
}

export async function startKafkaSimulation(limit = 1000, delayMs = 50): Promise<any> {
  const response = await fetch(`${API_BASE_URL}/api/v1/simulation/kafka/start?limit=${limit}&delayMs=${delayMs}`, {
    method: "POST"
  });
  if (!response.ok) throw new Error("Simülasyon başlatılamadı");
  return response.json();
}

export async function stopKafkaSimulation(): Promise<any> {
  const response = await fetch(`${API_BASE_URL}/api/v1/simulation/kafka/stop`, {
    method: "POST"
  });
  if (!response.ok) throw new Error("Simülasyon durdurulamadı");
  return response.json();
}

export async function getKafkaSimulationStatus(): Promise<KafkaSimulationStatus> {
  const response = await fetch(`${API_BASE_URL}/api/v1/simulation/kafka/status`);
  if (!response.ok) throw new Error("Durum alınamadı");
  return response.json();
}

