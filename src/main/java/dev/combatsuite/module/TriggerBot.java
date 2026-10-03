package dev.combatsuite.module;

import dev.combatsuite.Config;
import dev.combatsuite.Util;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Hits when a valid target is within range.
 * Silent aim off: hits whatever your crosshair is on.
 * Silent aim on: hits the nearest valid target in range, no camera movement.
 */
public class TriggerBot {
    private int delay;

    public void tick(MinecraftClient mc) {
        Config c = Config.I;
        if (!c.triggerEnabled) return;
        if (delay > 0) {
            delay--;
            return;
        }
        ClientPlayerEntity p = mc.player;
        if (c.triggerPauseWhileUsing && p.isUsingItem()) return;
        if (c.triggerRequireWeapon && !Util.isWeapon(p.getMainHandStack())) return;
        if (p.getAttackCooldownProgress(0.5f) < c.triggerCooldown) return;
        if (c.triggerCritsOnly && (p.isOnGround() || p.getVelocity().y >= 0 || p.isClimbing() || p.isTouchingWater())) return;

        Entity target = null;
        if (c.silentAim) {
            target = Util.nearest(mc, c.triggerRange);
        } else if (mc.crosshairTarget instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            if (Util.validTarget(e, p) && Util.reachDist(p, e) <= c.triggerRange) target = e;
        }
        if (target == null) return;

        Util.hit(mc, target);
        delay = c.triggerMinDelayTicks
                + (c.triggerRandomTicks > 0 ? ThreadLocalRandom.current().nextInt(c.triggerRandomTicks + 1) : 0);
    }
}
