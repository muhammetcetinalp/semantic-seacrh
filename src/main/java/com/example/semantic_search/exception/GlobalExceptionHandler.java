package com.example.semantic_search.exception;

import com.example.semantic_search.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Uygulama genelindeki tüm REST denetleyicilerinde fırlatılan istisnaları merkezi olarak yakalayan
 * ve standart {@link ErrorResponse} formatında HTTP yanıtlarına dönüştüren global hata yöneticisi.
 *
 * <p>Bu sınıf sayesinde denetleyiciler (controllers) try-catch bloklarından arındırılır,
 * istemcilere hassas yığın izleri (stack traces) veya dahili sunucu ayrıntıları sızdırılmaz.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * DTO validasyon hatalarını (ör. {@code @Valid}, {@code @NotBlank}) yakalar ve
     * hangi alanların geçersiz olduğunu açıklayan 400 Bad Request yanıtı döner.
     *
     * @param ex Validasyon istisnası
     * @param headers HTTP başlıkları
     * @param status HTTP durum kodu
     * @param request Mevcut web isteği
     * @return Standart hata yanıtı içeren ResponseEntity
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return handleExceptionInternal(ex,
                new ErrorResponse(400, "Validation Error", message), headers, status, request);
    }

    /**
     * Spring MVC dahili istisnalarını yakalayarak standart {@link ErrorResponse} nesnesine dönüştürür.
     *
     * @param ex Dahili Spring istisnası
     * @param body Yanıt gövdesi
     * @param headers HTTP başlıkları
     * @param status HTTP durum kodu
     * @param request Mevcut web isteği
     * @return Biçimlendirilmiş ResponseEntity
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (body instanceof ErrorResponse) {
            return super.handleExceptionInternal(ex, body, headers, status, request);
        }

        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        String error = httpStatus != null ? httpStatus.getReasonPhrase() : "Request Error";
        String message = switch (status.value()) {
            case 400 -> "İstek hatalı biçimlendirilmiş veya geçersiz parametreler içeriyor.";
            case 404 -> "İstenen kaynak bulunamadı.";
            case 405 -> "Bu kaynak için HTTP metodu desteklenmiyor.";
            case 406 -> "İstenen yanıt formatı desteklenmiyor.";
            case 415 -> "İstek içerik türü (Content-Type) desteklenmiyor.";
            default -> status.is5xxServerError()
                    ? "Beklenmeyen bir sunucu hatası oluştu. Lütfen daha sonra tekrar deneyin."
                    : "İstek işlenemedi.";
        };
        return super.handleExceptionInternal(ex,
                new ErrorResponse(status.value(), error, message), headers, status, request);
    }

    /**
     * İstenen doküman bulunamadığında fırlatılan {@link DocumentNotFoundException} istisnasını yakalar.
     *
     * @param ex Doküman bulunamadı istisnası
     * @return 404 Not Found durum kodlu ErrorResponse
     */
    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotFound(DocumentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "Not Found", ex.getMessage()));
    }

    /**
     * Geçersiz olay formatı olduğunda fırlatılan {@link InvalidSearchEventException} istisnasını yakalar.
     *
     * @param ex Geçersiz olay istisnası
     * @return 400 Bad Request durum kodlu ErrorResponse
     */
    @ExceptionHandler(InvalidSearchEventException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSearchEvent(InvalidSearchEventException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "Bad Request", ex.getMessage()));
    }

    /**
     * OpenSearch kümesine erişilemediğinde fırlatılan istisnayı yakalar.
     *
     * @param ex OpenSearch erişim istisnası
     * @return 503 Service Unavailable durum kodlu ErrorResponse
     */
    @ExceptionHandler(OpenSearchUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleOpenSearchUnavailable(OpenSearchUnavailableException ex) {
        log.error("OpenSearch erişilemez durumda: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(503, "Service Unavailable",
                        "Arama motoru şu anda kullanılamıyor. Lütfen daha sonra tekrar deneyiniz."));
    }

    /**
     * Vektör gömme servisine erişilemediğinde fırlatılan istisnayı yakalar.
     *
     * @param ex Embedding erişim istisnası
     * @return 503 Service Unavailable durum kodlu ErrorResponse
     */
    @ExceptionHandler(EmbeddingUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleEmbeddingUnavailable(EmbeddingUnavailableException ex) {
        log.error("Embedding servisi erişilemez durumda: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(503, "Service Unavailable",
                        "Embedding servisi şu anda kullanılamıyor. Lütfen daha sonra tekrar deneyiniz."));
    }

    /**
     * Arama servisinin iş akışı sırasında fırlatılan genel hataları yakalar.
     *
     * @param ex Arama servisi istisnası
     * @return 500 Internal Server Error durum kodlu ErrorResponse
     */
    @ExceptionHandler(SearchServiceException.class)
    public ResponseEntity<ErrorResponse> handleSearchService(SearchServiceException ex) {
        log.error("Arama servisi hatası: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Internal Error",
                        "Beklenmeyen bir arama hatası oluştu. Lütfen daha sonra tekrar deneyiniz."));
    }

    /**
     * Yakalanmamış diğer tüm genel istisnaları yakalayarak istemciye güvenli bir hata döner.
     *
     * @param ex Genel istisna
     * @return 500 Internal Server Error durum kodlu ErrorResponse
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Yakalanmamış beklenmeyen hata: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Internal Error",
                        "Beklenmeyen bir sistem hatası oluştu. Lütfen daha sonra tekrar deneyiniz."));
    }
}
