package com.example.semantic_search.exception;

/**
 * OpenSearch veya Qdrant indekslerinde belirtilen benzersiz kimliğe (ID)
 * sahip bir doküman bulunamadığında fırlatılan özel istisna sınıfı.
 *
 * <p>Genellikle REST API seviyesinde HTTP 404 (Not Found) durum koduna dönüştürülür.</p>
 */
public class DocumentNotFoundException extends SearchServiceException {

    /**
     * Belirtilen doküman kimliğiyle yeni bir DocumentNotFoundException oluşturur.
     *
     * @param documentId Bulunamayan dokümanın benzersiz kimliği
     */
    public DocumentNotFoundException(String documentId) {
        super("Doküman bulunamadı: " + documentId);
    }

    /**
     * Belirtilen doküman kimliği ve indeks adıyla yeni bir DocumentNotFoundException oluşturur.
     *
     * @param documentId Bulunamayan dokümanın benzersiz kimliği
     * @param indexName Dokümanın arandığı hedef indeksin adı
     */
    public DocumentNotFoundException(String documentId, String indexName) {
        super("Doküman (" + documentId + ") belirtilen indekste bulunamadı: " + indexName);
    }
}
