package com.example.semantic_search.model;

/**
 * Bir dokümanın indeksleme yaşam döngüsündeki operasyonel durumunu belirten numaralandırma (Enum).
 *
 * <ul>
 *   <li><b>PENDING:</b> Doküman indeksleme kuyruğuna alındı veya işlem bekliyor.</li>
 *   <li><b>INDEXED:</b> Doküman başarıyla OpenSearch ve Qdrant üzerinde indekslendi.</li>
 *   <li><b>FAILED:</b> İndeksleme veya vektörleme aşamasında hata oluştu.</li>
 *   <li><b>DELETED:</b> Doküman indeksten kaldırıldı.</li>
 * </ul>
 */
public enum IndexingStatus {

    /** İndeksleme için sırada bekliyor. */
    PENDING,

    /** Başarıyla indekslendi ve aramaya hazır. */
    INDEXED,

    /** İşlem sırasında hata meydana geldi. */
    FAILED,

    /** Sistemden ve arama indeksinden silindi. */
    DELETED
}
