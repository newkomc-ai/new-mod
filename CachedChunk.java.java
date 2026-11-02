package com.example.chunkramcache;

import net.minecraft.world.chunk.WorldChunk;

public record CachedChunk(int x, int z, long lastSeenMs, WorldChunk chunk) {}