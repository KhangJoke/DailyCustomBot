package unicorn.bot.dailycustombot.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import unicorn.bot.dailycustombot.config.EnvLoader;

/**
 * Service gọi API bên ngoài để xác thực tài khoản Net Cafe.
 *
 * Kết quả phụ thuộc hoàn toàn vào trường "data.exists" trong JSON response,
 * KHÔNG chỉ dựa vào HTTP status code.
 *
 * Cấu hình qua biến môi trường:
 * - NET_API_URL: URL gốc của API (bắt buộc)
 */
public class NetApiService {

    private static final Logger logger = LoggerFactory.getLogger(NetApiService.class);

    /** Timeout cho HTTP request (giây) */
    private static final int REQUEST_TIMEOUT_SECONDS = 10;

    private final HttpClient httpClient;
    private final Gson gson;

    public NetApiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build();
        this.gson = new Gson();
    }

    /**
     * Xác thực tài khoản Net Cafe qua API.
     *
     * Gửi HTTP GET request, parse JSON response và trả về giá trị
     * từ trường "data.exists". Nếu HTTP lỗi hoặc parse thất bại → trả về false.
     *
     * @param username Tên tài khoản Net cần xác thực
     * @return CompletableFuture<Boolean> — true nếu tài khoản tồn tại, false nếu
     *         không
     */
    public CompletableFuture<Boolean> verifyAccount(String username) {
        // Đọc URL từ biến môi trường (bắt buộc phải cấu hình)
        String apiUrl = EnvLoader.get("NET_API_URL");
        if (apiUrl == null || apiUrl.isBlank()) {
            logger.error("Biến môi trường NET_API_URL chưa được cấu hình!");
            return CompletableFuture.completedFuture(false);
        }

        // Encode username để an toàn khi đặt vào URL query param
        String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String fullUrl = apiUrl + "?userName=" + encodedUsername;

        // Xây dựng HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build();
        logger.info("Gọi API xác thực Net: GET {}", fullUrl);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int statusCode = response.statusCode();
                    String body = response.body();
                    logger.info("API xác thực trả về HTTP {}: {}", statusCode, body);

                    // HTTP lỗi → trả false
                    if (statusCode != 200) {
                        logger.warn("API trả về HTTP {} — coi như tài khoản không hợp lệ.", statusCode);
                        return false;
                    }

                    // Parse JSON và trích xuất data.exists
                    return parseExistsField(body);
                })
                .exceptionally(throwable -> {
                    logger.error("Lỗi khi gọi API xác thực Net cho '{}': {}",
                            username, throwable.getMessage(), throwable);
                    return false;
                });
    }

    /**
     * Parse JSON response và trích xuất giá trị boolean từ "data.exists".
     *
     * Format JSON mong đợi:
     * {
     * "statusCode": 200,
     * "message": "User existence checked successfully",
     * "data": {
     * "exists": true,
     * "user": { "fullName": "...", "userName": "..." }
     * }
     * }
     *
     * @param responseBody Chuỗi JSON từ API
     * @return true nếu data.exists == true, false trong mọi trường hợp khác
     */
    private boolean parseExistsField(String responseBody) {
        try {
            JsonObject root = gson.fromJson(responseBody, JsonObject.class);

            // Kiểm tra trường "data" có tồn tại và là object
            if (!root.has("data") || !root.get("data").isJsonObject()) {
                logger.warn("Response JSON không có trường 'data' hoặc 'data' không phải object.");
                return false;
            }

            JsonObject data = root.getAsJsonObject("data");

            // Kiểm tra trường "exists" có tồn tại
            if (!data.has("exists")) {
                logger.warn("Response JSON thiếu trường 'data.exists'.");
                return false;
            }

            boolean exists = data.get("exists").getAsBoolean();
            logger.info("Kết quả xác thực: data.exists = {}", exists);
            return exists;

        } catch (JsonSyntaxException e) {
            logger.error("Lỗi parse JSON từ API response: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            logger.error("Lỗi không mong đợi khi parse JSON: {}", e.getMessage(), e);
            return false;
        }
    }
}
