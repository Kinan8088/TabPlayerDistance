package com.kinan8088.tabplayerdistance;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.entity.player.PlayerEntity;

import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.io.IOException;

@Environment(EnvType.CLIENT)
public class TabPlayerDistance implements ClientModInitializer {

    /* ---------------- CONFIG ---------------- */

    public static class Config {
        public boolean enabled = true;
        public int updateIntervalTicks = 10;
        public Formatting closeColor = Formatting.GREEN;
        public Formatting midColor = Formatting.YELLOW;
        public Formatting farColor = Formatting.RED;
        public Formatting unloadedColor = Formatting.GRAY;
    }

    public static final Config CONFIG = new Config();

    private static final Path CONFIG_PATH = net.fabricmc.loader.api.FabricLoader
            .getInstance()
            .getConfigDir()
            .resolve("tabplayerdistance.properties");

    /* ---------------- CONSTANTS ---------------- */

    private static final double CLOSE_DISTANCE = 32.0;
    private static final double MID_DISTANCE = 96.0;

    /* ---------------- STATE ---------------- */

    private static KeyBinding OPEN_CONFIG_KEY;
    private int tickCounter = 0;

    /* ---------------- INIT ---------------- */

    @Override
    public void onInitializeClient() {
        loadConfig();

        OPEN_CONFIG_KEY = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "Open config",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_R,
                        "TabPlayerDistance"
                )
        );

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    /* ---------------- TICK ---------------- */

    private void onClientTick(MinecraftClient client) {
        while (OPEN_CONFIG_KEY.wasPressed()) {
            client.setScreen(new ConfigScreen());
        }

        if (!CONFIG.enabled) {
            restoreVanillaNames(client);
            return;
        }

        tickCounter++;
        if (tickCounter % Math.max(1, CONFIG.updateIntervalTicks) != 0) return;

        updateTabList(client);
    }

    /* ---------------- TAB LIST ---------------- */

    private void updateTabList(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        for (PlayerListEntry entry : client.player.networkHandler.getPlayerList()) {

            if (entry.getProfile().getId().equals(client.player.getUuid())) {
                entry.setDisplayName(null);
                continue;
            }

            PlayerEntity target = client.world.getPlayerByUuid(entry.getProfile().getId());
            MutableText name;

            if (target != null) {
                double distance = client.player.distanceTo(target);

                if (distance <= CLOSE_DISTANCE) {
                    name = Text.literal(entry.getProfile().getName()).formatted(CONFIG.closeColor);
                } else if (distance <= MID_DISTANCE) {
                    name = Text.literal(entry.getProfile().getName()).formatted(CONFIG.midColor);
                } else {
                    name = Text.literal(entry.getProfile().getName()).formatted(CONFIG.farColor);
                }
            } else {
                name = Text.literal(entry.getProfile().getName()).formatted(CONFIG.unloadedColor);
            }

            entry.setDisplayName(name);
        }
    }

    private void restoreVanillaNames(MinecraftClient client) {
        if (client.player == null) return;
        for (PlayerListEntry entry : client.player.networkHandler.getPlayerList()) {
            entry.setDisplayName(null);
        }
    }

    /* ---------------- CONFIG IO ---------------- */

    private static void loadConfig() {
        if (!Files.exists(CONFIG_PATH)) {
            saveConfig();
            return;
        }

        Properties props = new Properties();
        try (var in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);

            CONFIG.enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
            CONFIG.updateIntervalTicks = Integer.parseInt(props.getProperty("updateIntervalTicks", "10"));
            CONFIG.closeColor = Formatting.valueOf(props.getProperty("closeColor", "GREEN"));
            CONFIG.midColor = Formatting.valueOf(props.getProperty("midColor", "YELLOW"));
            CONFIG.farColor = Formatting.valueOf(props.getProperty("farColor", "RED"));
            CONFIG.unloadedColor = Formatting.valueOf(props.getProperty("unloadedColor", "GRAY"));

        } catch (Exception ignored) {
        }
    }

    private static void saveConfig() {
        Properties props = new Properties();

        props.setProperty("enabled", Boolean.toString(CONFIG.enabled));
        props.setProperty("updateIntervalTicks", Integer.toString(CONFIG.updateIntervalTicks));
        props.setProperty("closeColor", CONFIG.closeColor.name());
        props.setProperty("midColor", CONFIG.midColor.name());
        props.setProperty("farColor", CONFIG.farColor.name());
        props.setProperty("unloadedColor", CONFIG.unloadedColor.name());

        try (var out = Files.newOutputStream(CONFIG_PATH)) {
            props.store(out, "TabPlayerDistance configuration");
        } catch (IOException ignored) {
        }
    }

    /* ---------------- CONFIG SCREEN ---------------- */

    public static class ConfigScreen extends Screen {

        protected ConfigScreen() {
            super(Text.literal("TabPlayerDistance Config"));
        }

        @Override
        protected void init() {
            int y = 40 + 20; // start y position for actual buttons (+20 because of margin)

            addDrawableChild(ButtonWidget.builder(
                            Text.literal("Enabled: " + CONFIG.enabled),
                            b -> {
                                CONFIG.enabled = !CONFIG.enabled;
                                b.setMessage(Text.literal("Enabled: " + CONFIG.enabled));
                                saveConfig();
                            })
                    .dimensions(width / 2 - 100, y, 200, 20)
                    .build());

            y += 28;

            // ---------------- COLOR DROPDOWNS ----------------
            addColorDropdown(y, "Close Color", CONFIG.closeColor, c -> CONFIG.closeColor = c);
            y += 24;
            addColorDropdown(y, "Mid Color", CONFIG.midColor, c -> CONFIG.midColor = c);
            y += 24;
            addColorDropdown(y, "Far Color", CONFIG.farColor, c -> CONFIG.farColor = c);
            y += 24;
            addColorDropdown(y, "Unloaded Color", CONFIG.unloadedColor, c -> CONFIG.unloadedColor = c);

            y += 40;

            // ---------------- DONE BUTTON ----------------
            addDrawableChild(
                    ButtonWidget.builder(
                            Text.literal("Done"),
                            b -> close()
                    ).dimensions(width / 2 - 100, y, 200, 20).build()
            );
        }

        private void addColorDropdown(int y, String label, Formatting current, java.util.function.Consumer<Formatting> setter) {
            List<Formatting> options = List.of(
                    Formatting.GREEN,
                    Formatting.YELLOW,
                    Formatting.RED,
                    Formatting.AQUA,
                    Formatting.LIGHT_PURPLE,
                    Formatting.WHITE,
                    Formatting.GRAY
            );

            addDrawableChild(
                    CyclingButtonWidget.<Formatting>builder(f -> Text.literal(f.getName()))
                            .values(options)
                            .initially(current)
                            .build(
                                    width / 2 - 100,
                                    y,
                                    200,
                                    20,
                                    Text.literal(label),
                                    (btn, value) -> {
                                        setter.accept(value);
                                        saveConfig();
                                    }
                            )
            );
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Draw background
            this.renderBackground(context, 0, 0, delta);

            // Render buttons and widgets first
            super.render(context, mouseX, mouseY, delta);

            // ---------------- TITLE ----------------
            String title = "TabPlayerRender Config";
            float scale = 2.0f; // scale factor
            int topMargin = 20; // pixels from top after scaling

            // Save current scale
            context.getMatrices().push();

            // Apply scale
            context.getMatrices().scale(scale, scale, 1.0f);

            // Center text accounting for scale
            int x = (int)((this.width / scale - this.textRenderer.getWidth(title)) / 2);
            int y = topMargin / (int)scale; // divide by scale to keep proper distance

            // Draw title
            context.drawTextWithShadow(this.textRenderer, title, x, y, 0xFFFFFF);

            // Restore scale
            context.getMatrices().pop();
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}