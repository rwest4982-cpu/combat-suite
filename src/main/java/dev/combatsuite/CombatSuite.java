package dev.combatsuite;

import dev.combatsuite.gui.ConfigScreen;
import dev.combatsuite.module.Combo;
import dev.combatsuite.module.TriggerBot;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Combat Suite. Everything is gated on a singleplayer (integrated server) world.
 * If the client is connected to a multiplayer server, no module does anything.
 */
public class CombatSuite implements ClientModInitializer {
    public static final String ID = "combatsuite";
    public static final Logger LOG = LoggerFactory.getLogger(ID);

    public static final TriggerBot TRIGGER = new TriggerBot();
    public static final Combo COMBO = new Combo();

    private static KeyBinding openGui, toggleTrigger, toggleStun, toggleHead;

    @Override
    public void onInitializeClient() {
        Config.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(ID, "keys"));
        openGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.combatsuite.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        toggleTrigger = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.combatsuite.trigger", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        toggleStun = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.combatsuite.stunweb", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        toggleHead = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.combatsuite.headweb", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

        ClientTickEvents.END_CLIENT_TICK.register(CombatSuite::tick);
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null) return;

        while (openGui.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new ConfigScreen(null));
        }
        while (toggleTrigger.wasPressed()) flip(mc, "Trigger Bot", Config.I.triggerEnabled = !Config.I.triggerEnabled);
        while (toggleStun.wasPressed()) flip(mc, "Stun Web", Config.I.stunWebEnabled = !Config.I.stunWebEnabled);
        while (toggleHead.wasPressed()) flip(mc, "Head Web", Config.I.headWebEnabled = !Config.I.headWebEnabled);

        if (!Util.active(mc)) {
            COMBO.reset();
            return;
        }
        COMBO.tick(mc);
        if (!COMBO.busy()) TRIGGER.tick(mc);
    }

    private static void flip(MinecraftClient mc, String name, boolean on) {
        Config.save();
        mc.player.sendMessage(Text.literal(name + ": " + (on ? "ON" : "OFF")), true);
    }
}
