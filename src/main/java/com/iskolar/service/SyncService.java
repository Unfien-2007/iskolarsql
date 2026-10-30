package com.iskolar.service;

import com.iskolar.config.DatabaseConfig;
import com.iskolar.dao.SyncCatalogDao;
import com.iskolar.sync.CatalogApiClient;
import com.iskolar.sync.SyncException;
import com.iskolar.util.Prefs;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Client-side sync pipeline: polls the server's catalog version, pulls the
 * full snapshot when it changed, and hands it to {@link SyncCatalogDao} to
 * replace the local cache atomically. Runs in the background so the UI never
 * blocks; listeners (the discovery sync indicator) are notified on the FX
 * thread.
 */
public final class SyncService {

    /** Snapshot of a sync attempt for UI listeners. */
    public record SyncResult(
            boolean syncing, boolean success, boolean changed, int version, String error) {

        static SyncResult syncing(int localVersion) {
            return new SyncResult(true, true, false, localVersion, null);
        }

        static SyncResult ok(boolean changed, int version) {
            return new SyncResult(false, true, changed, version, null);
        }

        static SyncResult failed(int localVersion, String error) {
            return new SyncResult(false, false, false, localVersion, error);
        }
    }

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SyncCatalogDao store;
    private final CatalogApiClient client;
    private final List<Consumer<SyncResult>> listeners = new CopyOnWriteArrayList<>();

    public SyncService(SyncCatalogDao store, CatalogApiClient client) {
        this.store = store;
        this.client = client;
    }

    /** Shared singleton bound to the app database and the configured server URL. */
    public static SyncService createDefault() {
        return new SyncService(
                new SyncCatalogDao(DatabaseConfig.dataSource()),
                new CatalogApiClient(Prefs.apiUrl()));
    }

    /** Exposed so the admin gateway reuses this client's connection pool. */
    public CatalogApiClient client() {
        return client;
    }

    public void addListener(Consumer<SyncResult> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<SyncResult> listener) {
        listeners.remove(listener);
    }

    /** Cached catalog version (0 before the first sync). */
    public int localVersion() {
        return store.catalogVersion();
    }

    /** Timestamp of the last successful sync, if any. */
    public Optional<String> lastSyncAt() {
        return store.lastSyncAt();
    }

    /**
     * Blocking sync: version poll → snapshot pull → atomic local replace.
     * Never throws — failures come back as a failed {@link SyncResult}.
     */
    public SyncResult syncNow() {
        int local = localVersion();
        try {
            int remote = client.fetchVersion();
            if (remote == local) {
                return SyncResult.ok(false, local);
            }
            var snapshot = client.fetchSnapshot();
            store.replaceCatalog(snapshot);
            return SyncResult.ok(true, snapshot.version());
        } catch (SyncException e) {
            return SyncResult.failed(local, e.getMessage());
        }
    }

    /** Kicks off {@link #syncNow()} on a daemon thread; listeners fire on the FX thread. */
    public void syncInBackground() {
        notify(SyncResult.syncing(localVersion()));
        Thread thread = new Thread(() -> {
            SyncResult result = syncNow();
            javafx.application.Platform.runLater(() -> notify(result));
        }, "iskolar-catalog-sync");
        thread.setDaemon(true);
        thread.start();
    }

    private void notify(SyncResult result) {
        for (Consumer<SyncResult> listener : listeners) {
            listener.accept(result);
        }
    }

    // ------------------------------------------------------------------
    // Display helpers for the sync indicator
    // ------------------------------------------------------------------

    /** Human-friendly 'updated … ago' text for {@code lastSyncAt()}. */
    public String updatedText() {
        Optional<String> last = lastSyncAt();
        if (last.isEmpty()) {
            return "never synced";
        }
        try {
            LocalDateTime at = LocalDateTime.parse(last.get(), TS_FORMAT);
            long minutes = ChronoUnit.MINUTES.between(at, LocalDateTime.now());
            if (minutes < 1) {
                return "updated just now";
            }
            if (minutes < 60) {
                return "updated " + minutes + " min ago";
            }
            long hours = minutes / 60;
            if (hours < 24) {
                return "updated " + hours + " h ago";
            }
            return "updated " + (hours / 24) + " d ago";
        } catch (Exception e) {
            return "updated " + last.get();
        }
    }
}
