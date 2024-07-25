package xyz.bobkinn.webwhitelist;

import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

public class Requester {
    public static final TypeToken<List<String>> LIST_TYPE = new TypeToken<>() {};
    private static final long logDelay = Duration.ofHours(1).toMillis();

    private final Main main;
    private URI uri;
    @Getter
    private int delay; // seconds
    private int timeout; // seconds
    private boolean additionalInfo;

    private BukkitTask task = null;
    private HttpClient client;
    private long lastError = -1;
    private long lastSuccess = -1;

    public void reload(URI uri, int delay, int timeout){
        this.uri = uri;
        this.delay = delay;
        this.timeout = timeout;
        additionalInfo = main.getConfig().getBoolean("additional-meta", true);

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .build();
    }

    public Requester(Main main) {
        this.main = main;
    }

    public boolean isUpdating(){
        return task != null && !task.isCancelled();
    }

    public void start(){
        if (task != null) task.cancel();
        task = new RunHandle().runTaskTimerAsynchronously(main, 0, delay * 20L);
        Main.LOGGER.info("Started requesting whitelist data every {} seconds", delay);
    }

    public void stop(){
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public synchronized long lastSuccessfully(){
        synchronized (this) {
            return lastSuccess;
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private String getUserAgent(){
        return main.getName()+"/"+main.getPluginMeta().getVersion();
    }

    private void addHeaders(HttpRequest.Builder builder){
        builder.header("User-Agent", getUserAgent());
        if (!additionalInfo) return;
        var server = Bukkit.getServer();
        builder.header("Server-Online", String.valueOf(server.getOnlinePlayers().size()));
    }

    private void handle(String data){
        try {
            var list = Main.GSON.fromJson(data, LIST_TYPE);
            main.handleResponse(new HashSet<>(list));
        } catch (JsonSyntaxException e) {
            Main.LOGGER.warn("Failed to parse response json", e);
            throw new RuntimeException("Failed to parse response json", e);
        }
    }

    public class RunHandle extends BukkitRunnable {
        @Override
        public void run() {
            request(e -> {});
        }
    }

    public void request(Consumer<Exception> errorConsumer) {
        var request = HttpRequest.newBuilder(uri)
                .header("User-Agent", getUserAgent())
                .timeout(Duration.ofSeconds(timeout))
                .GET();
        addHeaders(request);
        try {
            var data = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (lastError != -1) {
                var seconds = (int) ((System.currentTimeMillis() - lastError) / 1000);
                Main.LOGGER.info("Successfully connected back after {} seconds", seconds);
            }
            lastError = -1;
            lastSuccess = System.currentTimeMillis();
            int code = data.statusCode();
            if (code < 200 || code >= 300) {
                Main.LOGGER.warn("Received not-ok code ({}). Aborting", code);
                errorConsumer.accept(new RuntimeException("Received not-ok code ("+ code +"). Aborting"));
                return;
            }
            try {
                handle(data.body());
                errorConsumer.accept(null);
            } catch (Exception e){
                errorConsumer.accept(e);
            }
        } catch (HttpTimeoutException | ConnectException e) {
            if (lastError == -1 || System.currentTimeMillis() - lastError > logDelay) {
                final int seconds;
                if (lastError == -1){
                    seconds = timeout;
                } else {
                    seconds = (int) ((System.currentTimeMillis() - lastError) / 1000);
                }
                Main.LOGGER.warn("Failed to connect to whitelist after {} seconds", seconds);
                lastError = System.currentTimeMillis();
                errorConsumer.accept(new RuntimeException("Failed to connect to whitelist after "+seconds+" seconds"));
            } else {
                errorConsumer.accept(new RuntimeException("Failed to connect to whitelist after "+timeout+" seconds"));
            }
        } catch (Exception e) {
            Main.LOGGER.warn("Unknown exception while requesting whitelist", e);
            errorConsumer.accept(e.getMessage() != null ? e : new RuntimeException("Unknown exception while requesting whitelist", e));
        }
    }
}
