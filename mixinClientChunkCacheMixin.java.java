package com.example.chunkramcache.mixin;

import com.example.chunkramcache.ChunkCacheManager;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkManager.class)
public abstract class ClientChunkCacheMixin {

    /**
     * Коли ванільний клієнт питає чанк, який уже вивантажився з ванільної
     * карти, але він є в нашому холодному кеші — віддаємо його з RAM,
     * уникаючи повторного запиту до сервера/диска.
     */
    @Inject(method = "getChunk(II)Lnet/minecraft/world/chunk/WorldChunk;",
            at = @At("HEAD"), cancellable = true)
    private void chunkramcache$serveFromCache(int x, int z, CallbackInfoReturnable<WorldChunk> cir) {
        WorldChunk cached = ChunkCacheManager.getCached(x, z);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }
}