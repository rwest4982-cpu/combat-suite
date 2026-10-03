package dev.combatsuite;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** All settings live here as plain public fields. Saved as config/combatsuite.json. */
public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("combatsuite.json");

    public static Config I = new Config();

    // ---------- Targets ----------
    public boolean targetPlayers = true;      // includes carpet-style fake players / bots
    public boolean targetHostile = true;
    public boolean targetNeutral = false;
    public boolean targetPassive = false;
    public boolean ignoreInvisible = false;
    public boolean requireLineOfSight = true;

    // ---------- Trigger Bot ----------
    public boolean triggerEnabled = false;
    public double triggerRange = 3.0;          // blocks
    public double triggerCooldown = 0.92;      // attack strength needed (0-1)
    public int triggerMinDelayTicks = 0;       // extra ticks between hits
    public int triggerRandomTicks = 0;         // random 0..N extra ticks per hit
    public boolean triggerRequireWeapon = false;
    public boolean triggerPauseWhileUsing = true;
    public boolean triggerCritsOnly = false;   // only hit while falling

    // ---------- Aim ----------
    public boolean silentAim = false;          // server-side rotation only, camera untouched
    public int aimPoint = 0;                   // 0 eyes, 1 center, 2 feet
    public boolean silentAimRestore = true;    // send real rotation again after acting

    // ---------- Stun Web ----------
    public boolean stunWebEnabled = false;
    public double stunRange = 3.0;             // used by Head Web too
    public boolean stunBreakShield = true;
    public int stunShieldSwapDelay = 1;        // ticks after locking target
    public int stunShieldHitRetries = 4;
    public int stunLaunchDelay = 1;            // ticks before launch hit
    public int stunWebDelay = 2;               // ticks after target leaves ground
    public double stunPredictTicks = 4.0;      // lookahead for landing spot
    public int stunRestoreDelay = 2;           // ticks before swapping back
    public int stunComboCooldown = 20;
    public int stunHitTimeout = 12;            // give up if target never leaves ground

    // ---------- Head Web ----------
    public boolean headWebEnabled = false;
    public int headWebDelay = 2;               // ticks after target leaves ground
    public double headPredictTicks = 6.0;
    public boolean headBoxEnabled = true;
    public int headBoxDelay = 1;               // ticks between box blocks
    public boolean headBoxTop = true;
    public int headScaffoldDepth = 1;          // support blocks placed when nothing to click on
    public boolean headPlate = true;
    public int headPlateDelay = 2;             // ticks after last box block
    public boolean headFallbackFeetWeb = true; // web at feet if head cell cannot be supported

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) I = c;
            }
        } catch (Exception e) {
            CombatSuite.LOG.warn("Could not read config, using defaults", e);
        }
        save();
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(I));
        } catch (IOException e) {
            CombatSuite.LOG.warn("Could not save config", e);
        }
    }
}
