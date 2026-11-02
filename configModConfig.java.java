package com.example.chunkramcache.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
    public static float fov = 90f;
    public static int extraRadius = 6; // "6 -> 12": додаткові чанки для кешу

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("chunkramcache.json");

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                ModConfig c = GSON.fromJson(Files.readString(PATH), ModConfig.class);
                if (c != null) { fov = c.fov; extraRadius = c.extraRadius; }
            } else {
                save();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.writeString(PATH, GSON.toJson(new ModConfig()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}