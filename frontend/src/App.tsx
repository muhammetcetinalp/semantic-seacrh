import { useState, useCallback, useRef, useEffect, useMemo } from "react";
import type {
  HybridExplainResponse, HybridExplainRequest, UiSettings,
  FusionResult, RankedResult, RerankedFusionResult, SearchDocument
} from "./types";
import {
  explainHybridSearch, getOlaylarMeta, importOlaylar,
  startKafkaSimulation, stopKafkaSimulation, getKafkaSimulationStatus,
  type KafkaSimulationStatus
} from "./api";

/* ────────────────────────────────────────────────────
   Default settings
──────────────────────────────────────────────────── */
const DEFAULT_SETTINGS: UiSettings = {
  bm25Weight: 0.5,
  semanticWeight: 0.5,
  limit: 10,
  candidateMultiplier: 3,
  rankConstant: 60,
  indexName: "",
  types: "",
  filters: "",
};

/* ────────────────────────────────────────────────────
   Helpers
──────────────────────────────────────────────────── */
function fmt(n: number, decimals = 4): string {
  return n.toFixed(decimals);
}

function sliderStyle(value: number, min: number, max: number): React.CSSProperties {
  const pct = ((value - min) / (max - min)) * 100;
  return { "--pct": `${pct}%` } as React.CSSProperties;
}

function parseTypes(raw: string): string[] | undefined {
  const t = raw.trim();
  if (!t) return undefined;
  return t.split(",").map((s) => s.trim()).filter(Boolean);
}

function parseFilters(raw: string): Record<string, unknown> | undefined {
  const t = raw.trim();
  if (!t) return undefined;
  try { return JSON.parse(t); } catch { return undefined; }
}

