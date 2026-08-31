package com.voicetodo.desktop;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class GoogleOAuth {
    static final String CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar";
    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    String accessToken(boolean interactive) throws Exception {
        Credentials credentials = readCredentials();
        Token token = readToken();
        if (token != null && token.access_token != null && token.expiresAt > System.currentTimeMillis() + 60_000) {
            return token.access_token;
        }
        if (token != null && token.refresh_token != null && !token.refresh_token.isBlank()) {
            try {
                return refresh(credentials, token).access_token;
            } catch (Exception error) {
                if (!interactive) throw error;
            }
        }
        if (!interactive) throw new IllegalStateException("Google 계정 연결이 필요합니다.");
        return authorize(credentials).access_token;
    }

    void disconnect() throws IOException {
        Files.deleteIfExists(AppPaths.token());
    }

    private Credentials readCredentials() throws IOException {
        if (!Files.exists(AppPaths.credentials())) {
            throw new IllegalStateException("Google OAuth credentials.json 파일을 먼저 선택해 주세요.");
        }
        CredentialRoot root = gson.fromJson(Files.readString(AppPaths.credentials(), StandardCharsets.UTF_8), CredentialRoot.class);
        Credentials value = root == null ? null : root.installed;
        if (value == null || value.client_id == null || value.client_id.isBlank()) {
            throw new IllegalStateException("데스크톱 앱 형식의 credentials.json 파일이 아닙니다.");
        }
        if (value.auth_uri == null) value.auth_uri = "https://accounts.google.com/o/oauth2/v2/auth";
        if (value.token_uri == null) value.token_uri = "https://oauth2.googleapis.com/token";
        return value;
    }

    private Token readToken() {
        try {
            if (!Files.exists(AppPaths.token())) return null;
            return gson.fromJson(Files.readString(AppPaths.token(), StandardCharsets.UTF_8), Token.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Token authorize(Credentials credentials) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String redirect = "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth2callback";
        String verifier = randomUrlSafe(64);
        String challenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String state = randomUrlSafe(24);
        CompletableFuture<String> codeFuture = new CompletableFuture<>();

        server.createContext("/oauth2callback", exchange -> handleCallback(exchange, state, codeFuture));
        server.start();
        try {
            String url = credentials.auth_uri + "?" + form(Map.of(
                    "client_id", credentials.client_id,
                    "redirect_uri", redirect,
                    "response_type", "code",
                    "scope", CALENDAR_SCOPE,
                    "access_type", "offline",
                    "prompt", "consent",
                    "code_challenge", challenge,
                    "code_challenge_method", "S256",
                    "state", state
            ));
            if (!Desktop.isDesktopSupported()) throw new IllegalStateException("기본 웹 브라우저를 열 수 없습니다.");
            Desktop.getDesktop().browse(URI.create(url));
            String code = codeFuture.get(3, TimeUnit.MINUTES);
            Map<String, String> body = new HashMap<>();
            body.put("client_id", credentials.client_id);
            if (credentials.client_secret != null && !credentials.client_secret.isBlank()) {
                body.put("client_secret", credentials.client_secret);
            }
            body.put("code", code);
            body.put("code_verifier", verifier);
            body.put("redirect_uri", redirect);
            body.put("grant_type", "authorization_code");
            return requestToken(credentials.token_uri, body, null);
        } finally {
            server.stop(0);
        }
    }

    private void handleCallback(HttpExchange exchange, String expectedState,
                                CompletableFuture<String> codeFuture) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        boolean ok = expectedState.equals(query.get("state")) && query.containsKey("code");
        String html = ok
                ? "<html><meta charset='utf-8'><body><h2>톡todo 연결 완료</h2><p>이 창을 닫고 앱으로 돌아가세요.</p></body></html>"
                : "<html><meta charset='utf-8'><body><h2>연결 실패</h2><p>앱에서 다시 시도해 주세요.</p></body></html>";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(ok ? 200 : 400, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
        if (ok) codeFuture.complete(query.get("code"));
        else codeFuture.completeExceptionally(new IllegalStateException(query.getOrDefault("error", "OAuth 응답이 올바르지 않습니다.")));
    }

    private Token refresh(Credentials credentials, Token current) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("client_id", credentials.client_id);
        if (credentials.client_secret != null && !credentials.client_secret.isBlank()) {
            body.put("client_secret", credentials.client_secret);
        }
        body.put("refresh_token", current.refresh_token);
        body.put("grant_type", "refresh_token");
        return requestToken(credentials.token_uri, body, current.refresh_token);
    }

    private Token requestToken(String tokenUri, Map<String, String> values, String previousRefreshToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUri))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(values)))
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Google 로그인 실패 (HTTP " + response.statusCode() + "): " + response.body());
        }
        Token token = gson.fromJson(response.body(), Token.class);
        if ((token.refresh_token == null || token.refresh_token.isBlank()) && previousRefreshToken != null) {
            token.refresh_token = previousRefreshToken;
        }
        token.expiresAt = System.currentTimeMillis() + Math.max(60, token.expires_in) * 1000L;
        Files.createDirectories(AppPaths.directory());
        Files.writeString(AppPaths.token(), gson.toJson(token), StandardCharsets.UTF_8);
        return token;
    }

    private static String randomUrlSafe(int bytes) {
        byte[] value = new byte[bytes];
        new SecureRandom().nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null) return values;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length == 2 ? java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return values;
    }

    private static final class CredentialRoot { Credentials installed; }
    private static final class Credentials {
        String client_id;
        String client_secret;
        String auth_uri;
        String token_uri;
    }
    private static final class Token {
        String access_token;
        String refresh_token;
        long expires_in;
        long expiresAt;
    }
}
