export type SearchDocument = {
  id: string;
  type?: string | null;
  title?: string | null;
  searchText?: string | null;
  shortText?: string | null;
  longText?: string | null;
  birim?: string | null;
  adres?: string | null;
  tarih?: string | null;
  konum?: any;
  metadata?: Record<string, unknown> | null;
  structuredFields?: Record<string, unknown> | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  score?: number;
};

export type TokenMatch = {
  queryToken: string;
  matchedDocToken: string;
  similarity: number;
};

export type RankedResult = {
  rank: number;
  originalScore: number;
  document: SearchDocument;
  tokenMatches?: TokenMatch[] | null;
};

export type SearchStage = {
  method: "BM25" | "SEMANTIC" | "COLBERT" | string;
  tookMs: number;
  results: RankedResult[];
};

export type FusionResult = {
  finalRank: number;
  rrfScore: number;
  bm25Rank: number | null;
  semanticRank: number | null;
  bm25Contribution: number;
  semanticContribution: number;
  document: SearchDocument;
  normalizedBm25Score?: number | null;
  normalizedSemanticScore?: number | null;
};

export type RerankedFusionResult = {
  finalRank: number;
  relevanceScore: number;
  rrfScore: number;
  bm25Rank: number | null;
  semanticRank: number | null;
  document: SearchDocument;
};

export type RerankStage = {
  model: string;
  tookMs: number;
  usedFallback: boolean;
  results: RerankedFusionResult[];
};

export type HybridSettings = {
  limit: number;
  candidateLimit: number;
  candidateMultiplier: number;
  rankConstant: number;
  bm25Weight: number;
  semanticWeight: number;
  types: string[];
  filters: Record<string, unknown>;
  fusionMode?: "SCORE_NORMALIZATION" | "RRF" | string;
};

export type HybridExplainResponse = {
  query: string;
  indexName: string;
  tookMs: number;
  settings: HybridSettings;
  bm25: SearchStage;
  semantic: SearchStage;
  finalResults: FusionResult[];
  totalCandidates: number;
  rerank?: RerankStage | null;
};

export type UiSettings = {
  bm25Weight: number;
  semanticWeight: number;
  limit: number;
  candidateMultiplier: number;
  rankConstant: number;
  indexName: string;
  types: string;
  filters: string;
  semanticMode: "DENSE" | "COLBERT";
};

export type HybridExplainRequest = {
  query: string;
  bm25Weight: number;
  semanticWeight: number;
  limit: number;
  candidateMultiplier: number;
  rankConstant: number;
  indexName?: string;
  types?: string[];
  filters?: Record<string, unknown>;
  semanticMode?: "DENSE" | "COLBERT";
  fusionMode?: string;
};
