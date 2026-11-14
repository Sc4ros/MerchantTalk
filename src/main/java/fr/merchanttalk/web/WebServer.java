package fr.merchanttalk.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.merchanttalk.MerchantTalkPlugin;
import fr.merchanttalk.storage.DataStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/** Petit serveur HTTP intégré : interface web, API JSON et téléchargement du resource pack. */
public final class WebServer {
    private static final Map<String, String> MIME = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "wasm", "application/wasm",
            "png", "image/png",
            "svg", "image/svg+xml",
            "txt", "text/plain; charset=utf-8");

    private final MerchantTalkPlugin plugin;
    private final ApiHandler api;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> tokens;
    private final Map<String, int[]> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> failureWindow = new ConcurrentHashMap<>();
    private HttpServer server;
    private ExecutorService executor;

    public WebServer(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
        this.api = new ApiHandler(plugin);
        this.tokens = plugin.webSessions();
    }

    public void start() throws IOException {
        String bind = plugin.getConfig().getString("web.bind", "0.0.0.0");
        int port = plugin.getConfig().getInt("web.port", 8085);
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "MerchantTalk-Web");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        plugin.getLogger().info("Interface web démarrée sur le port " + port + " (" + plugin.getConfig().getString("web.public-url") + ")");
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
        server = null;
        executor = null;
    }

    private void handle(HttpExchange ex) {
        try {
            handleSafely(ex);
        } finally {
            ex.close();
        }
    }

    private void handleSafely(HttpExchange ex) {
        try {
            ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/pack/")) {
                servePack(ex, path);
            } else if (path.equals("/api/login")) {
                login(ex);
            } else if (path.startsWith("/api/")) {
                if (!authorized(ex)) {
                    sendJson(ex, 401, error("Non connecté"));
                    return;
                }
                api.handle(ex, path.substring("/api/".length()));
            } else {
                serveStatic(ex, path);
            }
        } catch (ApiException e) {
            trySendError(ex, e.status, e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Erreur de l'interface web", e);
            trySendError(ex, 500, "Erreur interne : " + e.getMessage());
        }
    }

    private static void trySendError(HttpExchange ex, int status, String message) {
        try {
            sendJson(ex, status, error(message));
        } catch (IOException ignored) {
            // réponse déjà envoyée ou connexion fermée
        }
    }

    // ---------------------------------------------------------------- authentification

    private void login(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) throw new ApiException(405, "POST attendu");
        String ip = ex.getRemoteAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        Long windowStart = failureWindow.get(ip);
        if (windowStart == null || now - windowStart > 10 * 60_000L) {
            failureWindow.put(ip, now);
            failures.put(ip, new int[]{0});
        }
        if (failures.get(ip)[0] >= 8) throw new ApiException(429, "Trop d'essais, réessaie dans 10 minutes.");

        JsonObject body = readJson(ex);
        String given = body.has("password") ? body.get("password").getAsString() : "";
        String expected = plugin.getConfig().getString("web.password", "");
        boolean ok = !expected.isEmpty() && MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            failures.get(ip)[0]++;
            throw new ApiException(403, "Mot de passe incorrect");
        }
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        long hours = Math.max(1, plugin.getConfig().getLong("web.session-hours", 12));
        tokens.put(token, now + hours * 3_600_000L);
        tokens.entrySet().removeIf(e -> e.getValue() < now);
        JsonObject res = new JsonObject();
        res.addProperty("token", token);
        sendJson(ex, 200, res);
    }

    private boolean authorized(HttpExchange ex) {
        String token = null;
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) token = header.substring(7).trim();
        if (token == null) token = queryParam(ex, "token"); // pour <audio src> et <img src>
        if (token == null) return false;
        Long exp = tokens.get(token);
        return exp != null && exp > System.currentTimeMillis();
    }

    public void logoutAll() {
        tokens.clear();
    }

    // ---------------------------------------------------------------- fichiers

    private void servePack(HttpExchange ex, String path) throws IOException {
        String hash = plugin.pack().hash();
        byte[] bytes = plugin.pack().bytes();
        if (hash == null || bytes == null || !path.equals("/pack/" + hash + ".zip")) {
            send(ex, 404, "text/plain", "Pack introuvable".getBytes(StandardCharsets.UTF_8));
            return;
        }
        ex.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
        send(ex, 200, "application/zip", bytes);
    }

    private void serveStatic(HttpExchange ex, String path) throws IOException {
        if (path.equals("/") || path.isEmpty()) path = "/index.html";
        if (path.contains("..") || path.contains("\\")) {
            send(ex, 400, "text/plain", new byte[0]);
            return;
        }
        try (InputStream in = plugin.getResource("web" + path)) {
            if (in == null) {
                send(ex, 404, "text/plain", "Introuvable".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String ext = path.substring(path.lastIndexOf('.') + 1);
            ex.getResponseHeaders().set("Cache-Control", path.startsWith("/vendor/") ? "public, max-age=86400" : "no-cache");
            send(ex, 200, MIME.getOrDefault(ext, "application/octet-stream"), in.readAllBytes());
        }
    }

    // ---------------------------------------------------------------- utilitaires

    static String queryParam(HttpExchange ex, String name) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return null;
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name)) {
                return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    static byte[] readBody(HttpExchange ex, int maxBytes) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(maxBytes + 1);
            if (data.length > maxBytes) throw new ApiException(413, "Fichier trop gros (max " + (maxBytes / 1024 / 1024) + " Mo)");
            return data;
        }
    }

    static JsonObject readJson(HttpExchange ex) throws IOException {
        String s = new String(readBody(ex, 1024 * 1024), StandardCharsets.UTF_8);
        if (s.isBlank()) return new JsonObject();
        try {
            return JsonParser.parseString(s).getAsJsonObject();
        } catch (Exception e) {
            throw new ApiException(400, "JSON invalide");
        }
    }

    static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        send(ex, status, "application/json; charset=utf-8", DataStore.GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    static void send(HttpExchange ex, int status, String type, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        if (ex.getRequestMethod().equals("HEAD")) {
            ex.sendResponseHeaders(status, -1);
            return;
        }
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o;
    }

    /** Erreur renvoyée au navigateur avec un code HTTP. */
    public static final class ApiException extends RuntimeException {
        final int status;

        public ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
