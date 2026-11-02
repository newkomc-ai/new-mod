package com.example.chunkramcache;

import com.example.chunkramcache.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Тримає "гарячі" чанки навколо гравця у RAM, а чанки ПОЗА FOV
 * і далі за configured render distance зберігає в окремому кеш-пулі
 * з TTL, щоб уникнути повторного запиту в сервера/диска.
 *
 * НЕ замінює Distant Horizons: тут зберігаються лише ті WorldChunk,
 * які клієнт уже отримав. Чанки за межами view-distance сервер не надсилає.
 */
public final class ChunkCacheManager {

    private static final Map<Long, CachedChunk> coldCache = new ConcurrentHashMap<>();
    private static boolean enabled = false;

    // Кеш-пул обмежуємо, щоб не з'їсти всю RAM
    private static final int MAX_COLD_CHUNKS = 4096;      // ~ десятки МБ
    private static final long COLD_TTL_MS = 5 * 60 * 1000; // 5 хв

    private ChunkCacheManager() {}

    public static void setEnabled(boolean on) {
        enabled = on;
        if (!on) coldCache.clear();
    }

    public static void tick(MinecraftClient mc) {
        if (!enabled) return;
        ClientWorld world = mc.world;
        ClientPlayerEntity player = mc.player;
        if (world == null || player == null) return;

        long now = System.currentTimeMillis();
        Vec3d eye = player.getPos();
        float yaw = player.getYaw();

        // 1) Оновлюємо "холодний" кеш: усе, що поза FOV і поза render-distance*0.75 — туди
        int renderDist = mc.options.getViewDistance().getValue();
        int hotRadius = Math.max(2, (int) (renderDist * 0.75));
        int captureRadius = renderDist + ModConfig.extraRadius;

        for (int cx = -captureRadius; cx <= captureRadius; cx++) {
            for (int cz = -captureRadius; cz <= captureRadius; cz++) {
                ChunkPos pos = new ChunkPos(
                        player.getChunkPos().x + cx,
                        player.getChunkPos().z + cz);

                if (!world.isChunkLoaded(pos.x, pos.z)) continue;

                double dist = Math.sqrt(cx * cx + cz * cz);
                if (dist <= hotRadius) continue; // гарячі не кешуємо

                double angle = Math.toDegrees(Math.atan2(cz, cx));
                double delta = Math.abs(normalizeAngle(angle - (yaw + 90f)));

                // Поза FOV (|delta| > fov/2) — у холодний кеш
                if (delta > ModConfig.fov / 2.0) {
                    WorldChunk wc = world.getChunk(pos.x, pos.z);
                    if (wc != null) {
                        coldCache.put(pos.toLong(),
                                new CachedChunk(pos.x, pos.z, now, wc));
                    }
                }
            }
        }

        // 2) Чистимо за TTL та лімітом розміру
        coldCache.entrySet().removeIf(e -> now - e.getValue().lastSeenMs() > COLD_TTL_MS);
        if (coldCache.size() > MAX_COLD_CHUNKS) {
            coldCache.entrySet().stream()
                    .sorted(Comparator.comparingLong(e -> e.getValue().lastSeenMs()))
                    .limit(coldCache.size() - MAX_COLD_CHUNKS)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(coldCache::remove);
        }
    }

    /** Викликається міксином, коли клієнт збирається вивантажити чанк. */
    public static boolean tryRetain(WorldChunk chunk) {
        if (!enabled) return false;
        return coldCache.containsKey(chunk.getPos().toLong());
    }

    /** Повертає закешований чанк, якщо є (для міксина завантаження). */
    public static WorldChunk getCached(int x, int z) {
        CachedChunk c = coldCache.get(ChunkPos.toLong(x, z));
        return c == null ? null : c.chunk();
    }

    private static double normalizeAngle(double a) {
        while (a > 180) a -= 360;
        while (a < -180) a += 360;
        return a;
    }
}