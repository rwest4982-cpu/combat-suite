package dev.combatsuite.gui;

import dev.combatsuite.Config;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Tabbed settings screen. Every change is written to config/combatsuite.json when you close it. */
public class ConfigScreen extends Screen {
    private static final String[] TABS = {"Trigger Bot", "Aim", "Stun Web", "Head Web", "Targets"};
    private static final String[] AIM_POINTS = {"Eyes", "Center", "Feet"};

    private static int lastTab = 0;

    private final Screen parent;
    private int tab = lastTab;
    private int slot;   // next option slot, two columns

    public ConfigScreen(Screen parent) {
        super(Text.literal("Combat Suite"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        lastTab = tab;
        slot = 0;

        int n = TABS.length;
        int tw = Math.min(92, (width - 16) / n);
        int startX = (width - tw * n) / 2;
        for (int i = 0; i < n; i++) {
            int idx = i;
            ButtonWidget b = ButtonWidget.builder(Text.literal(TABS[i]), btn -> {
                tab = idx;
                clearAndInit();
            }).dimensions(startX + i * tw, 22, tw, 20).build();
            b.active = i != tab;
            addDrawableChild(b);
        }

        Config c = Config.I;
        switch (tab) {
            case 0 -> {
                toggle("Trigger Bot", () -> c.triggerEnabled, v -> c.triggerEnabled = v,
                        "Attack automatically when a target is in range.");
                slider("Range", 1.0, 6.0, c.triggerRange, false, v -> c.triggerRange = v,
                        "Distance from your eyes to the target's hitbox.");
                slider("Attack Strength", 0.1, 1.0, c.triggerCooldown, false, v -> c.triggerCooldown = v,
                        "Attack cooldown charge needed before swinging. 1.0 = full charge.");
                slider("Min Delay (ticks)", 0, 20, c.triggerMinDelayTicks, true, v -> c.triggerMinDelayTicks = (int) v,
                        "Extra ticks to wait between hits.");
                slider("Random Delay (ticks)", 0, 20, c.triggerRandomTicks, true, v -> c.triggerRandomTicks = (int) v,
                        "Adds 0 to N random ticks on top of the minimum delay.");
                toggle("Require Weapon", () -> c.triggerRequireWeapon, v -> c.triggerRequireWeapon = v,
                        "Only hit while holding a sword, axe, mace or trident.");
                toggle("Pause While Using Item", () -> c.triggerPauseWhileUsing, v -> c.triggerPauseWhileUsing = v,
                        "Do not hit while blocking, eating or drawing a bow.");
                toggle("Crits Only", () -> c.triggerCritsOnly, v -> c.triggerCritsOnly = v,
                        "Only hit while falling, so every hit is a critical.");
            }
            case 1 -> {
                toggle("Silent Aim", () -> c.silentAim, v -> c.silentAim = v,
                        "Server-side rotation only. Your camera never moves. With Trigger Bot this also lets it hit the nearest target without your crosshair on it.");
                cycle("Aim Point", AIM_POINTS, () -> c.aimPoint, v -> c.aimPoint = v,
                        "Which part of the target Silent Aim points at.");
                toggle("Restore Rotation", () -> c.silentAimRestore, v -> c.silentAimRestore = v,
                        "Send your real rotation again right after each action.");
            }
            case 2 -> {
                toggle("Stun Web", () -> c.stunWebEnabled, v -> c.stunWebEnabled = v,
                        "Axe the shield, launch them with a hit, web where they land. Head Web takes priority if both are on.");
                slider("Range (also Head Web)", 1.0, 6.0, c.stunRange, false, v -> c.stunRange = v,
                        "How close a target must be to start a combo. Hits only land within about 3 blocks.");
                toggle("Break Shield First", () -> c.stunBreakShield, v -> c.stunBreakShield = v,
                        "Swap to an axe and hit until their shield is disabled.");
                slider("Shield Swap Delay", 0, 10, c.stunShieldSwapDelay, true, v -> c.stunShieldSwapDelay = (int) v,
                        "Ticks to wait before swapping to the axe.");
                slider("Shield Hit Retries", 1, 10, c.stunShieldHitRetries, true, v -> c.stunShieldHitRetries = (int) v,
                        "Maximum axe hits before moving on.");
                slider("Launch Delay", 0, 10, c.stunLaunchDelay, true, v -> c.stunLaunchDelay = (int) v,
                        "Ticks to wait before the launch hit.");
                slider("Web Delay", 0, 10, c.stunWebDelay, true, v -> c.stunWebDelay = (int) v,
                        "Ticks after they leave the ground before the web is placed.");
                slider("Landing Prediction", 0, 12, c.stunPredictTicks, false, v -> c.stunPredictTicks = v,
                        "How many ticks of horizontal drift to predict for the landing spot.");
                slider("Restore Delay", 0, 10, c.stunRestoreDelay, true, v -> c.stunRestoreDelay = (int) v,
                        "Ticks before swapping back to your original hotbar slot.");
                slider("Combo Cooldown", 0, 100, c.stunComboCooldown, true, v -> c.stunComboCooldown = (int) v,
                        "Ticks to wait before another combo can start.");
                slider("Launch Timeout", 4, 40, c.stunHitTimeout, true, v -> c.stunHitTimeout = (int) v,
                        "Ticks to wait for them to leave the ground before hitting again.");
            }
            case 3 -> {
                toggle("Head Web", () -> c.headWebEnabled, v -> c.headWebEnabled = v,
                        "Launch them, web the cell their head will be in, box it, plate the floor. Uses the range from the Stun Web tab.");
                slider("Head Web Delay", 0, 10, c.headWebDelay, true, v -> c.headWebDelay = (int) v,
                        "Ticks after they leave the ground before the head web is placed.");
                slider("Landing Prediction", 0, 12, c.headPredictTicks, false, v -> c.headPredictTicks = v,
                        "How many ticks of horizontal drift to predict.");
                toggle("Box Head", () -> c.headBoxEnabled, v -> c.headBoxEnabled = v,
                        "Surround the head cell with any full block from your hotbar (never cobweb).");
                slider("Box Block Delay", 0, 10, c.headBoxDelay, true, v -> c.headBoxDelay = (int) v,
                        "Ticks between each box block.");
                toggle("Box Top", () -> c.headBoxTop, v -> c.headBoxTop = v,
                        "Also cap the head cell from above.");
                slider("Scaffold Depth", 0, 2, c.headScaffoldDepth, true, v -> c.headScaffoldDepth = (int) v,
                        "Support blocks placed under a box block when it has nothing to click on.");
                toggle("Pressure Plate", () -> c.headPlate, v -> c.headPlate = v,
                        "Place a pressure plate from your hotbar on the floor under them.");
                slider("Plate Delay", 0, 10, c.headPlateDelay, true, v -> c.headPlateDelay = (int) v,
                        "Ticks between the last box block and the plate.");
                toggle("Fallback Feet Web", () -> c.headFallbackFeetWeb, v -> c.headFallbackFeetWeb = v,
                        "If the head cell has nothing to place against, web their feet instead (the plate is skipped).");
            }
            default -> {
                toggle("Players and Bots", () -> c.targetPlayers, v -> c.targetPlayers = v,
                        "Other players, including fake players from mods like Carpet.");
                toggle("Hostile Mobs", () -> c.targetHostile, v -> c.targetHostile = v, "Zombies, skeletons, creepers and so on.");
                toggle("Neutral Mobs", () -> c.targetNeutral, v -> c.targetNeutral = v, "Wolves, iron golems, bees and so on.");
                toggle("Passive Mobs", () -> c.targetPassive, v -> c.targetPassive = v, "Animals and villagers.");
                toggle("Ignore Invisible", () -> c.ignoreInvisible, v -> c.ignoreInvisible = v, "Skip invisible targets.");
                toggle("Require Line of Sight", () -> c.requireLineOfSight, v -> c.requireLineOfSight = v,
                        "Skip targets you cannot see.");
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset All"), b -> {
            Config.I = new Config();
            clearAndInit();
        }).dimensions(width / 2 - 155, height - 28, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 + 55, height - 28, 100, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Singleplayer only. Modules do nothing on multiplayer servers.").formatted(Formatting.GRAY),
                width / 2, height - 42, 0xFFAAAAAA);
    }

    @Override
    public void close() {
        Config.save();
        client.setScreen(parent);
    }

    // ------------------------------------------------------------------ widget factories

    private int[] next() {
        int col = slot % 2;
        int row = slot / 2;
        slot++;
        return new int[]{col == 0 ? width / 2 - 155 : width / 2 + 5, 50 + row * 24};
    }

    private void toggle(String label, BooleanSupplier get, Consumer<Boolean> set, String tip) {
        int[] p = next();
        ButtonWidget b = ButtonWidget.builder(toggleText(label, get.getAsBoolean()), btn -> {
            set.accept(!get.getAsBoolean());
            btn.setMessage(toggleText(label, get.getAsBoolean()));
        }).dimensions(p[0], p[1], 150, 20).tooltip(Tooltip.of(Text.literal(tip))).build();
        addDrawableChild(b);
    }

    private static Text toggleText(String label, boolean on) {
        return Text.literal(label + ": ").append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
    }

    private void cycle(String label, String[] names, IntSupplier get, IntConsumer set, String tip) {
        int[] p = next();
        ButtonWidget b = ButtonWidget.builder(Text.literal(label + ": " + names[get.getAsInt()]), btn -> {
            set.accept((get.getAsInt() + 1) % names.length);
            btn.setMessage(Text.literal(label + ": " + names[get.getAsInt()]));
        }).dimensions(p[0], p[1], 150, 20).tooltip(Tooltip.of(Text.literal(tip))).build();
        addDrawableChild(b);
    }

    private void slider(String label, double min, double max, double cur, boolean integer, DoubleConsumer set, String tip) {
        int[] p = next();
        Slider s = new Slider(p[0], p[1], 150, 20, label, min, max, cur, integer, set);
        s.setTooltip(Tooltip.of(Text.literal(tip)));
        addDrawableChild(s);
    }

    private static class Slider extends SliderWidget {
        private final String label;
        private final double min, max;
        private final boolean integer;
        private final DoubleConsumer setter;

        Slider(int x, int y, int w, int h, String label, double min, double max, double cur, boolean integer, DoubleConsumer setter) {
            super(x, y, w, h, Text.empty(), Math.max(0, Math.min(1, (cur - min) / (max - min))));
            this.label = label;
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.setter = setter;
            updateMessage();
        }

        private double real() {
            double v = min + value * (max - min);
            return integer ? Math.round(v) : Math.round(v * 100.0) / 100.0;
        }

        @Override
        protected void updateMessage() {
            double v = real();
            setMessage(Text.literal(label + ": " + (integer ? String.valueOf((int) v) : String.valueOf(v))));
        }

        @Override
        protected void applyValue() {
            setter.accept(real());
        }
    }
}