/** Tokenize a query string into distinct terms (filtering short words/punctuation) */
function extractQueryTerms(query: string): string[] {
  if (!query) return [];
  const clean = query.toLocaleLowerCase("tr").replace(/['".,!?:;()[\]{}]/g, " ");
  return Array.from(new Set(clean.split(/\s+/).filter(t => t.length > 1)));
}

/**
 * Türkçe Morfolojik Kök Bulucu (Lucene turkish_stemmer kurallarıyla uyumlu).
 * İsim çekim eklerini (çoğul, iyelik, hal/durum ekleri ve türevleri) keserek kökü bulur.
 */
export function stemTurkishWord(word: string): string {
  if (!word || word.length <= 3) return word ? word.toLocaleLowerCase("tr").trim() : "";
  let w = word.toLocaleLowerCase("tr").trim();

  // Kesme işaretlerini temizle (Tekirdağ'da -> Tekirdağ)
  const apostropheIdx = w.indexOf("'");
  if (apostropheIdx > 1) {
    w = w.slice(0, apostropheIdx);
  }

  // 1. Çok heceli / Bileşik çekim ekleri (uzundan kısaya doğru)
  const compoundSuffixes = [
    /(?:lerinde|larında|lerinden|larından|lerine|larına|lerinin|larının|leriyle|larıyla)$/,
    /(?:sinde|sında|sinden|sından|sine|sına|sinin|sının|siyle|sıyla)$/,
    /(?:undaki|ündeki|ındaki|indeki)$/,
    /(?:deki|daki|teki|taki)$/,
    /(?:lerden|lardan|lerde|larda|lerle|larla|lerin|ların)$/,
    /(?:imizden|ımızdan|ümüzden|umuzdan|inizden|ınızdan|ünüzden|unuzdan)$/,
    /(?:imizde|ımızda|ümüzde|umuzda|inizde|ınızda|ünüzde|unuzda)$/,
    /(?:imize|ımıza|ümüze|umuza|inize|ınıza|ünüze|unuza)$/,
    /(?:imizin|ımızın|ümüzün|umuzun|inizin|ınızın|ünüzün|unuzun)$/,
    /(?:imiz|ımız|ümüz|umuz|iniz|ınız|ünüz|unuz)$/
  ];

  for (const re of compoundSuffixes) {
    const stripped = w.replace(re, "");
    if (stripped.length >= 3) {
      w = stripped;
      break;
    }
  }

  // 2. Basit çekim ekleri
  const simpleSuffixes = [
    /(?:den|dan|ten|tan)$/,
    /(?:de|da|te|ta)$/,
    /(?:ye|ya|e|a)$/,
    /(?:yi|yı|yu|yü|i|ı|u|ü)$/,
    /(?:nin|nın|nun|nün|in|ın|un|ün)$/,
    /(?:yle|yla|le|la)$/,
    /(?:ler|lar)$/,
    /(?:si|sı|su|sü)$/,
    /(?:im|ım|um|üm|in|ın|un|ün)$/
  ];

  for (const re of simpleSuffixes) {
    const stripped = w.replace(re, "");
    const minLen = re.source.includes("a") || re.source.includes("e") || re.source.includes("i") ? 4 : 3;
    if (stripped.length >= minLen) {
      w = stripped;
      break;
    }
  }

  // 3. Yumuşayan ünsüzleri normalize et (ağacı -> ağaç, kanadı -> kanat, balığı -> balık, kitabı -> kitap)
  if (w.endsWith("c") && w.length >= 3) w = w.slice(0, -1) + "ç";
  else if (w.endsWith("d") && w.length >= 3) w = w.slice(0, -1) + "t";
  else if (w.endsWith("ğ") && w.length >= 3) w = w.slice(0, -1) + "k";
  else if (w.endsWith("b") && w.length >= 3) w = w.slice(0, -1) + "p";

  return w;
}

/** Highlight query terms and stemmed root forms inside a text snippet */
function highlightTerms(text: string, query: string): React.ReactNode {
  if (!query || !text) return text;
  const terms = extractQueryTerms(query);
  if (terms.length === 0) return text;

  const termsToHighlight = new Set<string>();
  for (const t of terms) {
    termsToHighlight.add(t);
    const stem = stemTurkishWord(t);
    if (stem.length >= 3) {
      termsToHighlight.add(stem);
    }
  }

  const wordsInText = text.replace(/['".,!?:;()[\]{}]/g, " ").split(/\s+/).filter(w => w.length > 1);
  for (const w of wordsInText) {
    const wLower = w.toLocaleLowerCase("tr");
    const wStem = stemTurkishWord(wLower);
    for (const t of terms) {
      const tStem = stemTurkishWord(t);
      if (
        tStem.length >= 3 &&
        (wStem === tStem ||
         (tStem.length >= 4 && wLower.startsWith(tStem)) ||
         (t.length >= 4 && wLower.includes(t)) ||
         (tStem.length >= 4 && wLower.includes(tStem)))
      ) {
        termsToHighlight.add(wLower);
      }
    }
  }

  const escaped = Array.from(termsToHighlight)
    .sort((a, b) => b.length - a.length)
    .map(t => t.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
    .join("|");

  if (!escaped) return text;

  const pattern = new RegExp(`(${escaped})`, "giu");
  const testRegex = new RegExp(`^(${escaped})$`, "iu");
  const parts = text.split(pattern);
  return parts.map((part, i) => {
    if (testRegex.test(part)) {
      const partLower = part.toLocaleLowerCase("tr");
      const isExactQuery = terms.includes(partLower);
      return (
        <mark
          key={i}
          className={`hl-term ${isExactQuery ? "hl-exact" : "hl-stem"}`}
          title={isExactQuery ? `Tam kelime eşleşmesi: "${part}"` : `Kök eşleşmesi (turkish_stemmer)`}
        >
          {part}
        </mark>
      );
    }
    return part;
  });
}

/**
 * Akıllı Snippet Çıkarıcı:
 * 1. doc.shortText içinde sorgu kelimesi veya Türkçe kök geçiyorsa doğrudan shortText döner.
 * 2. Eğer shortText içinde geçmiyorsa (kelime sadece detaylı longText veya searchText'te geçiyorsa),
 *    metin taranarak kelimenin/kökün geçtiği cümlenin etrafından (~160 karakterlik) bir pasaj çıkarılır.
 *    Böylece arama sonuç kartında eşleşen kelime MUTLAKA görünür ve vurgulanır.
 * 3. Hiç eşleşme bulunamazsa varsayılan metin döner.
 */
function getSmartSnippet(doc: SearchDocument, query: string): string {
  const terms = extractQueryTerms(query);
  const short = doc.shortText || "";
  const long = doc.longText || doc.searchText || "";

  if (terms.length === 0) {
    return short || (long.length > 160 ? long.slice(0, 160) + "..." : long);
  }

  // 1. Check if shortText already contains any query term or Turkish stem
  const shortLower = short.toLocaleLowerCase("tr");
  const stems = terms.map(t => stemTurkishWord(t)).filter(s => s.length >= 3);
  const allMatchCandidates = [...terms, ...stems];

  const shortMatches = allMatchCandidates.some(c => shortLower.includes(c));
  if (shortMatches && short.trim().length > 0) {
    return short;
  }

  // 2. If shortText doesn't have the term, search in longText / searchText
  if (long.trim().length > 0) {
    const longLower = long.toLocaleLowerCase("tr");
    let bestIdx = -1;
    let matchLen = 0;

    for (const c of allMatchCandidates) {
      const idx = longLower.indexOf(c);
      if (idx !== -1 && (bestIdx === -1 || idx < bestIdx)) {
        bestIdx = idx;
        matchLen = c.length;
      }
    }

    if (bestIdx !== -1) {
      const start = Math.max(0, bestIdx - 50);
      const end = Math.min(long.length, bestIdx + matchLen + 110);
      let excerpt = long.slice(start, end).trim();
      if (start > 0) excerpt = "..." + excerpt;
      if (end < long.length) excerpt = excerpt + "...";
      return excerpt;
    }
  }

  // 3. Fallback
  return short || (long.length > 160 ? long.slice(0, 160) + "..." : long);
}

interface TermMatchInfo {
  term: string;
  stem: string;
  inTitle: boolean;
  textCount: number;
  matchedForms: string[];
  isExact: boolean;
  isStemMatch: boolean;
}

/** Analyze how query terms matched inside a document, including Turkish stem matching */
function analyzeTermMatches(doc: SearchDocument, query: string): { matched: TermMatchInfo[]; missing: string[] } {
  const terms = extractQueryTerms(query);
  const titleLower = (doc.title || "").toLocaleLowerCase("tr");
  const textContent = [doc.shortText, doc.longText, doc.searchText, doc.adres, doc.birim, doc.type].filter(Boolean).join(" ");
  const textLower = textContent.toLocaleLowerCase("tr");

  const allDocText = `${titleLower} ${textLower}`;
  const docTokens = Array.from(new Set(allDocText.replace(/['".,!?:;()[\]{}]/g, " ").split(/\s+/).filter(t => t.length > 1)));

  const matched: TermMatchInfo[] = [];
  const missing: string[] = [];

  for (const term of terms) {
    const stem = stemTurkishWord(term);
    const inTitleExact = titleLower.includes(term);

    const matchedFormsSet = new Set<string>();
    let textCount = 0;

    for (const token of docTokens) {
      const tokenStem = stemTurkishWord(token);
      const isExact = token === term;
      const isSameStem = (stem.length >= 3 && tokenStem === stem) ||
                         (stem.length >= 3 && token.length >= stem.length && token.startsWith(stem)) ||
                         (term.length >= 3 && token.length >= term.length && token.includes(term));

      if (isExact || isSameStem) {
        matchedFormsSet.add(token);
      }
    }

    const matchedForms = Array.from(matchedFormsSet);
    for (const form of matchedForms) {
      if (textLower) {
        let pos = textLower.indexOf(form);
        while (pos !== -1 && textCount < 20) {
          textCount++;
          pos = textLower.indexOf(form, pos + form.length);
        }
      }
    }

    const inTitle = titleLower.includes(term) || matchedForms.some(f => titleLower.includes(f));
    const isExact = inTitleExact || (textLower && textLower.includes(term));

    if (inTitle || textCount > 0) {
      matched.push({
        term,
        stem,
        inTitle,
        textCount,
        matchedForms,
        isExact: !!isExact,
        isStemMatch: !isExact && matchedForms.length > 0
      });
    } else {
      missing.push(term);
    }
  }

  return { matched, missing };
}

/** Score bar with normalized percentage fill */
function ScoreBar({ value, max, variant }: { value: number; max: number; variant: string }) {
  const pct = max > 0 ? Math.min((value / max) * 100, 100) : 0;
  return (
    <div className="score-bar-wrap">
      <div className="score-bar-bg">
        <div className={`score-bar-fill ${variant}`} style={{ width: `${pct}%` }} />
      </div>
      <span className="score-bar-val">{fmt(value, 3)}</span>
    </div>
  );
}

/* ────────────────────────────────────────────────────
   Algorithm Diagnostic / Overlap Banner
──────────────────────────────────────────────────── */
function SearchDiagnosticBanner({ result }: { result: HybridExplainResponse }) {
  const bm25Ids = useMemo(() => new Set(result.bm25.results.map(r => r.document.id)), [result]);
  const semIds = useMemo(() => new Set(result.semantic.results.map(r => r.document.id)), [result]);

  const bothCount = useMemo(() => {
    let count = 0;
    for (const id of bm25Ids) {
      if (semIds.has(id)) count++;
    }
    return count;
  }, [bm25Ids, semIds]);

  const bm25OnlyCount = bm25Ids.size - bothCount;
  const semOnlyCount = semIds.size - bothCount;

  return (
    <div className="diagnostic-bar">
      <div className="diagnostic-card both">
        <div className="diagnostic-indicator both" />
        <div className="diagnostic-info">
          <span className="diagnostic-label">Ortak Adaylar</span>
          <span className="diagnostic-count">{bothCount}</span>
          <span className="diagnostic-sub">BM25 ve Semantik</span>
        </div>
      </div>

      <div className="diagnostic-card bm25">
        <div className="diagnostic-indicator bm25" />
        <div className="diagnostic-info">
          <span className="diagnostic-label">Yalnızca BM25</span>
          <span className="diagnostic-count">{bm25OnlyCount}</span>
          <span className="diagnostic-sub">Sözcüksel tam eşleşme</span>
        </div>
      </div>

      <div className="diagnostic-card sem">
        <div className="diagnostic-indicator sem" />
        <div className="diagnostic-info">
          <span className="diagnostic-label">Yalnızca Semantik</span>
          <span className="diagnostic-count">{semOnlyCount}</span>
          <span className="diagnostic-sub">Vektörel anlamsal yakınlık</span>
        </div>
      </div>

      {result.rerank && (
        <div className="diagnostic-card rerank">
          <div className="diagnostic-indicator rerank" />
          <div className="diagnostic-info">
            <span className="diagnostic-label">Cross-Encoder</span>
            <span className="diagnostic-count">{result.rerank.results.length}</span>
            <span className="diagnostic-sub">{(result.rerank.model.includes("Tei") ? "bge-reranker-v2-m3" : result.rerank.model)} · {result.rerank.tookMs}ms</span>
          </div>
        </div>
      )}
    </div>
  );
}

/* ────────────────────────────────────────────────────
   Algorithm info panel
──────────────────────────────────────────────────── */
function AlgoInfo({
  variant,
  query,
  model,
  resultCount,
  tookMs
}: {
  variant: "bm25" | "sem" | "rrf" | "normalized" | "rerank";
  query: string;
  model?: string;
  resultCount?: number;
  tookMs?: number;
}) {
  const terms = extractQueryTerms(query);

  if (variant === "bm25") {
    return (
      <div className="algo-info bm25">
        <div className="algo-info-row">
          <span className="algo-info-label">ALGORİTMA</span>
          <span className="algo-info-val"><strong>Okapi BM25</strong> (k1=1.2, b=0.75) — Terim sıklığı (TF) ve ters belge sıklığı (IDF)</span>
        </div>
        <div className="algo-info-row">
          <span className="algo-info-label">ANALYZER</span>
          <span className="algo-info-val"><strong>turkish_search</strong> (Standard tokenizer + küçük harf + kesme imi + stop-words + stemmer)</span>
        </div>
        <div className="algo-info-row">
          <span className="algo-info-label">ARANAN TERİMLER</span>
          <div className="term-chips">
            {terms.map(t => (
              <span key={t} className="term-chip bm25">{t}</span>
            ))}
            {terms.length === 0 && <span className="term-chip">Tüm kelimeler</span>}
          </div>
        </div>
        <div className="algo-info-row">
          <span className="algo-info-label">KÖK ANALİZİ</span>
          <div className="term-chips">
            {terms.map(t => {
              const stem = stemTurkishWord(t);
              const hasSuffix = stem !== t.toLowerCase();
              return (
                <span key={t} className="term-chip stem" title={hasSuffix ? `"${t}" kelimesinden Türkçe çekim ekleri kesilerek kök "${stem}" bulundu.` : `Yalın kök: "${stem}"`}>
                  {t} <span className="stem-arrow">→</span> <strong>{stem}</strong>
                  {hasSuffix && <span className="stem-badge">kök</span>}
                </span>
              );
            })}
          </div>
        </div>
        <div className="algo-info-row">
          <span className="algo-info-label">TARANAN ALANLAR</span>
          <span className="algo-info-val">title^3.0 · birim^2.0 · adres^2.0 · type^2.0 · short/longText^2.0</span>
        </div>
        <div className="algo-info-row">
          <span className="algo-info-label">SONUÇ HAVUZU</span>
          <span className="algo-info-val"><strong>{resultCount ?? 0} aday döküman</strong> ({tookMs ?? 0} ms) — Terim frekansı ve alan ağırlığına göre puanlandı</span>
        </div>
      </div>
    );
  }

  if (variant === "sem") {
    return (
      <div className="algo-info sem">
        <div className="algo-info-row">
          <span className="algo-info-label">MODEL</span>
          <span className="algo-info-val"><strong>{model || "BAAI/bge-m3"}</strong> (Çok dilli, 1024 boyutlu yoğun gömme)</span>
        </div>
        <div className="algo-info-row">
          <span className="algo-info-label">VEKTÖRLEŞTİRME</span>
          <span className="algo-info-val">Sorgu: "{query}" → <strong>float[1024]</strong> yoğun uzaya haritalandı</span>
        </div>
        <div className="algo-info-row">
          <span className="algo-info-label">BENZERLİK METRİĞİ</span>
          <span className="algo-info-val"><strong>Kosinüs Benzerliği (Cosine Similarity)</strong> · HNSW grafiği (ef_search=256, m=16)</span>
        </div>
        <div className="algo-info-row">
          <span className="algo-info-label">SONUÇ HAVUZU</span>
          <span className="algo-info-val"><strong>{resultCount ?? 0} aday döküman</strong> ({tookMs ?? 0} ms) — Anlamsal yakınlıktan yakalandı</span>
        </div>
      </div>
    );
  }

  if (variant === "rrf") {
    return (
      <div className="algo-info rrf">
        <div className="algo-info-row">
          <span className="algo-info-label">FORMÜL</span>
          <span className="algo-info-val rrf-formula-display">
            RRF(d) = w_BM25 / (k + rank_BM25) + w_SEM / (k + rank_SEM)
          </span>
        </div>
        <div className="algo-info-row">
          <span className="algo-info-label">ÇALIŞMA MANTIĞI</span>
          <span className="algo-info-val">BM25 ve Semantik skorlarının farklı dağılımları sıralama derecesine (rank) çevrilerek adilce birleştirildi</span>
        </div>
      </div>
    );
  }

  if (variant === "normalized") {
    return (
      <div className="algo-info rrf">
        <div className="algo-info-row">
          <span className="algo-info-label">FORMÜL</span>
          <span className="algo-info-val rrf-formula-display">
            Skor(d) = (w_BM25 × S_BM25_norm) + (w_SEM × S_SEM_norm)
          </span>
        </div>
        <div className="algo-info-row">
          <span className="algo-info-label">ÇALIŞMA MANTIĞI</span>
          <span className="algo-info-val">BM25 ve Semantik ham skorları [0, 1] aralığına normalize edildi; ağırlıklarıyla toplandı</span>
        </div>
      </div>
    );
  }

  return (
    <div className="algo-info rerank">
      <div className="algo-info-row">
        <span className="algo-info-label">RERANKER MODELİ</span>
        <span className="algo-info-val"><strong>{model || "BAAI/bge-reranker-v2-m3"}</strong> (Cross-Encoder / Ortak Dikkat Mekanizması)</span>
      </div>
      <div className="algo-info-row">
        <span className="algo-info-label">ÇALIŞMA MANTIĞI</span>
        <span className="algo-info-val">Sorgu ve döküman metni birlikte modele verildi; derin dil anlayışıyla alaka skoru hesaplandı</span>
      </div>
    </div>
  );
}

/* ────────────────────────────────────────────────────
   Expanded detail panel for a result card
──────────────────────────────────────────────────── */
function ResultDetail({
  doc,
  query
}: {
  doc: SearchDocument;
  query: string;
}) {
  const matchAnalysis = analyzeTermMatches(doc, query);

  const formattedKonum = doc.konum
    ? typeof doc.konum === "object"
      ? `${(doc.konum as any).lat}, ${(doc.konum as any).lon}`
      : String(doc.konum)
    : null;

  return (
    <div className="result-detail">
      {/* Incident Fields Card */}
      {(doc.birim || doc.tarih || doc.adres || formattedKonum) && (
        <div className="incident-detail-box">
          <div className="incident-detail-grid">
            {doc.birim && (
              <div className="incident-detail-item">
                <span className="lbl">Görevli Birim</span>
                <span className="val" style={{ color: "#fb923c" }}>{doc.birim}</span>
              </div>
            )}
            {doc.tarih && (
              <div className="incident-detail-item">
                <span className="lbl">Olay Tarihi</span>
                <span className="val" style={{ color: "#fcd34d" }}>{doc.tarih.replace("T", " ").replace("Z", "")}</span>
              </div>
            )}
            {doc.adres && (
              <div className="incident-detail-item">
                <span className="lbl">Olay Yeri / Adres</span>
                <span className="val">{highlightTerms(doc.adres, query)}</span>
              </div>
            )}
            {formattedKonum && (
              <div className="incident-detail-item">
                <span className="lbl">GPS Koordinatı</span>
                <span className="val" style={{ fontFamily: "monospace", color: "#fed7aa" }}>{formattedKonum}</span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Short Text if available */}
      {doc.shortText && (
        <div className="detail-section">
          <div className="detail-label">ÖZET BİLGİ</div>
          <div className="detail-text" style={{ fontStyle: "italic" }}>{highlightTerms(doc.shortText, query)}</div>
        </div>
      )}

      {/* Long Text if available */}
      {doc.longText && (
        <div className="detail-section">
          <div className="detail-label">AYRINTILI RAPOR</div>
          <div className="detail-text" style={{ whiteSpace: "pre-wrap" }}>{highlightTerms(doc.longText, query)}</div>
        </div>
      )}

      {/* Standard SearchText (for fallback/generic docs) */}
      {!doc.shortText && !doc.longText && doc.searchText && (
        <div className="detail-section">
          <div className="detail-label">DÖKÜMAN İÇERİĞİ</div>
          <div className="detail-text">{highlightTerms(doc.searchText, query)}</div>
        </div>
      )}

      {/* Term match analysis */}
      <div className="detail-section">
        <div className="detail-label">TERİM & KÖK ANALİZİ</div>
        <div className="match-pills">
          {matchAnalysis.matched.map(m => (
            <span
              key={m.term}
              className={`match-pill ${m.isStemMatch ? "stem-match" : "matched"}`}
              title={
                m.isStemMatch
                  ? `Kök eşleşmesi: "${m.term}" → "${m.stem}" ile dökümandaki [${m.matchedForms.join(", ")}] eşleşti.`
                  : `Tam kelime eşleşmesi: "${m.term}"`
              }
            >
              {m.isStemMatch ? (
                <>
                  "{m.term}" <span className="stem-arrow">→</span> Kök: <span className="stem-root">{m.stem}</span>
                  <span className="stem-detail">
                    (Dökümanda: {m.matchedForms.join(", ")} {m.textCount > 0 ? `· ${m.textCount}x` : ""} {m.inTitle ? "· Başlıkta" : ""})
                  </span>
                </>
              ) : (
                <>
                  "{m.term}" → {m.inTitle ? "Başlıkta" : ""}{m.inTitle && m.textCount > 0 ? " + " : ""}{m.textCount > 0 ? `${m.textCount}x İçerikte` : ""}
                </>
              )}
            </span>
          ))}
          {matchAnalysis.missing.map(term => (
            <span key={term} className="match-pill missing" title="Bu dökümanda bulunamadı">
              "{term}" (bulunmadı)
            </span>
          ))}
        </div>
      </div>

      {doc.type && (
        <div className="detail-row">
          <span className="detail-label">Tür:</span>
          <span className="detail-badge">{doc.type}</span>
        </div>
      )}

      {doc.metadata && Object.keys(doc.metadata).length > 0 && (
        <div className="detail-section">
          <div className="detail-label">METADATA</div>
          <div className="meta-grid">
            {Object.entries(doc.metadata).map(([k, v]) => (
              <div key={k} className="meta-entry">
                <span className="meta-key">{k}</span>
                <span className="meta-val">{String(v)}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="detail-row">
        <span className="detail-label">ID:</span>
        <span className="detail-id">{doc.id}</span>
      </div>
    </div>
  );
}

function getDocDisplayTitle(doc: SearchResult): string {
  if (doc.title && doc.title.trim().length > 0) return doc.title;
  if (doc.shortText && doc.shortText.trim().length > 0) {
    return doc.shortText.length > 90 ? doc.shortText.slice(0, 90) + "..." : doc.shortText;
  }
  return doc.id ? `Olay (${doc.id.slice(0, 8)}...)` : "İsimsiz Olay";
}

/* ────────────────────────────────────────────────────
   BM25 / Semantic result card
──────────────────────────────────────────────────── */
function RankedResultRow({
  item, variant, maxScore, query
}: {
  item: RankedResult;
  variant: "bm25" | "sem";
  maxScore: number;
  query: string;
}) {
  const [expanded, setExpanded] = useState(false);
  const doc = item.document;
  const matchAnalysis = useMemo(() => analyzeTermMatches(doc, query), [doc, query]);
  const isPureSemantic = variant === "sem" && matchAnalysis.matched.length === 0;

  return (
    <div className={`result-row ${expanded ? "expanded" : ""}`}>
      <div className="result-rank">
        <span className={`rank-number ${variant}`}>#{item.rank}</span>
      </div>

      <div className="result-body">
        <button className="result-title-btn" onClick={() => setExpanded(e => !e)}>
          <span className="result-title">{highlightTerms(getDocDisplayTitle(doc), query)}</span>
          <span className="expand-arrow">{expanded ? "Kapat" : "Detay"}</span>
        </button>

        {(doc.birim || doc.tarih || doc.konum) && (
          <div className="incident-header-meta">
            {doc.birim && <span className="unit-badge">{doc.birim}</span>}
            {doc.tarih && <span className="date-badge">{doc.tarih.replace("T", " ").replace("Z", "")}</span>}
            {doc.konum && (
              <span className="geo-badge">
                {typeof doc.konum === "object" ? `${(doc.konum as any).lat}, ${(doc.konum as any).lon}` : String(doc.konum)}
              </span>
            )}
          </div>
        )}
        {!expanded && (
          <div className="incident-snippet">
            {highlightTerms(getSmartSnippet(doc, query), query)}
          </div>
        )}

        {/* Score bar */}
        <ScoreBar value={item.originalScore} max={maxScore} variant={variant} />

        {/* BM25: Show which words matched (both exact and turkish_stemmer roots) */}
        {variant === "bm25" && (
          <div className="match-pills">
            {matchAnalysis.matched.map(m => (
              <span
                key={m.term}
                className={`match-pill ${m.isStemMatch ? "stem-match" : "matched"}`}
                title={
                  m.isStemMatch
                    ? `Kök Eşleşmesi: Sorgu "${m.term}" kökü "${m.stem}" ile dökümandaki [${m.matchedForms.join(", ")}] eşleşti (${m.textCount} kez).`
                    : `Tam Kelime Eşleşmesi: Başlık: ${m.inTitle}, İçerik: ${m.textCount} kez`
                }
              >
                {m.isStemMatch ? (
                  <>
                    {m.term} <span className="stem-arrow">→</span> <span className="stem-root">{m.stem}</span>
                    <span className="stem-detail">({m.matchedForms.slice(0, 2).join(", ")} {m.textCount > 0 ? `${m.textCount}x` : ""})</span>
                  </>
                ) : (
                  <>
                    {m.term} {m.inTitle ? "(başlık)" : m.textCount > 0 ? `(${m.textCount}x)` : ""}
                  </>
                )}
              </span>
            ))}
            {matchAnalysis.missing.map(term => (
              <span key={term} className="match-pill missing" title="Bu dökümanda bulunamadı">
                {term} (yok)
              </span>
            ))}
          </div>
        )}

        {/* Semantic: Show vector similarity badge */}
        {variant === "sem" && (
          <div className="match-pills">
            <span className="semantic-pill">
              Kosinüs: %{(Math.min(item.originalScore, 1) * 100).toFixed(1)}
            </span>
            {isPureSemantic ? (
              <span className="semantic-pill pure" title="Aranan terimler metinde doğrudan geçmese de anlamsal yakınlıktan bulundu">
                Vektör Benzerliği (Terim doğrudan geçmiyor)
              </span>
            ) : matchAnalysis.matched.length > 0 ? (
              <span className="match-pill matched">
                {matchAnalysis.matched.length} terim örtüşüyor
              </span>
            ) : null}
          </div>
        )}

        {expanded && <ResultDetail doc={doc} query={query} />}
      </div>

      <div className="result-meta">
        <span className={`score-badge ${variant}`}>
          {variant === "bm25"
            ? fmt(item.originalScore, 2)
            : fmt(item.originalScore, 4)}
        </span>
        {doc.type && <span className="type-badge">{doc.type}</span>}
      </div>
    </div>
  );
}

/* ────────────────────────────────────────────────────
   Fusion result card with visual contribution
──────────────────────────────────────────────────── */
function FusionResultRow({
  item,
  query,
  settings,
}: {
  item: FusionResult;
  query: string;
  settings?: HybridSettings;
}) {
  const [expanded, setExpanded] = useState(false);
  const doc = item.document;
  const maxContrib = Math.max(item.bm25Contribution, item.semanticContribution, 0.0001);

  const bm25Weight = settings?.bm25Weight ?? 0.5;
  const semanticWeight = settings?.semanticWeight ?? 0.5;
  const rankConstant = settings?.rankConstant ?? 60;

  return (
    <div className={`result-row ${expanded ? "expanded" : ""}`}>
      <div className="result-rank">
        <span className="rank-number rrf">#{item.finalRank}</span>
      </div>

      <div className="result-body">
        <button className="result-title-btn" onClick={() => setExpanded(e => !e)}>
          <span className="result-title">{highlightTerms(getDocDisplayTitle(doc), query)}</span>
          <span className="expand-arrow">{expanded ? "Kapat" : "Detay"}</span>
        </button>

        {(doc.birim || doc.tarih || doc.konum) && (
          <div className="incident-header-meta">
            {doc.birim && <span className="unit-badge">{doc.birim}</span>}
            {doc.tarih && <span className="date-badge">{doc.tarih.replace("T", " ").replace("Z", "")}</span>}
            {doc.konum && (
              <span className="geo-badge">
                {typeof doc.konum === "object" ? `${(doc.konum as any).lat}, ${(doc.konum as any).lon}` : String(doc.konum)}
              </span>
            )}
          </div>
        )}
        {!expanded && (
          <div className="incident-snippet">
            {highlightTerms(getSmartSnippet(doc, query), query)}
          </div>
        )}

        {/* Rank pills showing source positions */}
        <div className="rank-pills">
          {item.bm25Rank != null
            ? <span className="rank-pill bm25">BM25 #{item.bm25Rank}</span>
            : <span className="rank-pill miss">BM25 —</span>}
          {item.semanticRank != null
            ? <span className="rank-pill sem">SEM #{item.semanticRank}</span>
            : <span className="rank-pill miss">SEM —</span>}
        </div>

        {/* Contribution bars */}
        <div className="contrib-bar-wrap">
          <div className="contrib-bar-row">
            <span className="contrib-bar-label bm25">BM25</span>
            <div className="contrib-bar-bg">
              <div className="contrib-bar-fill bm25" style={{ width: `${(item.bm25Contribution / maxContrib) * 100}%` }} />
            </div>
            <span className="contrib-val">{fmt(item.bm25Contribution, 5)}</span>
          </div>
          <div className="contrib-bar-row">
            <span className="contrib-bar-label sem">SEM</span>
            <div className="contrib-bar-bg">
              <div className="contrib-bar-fill sem" style={{ width: `${(item.semanticContribution / maxContrib) * 100}%` }} />
            </div>
            <span className="contrib-val">{fmt(item.semanticContribution, 5)}</span>
          </div>
        </div>

        {/* Formula breakdown */}
        <div className="rrf-formula">
          RRF = {item.bm25Rank != null ? `${bm25Weight.toFixed(2)}/(${rankConstant}+${item.bm25Rank})` : "0"} + {item.semanticRank != null ? `${semanticWeight.toFixed(2)}/(${rankConstant}+${item.semanticRank})` : "0"} = {fmt(item.rrfScore, 5)}
        </div>

        {expanded && <ResultDetail doc={doc} query={query} />}
      </div>

      <div className="result-meta">
        <span className="score-badge rrf">{fmt(item.rrfScore, 5)}</span>
        {doc.type && <span className="type-badge">{doc.type}</span>}
      </div>
    </div>
  );
}

/* ────────────────────────────────────────────────────
   Reranker result card with delta rank
──────────────────────────────────────────────────── */
function RerankedResultRow({
  item,
  query,
  rrfRank
}: {
  item: RerankedFusionResult;
  query: string;
  rrfRank?: number;
}) {
  const [expanded, setExpanded] = useState(false);
  const doc = item.document;

  const delta = rrfRank != null ? rrfRank - item.finalRank : 0;

  return (
    <div className={`result-row ${expanded ? "expanded" : ""}`}>
      <div className="result-rank">
        <span className="rank-number rerank">#{item.finalRank}</span>
        {delta > 0 && <span className="rank-delta up">+{delta}</span>}
        {delta < 0 && <span className="rank-delta down">{delta}</span>}
        {delta === 0 && <span className="rank-delta same">0</span>}
      </div>

      <div className="result-body">
        <button className="result-title-btn" onClick={() => setExpanded(e => !e)}>
          <span className="result-title">{highlightTerms(getDocDisplayTitle(doc), query)}</span>
          <span className="expand-arrow">{expanded ? "Kapat" : "Detay"}</span>
        </button>

        {(doc.birim || doc.tarih || doc.konum) && (
          <div className="incident-header-meta">
            {doc.birim && <span className="unit-badge">{doc.birim}</span>}
            {doc.tarih && <span className="date-badge">{doc.tarih.replace("T", " ").replace("Z", "")}</span>}
            {doc.konum && (
              <span className="geo-badge">
                {typeof doc.konum === "object" ? `${(doc.konum as any).lat}, ${(doc.konum as any).lon}` : String(doc.konum)}
              </span>
            )}
          </div>
        )}
        {!expanded && (
          <div className="incident-snippet">
            {highlightTerms(getSmartSnippet(doc, query), query)}
          </div>
        )}

        <div className="rerank-scores">
          <div className="rerank-score-row">
            <span className="algo-info-label">Cross-encoder:</span>
            <ScoreBar value={item.relevanceScore} max={1} variant="rerank" />
          </div>
          <div className="rerank-score-row">
            <span className="algo-info-label">RRF Sırası:</span>
            <span className="score-muted">#{rrfRank ?? "—"} (RRF Skor: {fmt(item.rrfScore, 5)})</span>
          </div>
        </div>

        <div className="rank-pills">
          {item.bm25Rank != null
            ? <span className="rank-pill bm25">BM25 #{item.bm25Rank}</span>
            : <span className="rank-pill miss">BM25 —</span>}
          {item.semanticRank != null
            ? <span className="rank-pill sem">SEM #{item.semanticRank}</span>
            : <span className="rank-pill miss">SEM —</span>}
        </div>

        {expanded && <ResultDetail doc={doc} query={query} />}
      </div>

      <div className="result-meta">
        <span className="score-badge rerank">{fmt(item.relevanceScore, 4)}</span>
        {doc.type && <span className="type-badge">{doc.type}</span>}
      </div>
    </div>
  );
}

/* Skeleton loading */
function SkeletonResults() {
  return (
    <div style={{ padding: "14px 18px" }}>
      {[1, 2, 3, 4].map((i) => (
        <div key={i} className="skeleton skeleton-block" style={{ animationDelay: `${i * 0.08}s` }} />
      ))}
    </div>
  );
}

/* Flow separator */
function FlowArrow({ label }: { label: string }) {
  return (
    <div className="flow-arrow">
      <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
        <path d="M12 5v14M5 12l7 7 7-7" />
      </svg>
      <span style={{ marginLeft: 8, fontWeight: 600 }}>{label}</span>
    </div>
  );
}

/* ────────────────────────────────────────────────────
   Main App Component
──────────────────────────────────────────────────── */
export default function App() {
  const [query, setQuery] = useState("");
  const [settings, setSettings] = useState<UiSettings>(DEFAULT_SETTINGS);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<HybridExplainResponse | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Incident dataset & filter states
  const [olaylarMeta, setOlaylarMeta] = useState<{ totalCount: number; types: string[]; birimler: string[] } | null>(null);
  const [selectedType, setSelectedType] = useState<string>("");
  const [selectedBirim, setSelectedBirim] = useState<string>("");
  const [startDate, setStartDate] = useState<string>("");
  const [endDate, setEndDate] = useState<string>("");
  const [importLoading, setImportLoading] = useState(false);
  const [importLimit, setImportLimit] = useState(1000);
  const [importStatus, setImportStatus] = useState<string | null>(null);

  // Kafka Live Simulation States
  const [kafkaSimStatus, setKafkaSimStatus] = useState<KafkaSimulationStatus | null>(null);
  const [kafkaLimit, setKafkaLimit] = useState<number>(1000);
  const [kafkaDelayMs, setKafkaDelayMs] = useState<number>(50);
  const [kafkaLoading, setKafkaLoading] = useState<boolean>(false);
  const [kafkaMsg, setKafkaMsg] = useState<string | null>(null);

  useEffect(() => { inputRef.current?.focus(); }, []);

  // Poll Kafka simulation status periodically
  useEffect(() => {
    let timer: any;
    const poll = async () => {
      try {
        const s = await getKafkaSimulationStatus();
        setKafkaSimStatus(s);
      } catch (ignored) {}
    };
    poll();
    timer = setInterval(poll, 1500);
    return () => clearInterval(timer);
  }, []);

  const handleStartKafkaSim = async () => {
    setKafkaLoading(true);
    setKafkaMsg(null);
    try {
      await startKafkaSimulation(kafkaLimit, kafkaDelayMs);
      setKafkaMsg("Kafka simülasyonu başlatıldı.");
      const s = await getKafkaSimulationStatus();
      setKafkaSimStatus(s);
    } catch (err: any) {
      setKafkaMsg(`Hata: ${err.message}`);
    } finally {
      setKafkaLoading(false);
    }
  };

  const handleStopKafkaSim = async () => {
    setKafkaLoading(true);
    try {
      await stopKafkaSimulation();
      setKafkaMsg("Simülasyon durduruldu.");
      const s = await getKafkaSimulationStatus();
      setKafkaSimStatus(s);
    } catch (err: any) {
      setKafkaMsg(`Hata: ${err.message}`);
    } finally {
      setKafkaLoading(false);
    }
  };

  useEffect(() => {
    getOlaylarMeta().then(data => {
      if (data && data.totalCount) setOlaylarMeta(data);
    }).catch(() => {});
  }, []);

  const handleImportOlaylar = async () => {
    setImportLoading(true);
    setImportStatus(null);
    try {
      const res = await importOlaylar(importLimit, false);
      if (res.status === "ok") {
        setImportStatus(`${res.indexedCount} olay OpenSearch'e aktarıldı (${res.tookMs}ms).`);
        getOlaylarMeta().then(data => {
          if (data && data.totalCount) setOlaylarMeta(data);
        }).catch(() => {});
      } else {
        setImportStatus(`Uyarı: ${res.message || "Yükleme uyarısı"}`);
      }
    } catch (err: any) {
      setImportStatus(`Hata: ${err.message}`);
    } finally {
      setImportLoading(false);
    }
  };

  const handleSearch = useCallback(async () => {
    const q = query.trim();
    if (!q) return;
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setLoading(true);
    setError(null);

    // Merge custom incident filters with generic filters
    const customFilters: Record<string, unknown> = parseFilters(settings.filters) || {};
    if (selectedBirim) {
      customFilters["birim"] = selectedBirim;
    }
    if (startDate || endDate) {
      const dateRange: Record<string, string> = {};
      if (startDate) dateRange["gte"] = startDate;
      if (endDate) dateRange["lte"] = endDate;
      customFilters["tarih"] = dateRange;
    }

    const explicitTypes = parseTypes(settings.types);
    const effectiveTypes = explicitTypes || (selectedType ? [selectedType] : undefined);

    const req: HybridExplainRequest = {
      query: q,
      bm25Weight: settings.bm25Weight,
      semanticWeight: settings.semanticWeight,
      limit: settings.limit,
      candidateMultiplier: settings.candidateMultiplier,
      rankConstant: settings.rankConstant,
      fusionMode: "RRF",
      ...(settings.indexName.trim() ? { indexName: settings.indexName.trim() } : {}),
      ...(effectiveTypes ? { types: effectiveTypes } : {}),
      ...(Object.keys(customFilters).length > 0 ? { filters: customFilters } : {}),
    };

    try {
      const data = await explainHybridSearch(req, ctrl.signal);
      setResult(data);
    } catch (err: unknown) {
      if (err instanceof Error && err.name === "AbortError") return;
      setError(err instanceof Error ? err.message : "Bilinmeyen bir hata oluştu.");
      setResult(null);
    } finally {
      setLoading(false);
    }
  }, [query, settings, selectedType, selectedBirim, startDate, endDate]);

  const handleKeyDown = (e: React.KeyboardEvent) => { if (e.key === "Enter") handleSearch(); };
  const setSetting = <K extends keyof UiSettings>(key: K, val: UiSettings[K]) =>
    setSettings((prev) => ({ ...prev, [key]: val }));
  const resetSettings = () => {
    setSettings(DEFAULT_SETTINGS);
    setSelectedType("");
    setSelectedBirim("");
    setStartDate("");
    setEndDate("");
  };

  /* Compute max scores for normalizing bars */
  const bm25Max = result ? Math.max(...result.bm25.results.map(r => r.originalScore), 0.0001) : 1;
  const semMax = result ? Math.max(...result.semantic.results.map(r => r.originalScore), 0.0001) : 1;

  // Map doc ID to RRF rank for comparing with Reranker
  const rrfRankMap = useMemo(() => {
    const map = new Map<string, number>();
    if (result) {
      result.finalResults.forEach(r => map.set(r.document.id, r.finalRank));
    }
    return map;
  }, [result]);

  return (
    <div className="shell">
      {/* ── HEADER ── */}
      <header className="header">
        <div className="header-logo">
          <div className="logo-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8"/>
              <line x1="21" y1="21" x2="16.65" y2="16.65"/>
              <line x1="11" y1="8" x2="11" y2="14"/>
              <line x1="8" y1="11" x2="14" y2="11"/>
            </svg>
          </div>
          <div className="logo-text-group">
            <span className="logo-title">SEMANTIC SEARCH</span>
            <span className="logo-badge">CONSOLE</span>
          </div>
        </div>

        <div className="search-bar-wrap">
          <span className="search-bar-icon">
            <svg width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <circle cx="11" cy="11" r="8" /><path d="M21 21l-4.35-4.35" />
            </svg>
          </span>
          <input
            ref={inputRef} id="search-input" className="search-bar" type="text"
            placeholder="Arama sorgusu girin (örn: yapay zeka modelleri, opensearch mimarisi)..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown} disabled={loading} autoComplete="off"
          />
        </div>

        <button id="search-btn" className="search-btn" onClick={handleSearch} disabled={loading || !query.trim()}>
          {loading ? <span className="spinner" /> : (
            <svg width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24">
              <path d="M5 12h14M12 5l7 7-7 7" />
            </svg>
          )}
          {loading ? "Aranıyor…" : "Ara"}
        </button>
      </header>

      {/* ── MAIN CONTENT ── */}
      <main className="main-content" id="main-content">
        {error && (
          <div className="error-banner" role="alert">
            <svg width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <circle cx="12" cy="12" r="10" /><path d="M12 8v4M12 16h.01" />
            </svg>
            {error}
          </div>
        )}

        {/* Loading Skeleton */}
        {loading && !result && (
          <div className="pipeline-steps">
            {["BM25", "SEMANTIC", "RRF FUSION"].map((label, i) => (
              <div key={label} className={`stage-card ${["bm25", "sem", "final pipeline-step-full"][i]}`}>
                <div className="stage-header">
                  <span className={`stage-badge ${["bm25", "sem", "rrf"][i]}`}>{label}</span>
                  <span className="stage-title">{["Kelime Eşleşmesi (BM25)", "Anlamsal Arama (BGE-M3)", "Hibrit Birleştirme (RRF)"][i]}</span>
                </div>
                <SkeletonResults />
              </div>
            ))}
          </div>
        )}

        {/* Results Pipeline */}
        {!loading && result && (
          <>
            {/* 1. Diagnostic / Overlap Overview */}
            <SearchDiagnosticBanner result={result} />

            {/* 2. Pipeline Summary Bar */}
            <div className="pipeline-header">
              <div>
                <div className="pipeline-meta">
                  Sorgu: <strong>"{result.query}"</strong>
                  {result.indexName && <> · İndeks: <strong>{result.indexName}</strong></>}
                  <> · Mod: <strong style={{ color: "var(--sem-color)" }}>Standart Dense (BGE-M3)</strong></>
                </div>
                <div className="stat-chips">
                  <div className="stat-chip">
                    <span className="dot bm25" />
                    BM25: {result.bm25.results.length} aday ({result.bm25.tookMs}ms)
                  </div>
                  <div className="stat-chip">
                    <span className="dot sem" />
                    Semantic: {result.semantic.results.length} aday ({result.semantic.tookMs}ms)
                  </div>
                  <div className="stat-chip">
                    <span className="dot rrf" />
                    RRF: {result.finalResults.length} sonuç ({result.totalCandidates} benzersiz aday)
                  </div>
                  {result.rerank && (
                    <div className="stat-chip">
                      <span className="dot rerank" />
                      Reranker: {result.rerank.results.length} sıralandı ({result.rerank.tookMs}ms)
                      {result.rerank.usedFallback && <span className="fallback-badge">fallback</span>}
                    </div>
                  )}
                </div>
              </div>
              <div className="pipeline-time">Süre: {result.tookMs}ms</div>
            </div>

            {/* 3. Pipeline Steps Grid */}
            <div className="pipeline-steps">
              {/* ── BM25 Stage ── */}
              <div className="stage-card bm25">
                <div className="stage-header">
                  <span className="stage-badge bm25">
                    <svg width="10" height="10" viewBox="0 0 10 10" fill="currentColor">
                      <rect x="0" y="4" width="2" height="6" rx="1"/>
                      <rect x="3" y="2" width="2" height="8" rx="1"/>
                      <rect x="6" y="0" width="2" height="10" rx="1"/>
                    </svg>
                    BM25
                  </span>
                  <span className="stage-title">Sözcüksel Arama (Okapi BM25)</span>
                  <span className="stage-time">{result.bm25.tookMs}ms</span>
                </div>
                <AlgoInfo
                  variant="bm25"
                  query={result.query}
                  resultCount={result.bm25.results.length}
                  tookMs={result.bm25.tookMs}
                />
                <div className="stage-body">
                  {result.bm25.results.length === 0
                    ? <div className="no-results">Bu sorgu için BM25 eşleşmesi bulunamadı</div>
                    : result.bm25.results.map((r) => (
                        <RankedResultRow
                          key={r.document.id}
                          item={r}
                          variant="bm25"
                          maxScore={bm25Max}
                          query={result.query}
                        />
                      ))
                  }
                </div>
              </div>

              {/* ── Semantic Stage ── */}
              <div className="stage-card sem">
                <div className="stage-header">
                  <span className="stage-badge sem">
                    SEMANTIC
                  </span>
                  <span className="stage-title">
                    Vektör Tabanlı Anlamsal Arama (Dense)
                  </span>
                  <span className="stage-time">{result.semantic.tookMs}ms</span>
                </div>
                <AlgoInfo
                  variant="sem"
                  query={result.query}
                  resultCount={result.semantic.results.length}
                  tookMs={result.semantic.tookMs}
                  model="BAAI/bge-m3"
                />
                <div className="stage-body">
                  {result.semantic.results.length === 0
                    ? <div className="no-results">Anlamsal arama sonucu bulunamadı</div>
                    : result.semantic.results.map((r) => (
                        <RankedResultRow
                          key={r.document.id}
                          item={r}
                          variant="sem"
                          maxScore={semMax}
                          query={result.query}
                        />
                      ))
                  }
                </div>
              </div>

              {/* Flow separator */}
              <FlowArrow
                label={
                  result.settings.fusionMode === "RRF"
                    ? `Reciprocal Rank Fusion (RRF) Sıralama Birleştirmesi · k=${result.settings.rankConstant}`
                    : `Skor Normalizasyonu ile Birleştirme`
                }
              />

              {/* ── Fusion Stage ── */}
              <div className="stage-card final pipeline-step-full">
                <div className="stage-header">
                  <span className="stage-badge rrf">
                    <svg width="10" height="10" viewBox="0 0 10 10" fill="currentColor">
                      <polygon points="5,0 9,9 1,9"/>
                    </svg>
                    RRF FUSION
                  </span>
                  <span className="stage-title">
                    Hibrit Birleştirme — BM25 (%{Math.round(result.settings.bm25Weight * 100)}) + Semantic (%{Math.round(result.settings.semanticWeight * 100)})
                  </span>
                </div>
                <AlgoInfo variant="rrf" query={result.query} />
                <div className="stage-body" style={{ maxHeight: "none" }}>
                  {result.finalResults.map((r) => (
                    <FusionResultRow key={r.document.id} item={r} query={result.query} settings={result.settings} />
                  ))}
                </div>
              </div>

              {/* ── Reranker Stage (if available) ── */}
              {result.rerank && (
                <>
                  <FlowArrow label={`Cross-Encoder Derin Alaka Sıralaması · ${result.rerank.model.includes("Tei") ? "bge-reranker-v2-m3" : result.rerank.model}`} />
                  <div className="stage-card rerank pipeline-step-full">
                    <div className="stage-header">
                      <span className="stage-badge rerank">
                        RERANKER
                      </span>
                      <span className="stage-title">
                        Cross-Encoder Yeniden Sıralama · {result.rerank.tookMs}ms
                      </span>
                      {result.rerank.usedFallback && (
                        <span className="fallback-badge">fallback</span>
                      )}
                    </div>
                    <AlgoInfo
                      variant="rerank"
                      query={result.query}
                      model={result.rerank.model.includes("Tei") ? "BAAI/bge-reranker-v2-m3 (TEI Cross-Encoder)" : result.rerank.model}
                    />
                    <div className="stage-body" style={{ maxHeight: "none" }}>
                      {result.rerank.results.length === 0 ? (
                        <div style={{ padding: "24px 16px", textAlign: "center", color: "var(--text-secondary)", fontSize: "13px", lineHeight: "1.6" }}>
                          <strong>Filtrelendi:</strong> Adayların hiçbiri bu sorguyla (<code>{result.query}</code>) yeterli alaka taşımadığı için elendi (Alaka Skoru &lt; 0.20).
                        </div>
                      ) : (
                        result.rerank.results.map((r) => (
                          <RerankedResultRow
                            key={r.document.id}
                            item={r}
                            query={result.query}
                            rrfRank={rrfRankMap.get(r.document.id)}
                          />
                        ))
                      )}
                    </div>
                  </div>
                </>
              )}
            </div>
          </>
        )}

        {/* Empty State */}
        {!loading && !result && !error && (
          <div className="empty-state">
            <div className="empty-icon-wrap">
              <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                <circle cx="11" cy="11" r="8"/>
                <line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
            </div>
            <div className="empty-title">Semantik Arama Konsolu</div>
            <div className="empty-desc">
              Arama sorgusu girerek BM25 sözcüksel filtrelemesini, Dense vektörel çıkarımlarını,
              RRF birleşimini ve Cross-Encoder derin alaka skorlarını adım adım analiz edebilirsiniz.
            </div>
          </div>
        )}
      </main>

      {/* ── SIDEBAR ── */}
      <aside className="sidebar" id="settings-sidebar">
        <div className="sidebar-header">
          <svg width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
            <path d="M12 20h9M16.5 3.5a2.121 2.121 0 013 3L7 19l-4 1 1-4L16.5 3.5z"/>
          </svg>
          Arama Ayarları
        </div>

        <div className="sidebar-body">
          {/* Weights */}
          <div className="settings-group">
            <div className="settings-group-label">Ağırlıklar (RRF Katsayıları)</div>
            <div className="slider-control">
              <div className="slider-label-row">
                <span className="slider-label">BM25 Ağırlığı</span>
                <span className="slider-value bm25">{settings.bm25Weight.toFixed(2)}</span>
              </div>
              <input id="slider-bm25" type="range" className="bm25"
                min={0} max={1} step={0.01} value={settings.bm25Weight}
                style={sliderStyle(settings.bm25Weight, 0, 1)}
                onChange={(e) => setSetting("bm25Weight", parseFloat(e.target.value))} />
            </div>
            <div className="slider-control">
              <div className="slider-label-row">
                <span className="slider-label">Semantic Ağırlığı</span>
                <span className="slider-value sem">{settings.semanticWeight.toFixed(2)}</span>
              </div>
              <input id="slider-semantic" type="range" className="sem"
                min={0} max={1} step={0.01} value={settings.semanticWeight}
                style={sliderStyle(settings.semanticWeight, 0, 1)}
                onChange={(e) => setSetting("semanticWeight", parseFloat(e.target.value))} />
            </div>
            <div className="weight-bar"
              data-tooltip={`BM25: ${(settings.bm25Weight * 100).toFixed(0)}% · Semantik: ${(settings.semanticWeight * 100).toFixed(0)}%`}>
              <div className="weight-bar-bm25" style={{ flex: settings.bm25Weight }} />
              <div className="weight-bar-sem" style={{ flex: settings.semanticWeight }} />
            </div>
          </div>

          <div className="divider" />

          {/* Parameters */}
          <div className="settings-group">
            <div className="settings-group-label">RRF Parametreleri</div>
            <div className="slider-control">
              <div className="slider-label-row">
                <span className="slider-label">Rank Sabiti (k)</span>
                <span className="slider-value">{settings.rankConstant}</span>
              </div>
              <input id="slider-rank-constant" type="range" className="neutral"
                min={1} max={1000} step={1} value={settings.rankConstant}
                style={sliderStyle(settings.rankConstant, 1, 1000)}
                onChange={(e) => setSetting("rankConstant", parseInt(e.target.value))} />
              <div className="info-note">Yüksek k değeri alt sıralardaki dökümanların cezasını yumuşatır. Varsayılan: 60</div>
            </div>
            <div className="slider-control">
              <div className="slider-label-row">
                <span className="slider-label">Aday Havuz Çarpanı</span>
                <span className="slider-value">{settings.candidateMultiplier}×</span>
              </div>
              <input id="slider-candidate-mult" type="range" className="neutral"
                min={1} max={10} step={1} value={settings.candidateMultiplier}
                style={sliderStyle(settings.candidateMultiplier, 1, 10)}
                onChange={(e) => setSetting("candidateMultiplier", parseInt(e.target.value))} />
              <div className="info-note">Her koldan çekilecek aday sayısı: Limit × {settings.candidateMultiplier}</div>
            </div>
          </div>

          <div className="divider" />

          {/* Limit */}
          <div className="settings-group">
            <div className="settings-group-label">Sonuç Limiti</div>
            <div className="slider-control">
              <div className="slider-label-row">
                <span className="slider-label">Limit</span>
                <span className="slider-value">{settings.limit}</span>
              </div>
              <input id="slider-limit" type="range" className="neutral"
                min={1} max={100} step={1} value={settings.limit}
                style={sliderStyle(settings.limit, 1, 100)}
                onChange={(e) => setSetting("limit", parseInt(e.target.value))} />
            </div>
          </div>

          <div className="divider" />

          {/* ── Incident Report Filters ── */}
          <div className="settings-group">
            <div className="settings-group-label" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <span>Olay Raporu Filtreleri</span>
              {(selectedBirim || selectedType || startDate || endDate) && (
                <button
                  type="button"
                  onClick={() => { setSelectedBirim(""); setSelectedType(""); setStartDate(""); setEndDate(""); }}
                  style={{ background: "none", border: "none", color: "#f87171", fontSize: "10px", cursor: "pointer", padding: 0 }}
                >
                  Temizle
                </button>
              )}
            </div>

            {/* Birim Select */}
            <div className="form-field">
              <label className="form-label" htmlFor="select-birim">Görevli Askeri/Güvenlik Birimi</label>
              <select
                id="select-birim"
                className="filter-select"
                value={selectedBirim}
                onChange={(e) => setSelectedBirim(e.target.value)}
              >
                <option value="">Tüm Birimler ({olaylarMeta?.birimler.length ?? "..."})</option>
                {olaylarMeta?.birimler.map((b) => (
                  <option key={b} value={b}>{b}</option>
                ))}
              </select>
            </div>

            {/* Olay Türü Select */}
            <div className="form-field">
              <label className="form-label" htmlFor="select-type">Olay Türü</label>
              <select
                id="select-type"
                className="filter-select"
                value={selectedType}
                onChange={(e) => setSelectedType(e.target.value)}
              >
                <option value="">Tüm Türler ({olaylarMeta?.types.length ?? "..."})</option>
                {olaylarMeta?.types.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>

            {/* Tarih Aralığı */}
            <div className="form-field">
              <label className="form-label">Olay Tarihi Aralığı</label>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "6px" }}>
                <div>
                  <label className="form-label" style={{ fontSize: "10px", color: "var(--text-muted)", marginBottom: "2px" }}>Başlangıç</label>
                  <input
                    type="date"
                    className="form-input"
                    style={{ padding: "4px 6px", fontSize: "11px" }}
                    value={startDate}
                    onChange={(e) => setStartDate(e.target.value)}
                  />
                </div>
                <div>
                  <label className="form-label" style={{ fontSize: "10px", color: "var(--text-muted)", marginBottom: "2px" }}>Bitiş</label>
                  <input
                    type="date"
                    className="form-input"
                    style={{ padding: "4px 6px", fontSize: "11px" }}
                    value={endDate}
                    onChange={(e) => setEndDate(e.target.value)}
                  />
                </div>
              </div>
            </div>
          </div>

          <div className="divider" />

          {/* ── Kafka Live Event Simulation Widget ── */}
          <div className="settings-group">
            <div className="settings-group-label" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <span>Canlı Veri Akış Simülasyonu</span>
              <span className={`kafka-status-badge ${kafkaSimStatus?.running ? "live" : "idle"}`}>
                <span className="pulse-dot" />
                <span>{kafkaSimStatus?.running ? "Canlı Akış" : "Hazır"}</span>
              </span>
            </div>

            <div className="kafka-sim-box">
              {/* Stat Grid */}
              <div className="kafka-stat-grid">
                <div className="kafka-stat-card">
                  <span className="lbl">Kafka Gönderilen</span>
                  <span className="val" style={{ color: "#f97316" }}>
                    {kafkaSimStatus?.publishedCount ?? 0}
                    <span style={{ fontSize: "10px", fontWeight: 400, color: "var(--text-muted)", marginLeft: "4px" }}>
                      / {kafkaSimStatus?.targetLimit && kafkaSimStatus.targetLimit > 0 ? kafkaSimStatus.targetLimit : kafkaLimit}
                    </span>
                  </span>
                </div>
                <div className="kafka-stat-card">
                  <span className="lbl">İşlenen Kayıt</span>
                  <span className="val" style={{ color: "#fb923c" }}>
                    {kafkaSimStatus?.dbRecordedCount ?? 0}
                    <span style={{ fontSize: "10px", fontWeight: 400, color: "var(--text-muted)", marginLeft: "4px" }}>işlendi</span>
                  </span>
                </div>
              </div>

              {/* Progress Bar */}
              {kafkaSimStatus && kafkaSimStatus.targetLimit > 0 && (
                <div className="kafka-progress-wrap">
                  <div
                    className="kafka-progress-fill"
                    style={{ width: `${Math.min((kafkaSimStatus.publishedCount / kafkaSimStatus.targetLimit) * 100, 100)}%` }}
                  />
                </div>
              )}

              {/* Controls */}
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "6px" }}>
                <div>
                  <label className="form-label" style={{ fontSize: "10px", marginBottom: "2px" }}>Adet</label>
                  <select
                    className="filter-select"
                    style={{ padding: "4px 6px", fontSize: "11px" }}
                    value={kafkaLimit}
                    onChange={(e) => setKafkaLimit(parseInt(e.target.value))}
                    disabled={kafkaSimStatus?.running}
                  >
                    <option value={100}>100 Olay</option>
                    <option value={500}>500 Olay</option>
                    <option value={1000}>1.000 Olay</option>
                    <option value={3000}>3.000 Olay</option>
                    <option value={5000}>5.000 Olay</option>
                    <option value={10000}>10.000 Olay</option>
                  </select>
                </div>
                <div>
                  <label className="form-label" style={{ fontSize: "10px", marginBottom: "2px" }}>Akış Hızı</label>
                  <select
                    className="filter-select"
                    style={{ padding: "4px 6px", fontSize: "11px" }}
                    value={kafkaDelayMs}
                    onChange={(e) => setKafkaDelayMs(parseInt(e.target.value))}
                    disabled={kafkaSimStatus?.running}
                  >
                    <option value={100}>10 olay/sn</option>
                    <option value={50}>20 olay/sn</option>
                    <option value={20}>50 olay/sn</option>
                    <option value={0}>Maksimum Hız</option>
                  </select>
                </div>
              </div>

              {/* Action Button */}
              {kafkaSimStatus?.running ? (
                <button
                  type="button"
                  className="kafka-stop-btn"
                  onClick={handleStopKafkaSim}
                  disabled={kafkaLoading}
                >
                  {kafkaLoading ? "Durduruluyor..." : "Akışı Durdur"}
                </button>
              ) : (
                <button
                  type="button"
                  className="kafka-start-btn"
                  onClick={handleStartKafkaSim}
                  disabled={kafkaLoading}
                >
                  {kafkaLoading ? "Başlatılıyor..." : `Akışı Başlat (${kafkaLimit.toLocaleString()} Olay)`}
                </button>
              )}

              {/* Live Info & Status */}
              <div style={{ fontSize: "10px", color: "var(--text-muted)", lineHeight: 1.3 }}>
                <div>Broker: <span style={{ color: "#f97316", fontFamily: "monospace" }}>localhost:9092</span> · Topic: <span style={{ color: "#fb923c", fontFamily: "monospace" }}>olaylar-events</span></div>
                {kafkaSimStatus?.lastEventSummary && (
                  <div style={{ marginTop: "4px", color: "var(--text-secondary)", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                    Son Olay: {kafkaSimStatus.lastEventSummary}
                  </div>
                )}
                {kafkaSimStatus?.status && kafkaSimStatus.status.startsWith("ERROR") && (
                  <div style={{ marginTop: "4px", padding: "4px 8px", background: "rgba(239, 68, 68, 0.15)", border: "1px solid rgba(239, 68, 68, 0.4)", borderRadius: "4px", color: "#f87171", fontSize: "10px" }}>
                    {kafkaSimStatus.status}
                  </div>
                )}
                {kafkaMsg && (
                  <div style={{ marginTop: "3px", color: "#f97316", fontWeight: 600 }}>
                    {kafkaMsg}
                  </div>
                )}
              </div>
            </div>
          </div>

          <div className="divider" />

          {/* Filters */}
          <div className="settings-group">
            <div className="settings-group-label">Diğer / Gelişmiş Filtreler</div>
            <div className="form-field">
              <label className="form-label" htmlFor="input-index-name">İndeks Adı</label>
              <input id="input-index-name" className="form-input" type="text"
                placeholder="olaylar (varsayılan)" value={settings.indexName}
                onChange={(e) => setSetting("indexName", e.target.value)} />
            </div>
            <div className="form-field">
              <label className="form-label" htmlFor="input-types">Manuel Tipler (virgülle)</label>
              <input id="input-types" className="form-input" type="text"
                placeholder="OLAY, DEVRİYE, ..." value={settings.types}
                onChange={(e) => setSetting("types", e.target.value)} />
            </div>
            <div className="form-field">
              <label className="form-label" htmlFor="input-filters">Özel JSON Filtre</label>
              <input id="input-filters" className="form-input" type="text"
                placeholder='{"status": "active"}' value={settings.filters}
                onChange={(e) => setSetting("filters", e.target.value)} />
            </div>
          </div>

          <button id="reset-settings-btn" className="reset-btn" onClick={resetSettings} title="Varsayılan ayarlara dön">
            <svg width="12" height="12" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
              <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
              <path d="M3 3v5h5"/>
            </svg>
            Ayarları Sıfırla
          </button>
        </div>
      </aside>
    </div>
  );
}
