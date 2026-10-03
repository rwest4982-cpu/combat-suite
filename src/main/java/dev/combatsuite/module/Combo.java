package dev.combatsuite.module;

import dev.combatsuite.CombatSuite;
import dev.combatsuite.Config;
import dev.combatsuite.Util;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Stun Web and Head Web share one state machine:
 *
 *   IDLE -> SHIELD (axe the shield down, optional) -> LAUNCH (hit them upward)
 *        -> AIR (wait until they leave the ground) -> PLACE (queued placements with delays)
 *        -> RESTORE (swap back to your original slot)
 *
 * Stun Web places one cobweb where they will land.
 * Head Web places a cobweb in the cell their head will occupy, boxes that cell with a hotbar block,
 * then puts a pressure plate on the floor under them. If Head Web is enabled it takes priority.
 */
public class Combo {
    private enum State { IDLE, SHIELD, LAUNCH, AIR, PLACE, RESTORE }
    private enum Kind { WEB, BLOCK, PLATE }
    private record Step(BlockPos pos, Kind kind, int delay, BlockPos fallback) {}

    private State state = State.IDLE;
    private LivingEntity target;
    private int wait, cooldown, timeout, shieldHits, launchTries, prevSlot = -1;
    private boolean head, fallbackUsed;
    private final Deque<Step> queue = new ArrayDeque<>();

    public boolean busy() { return state != State.IDLE; }

    public void reset() {
        state = State.IDLE;
        target = null;
        queue.clear();
        wait = 0;
        prevSlot = -1;
    }

    public void tick(MinecraftClient mc) {
        Config c = Config.I;
        if (!c.stunWebEnabled && !c.headWebEnabled) {
            if (busy()) finish(mc, 0);
            return;
        }
        if (cooldown > 0) cooldown--;
        if (wait > 0) {
            wait--;
            return;
        }
        switch (state) {
            case IDLE -> idle(mc);
            case SHIELD -> shield(mc);
            case LAUNCH -> launch(mc);
            case AIR -> air(mc);
            case PLACE -> place(mc);
            case RESTORE -> finish(mc, c.stunComboCooldown);
        }
    }

    // ------------------------------------------------------------------ states

    private void idle(MinecraftClient mc) {
        if (cooldown > 0) return;
        Config c = Config.I;
        ClientPlayerEntity p = mc.player;
        LivingEntity t = Util.nearest(mc, c.stunRange);
        if (t == null) return;
        if (Util.findSlot(p, Util::isWeb) < 0) {
            warn(mc, "no cobweb in your hotbar");
            cooldown = 60;
            return;
        }
        target = t;
        head = c.headWebEnabled;
        prevSlot = p.getInventory().getSelectedSlot();
        shieldHits = 0;
        launchTries = 0;
        fallbackUsed = false;
        queue.clear();
        if (c.stunBreakShield && t.isBlocking()) {
            state = State.SHIELD;
            wait = c.stunShieldSwapDelay;
        } else {
            state = State.LAUNCH;
            wait = c.stunLaunchDelay;
        }
    }

    private void shield(MinecraftClient mc) {
        Config c = Config.I;
        ClientPlayerEntity p = mc.player;
        if (!targetOk(mc)) { finish(mc, 10); return; }
        if (!target.isBlocking()) {
            state = State.LAUNCH;
            wait = c.stunLaunchDelay;
            return;
        }
        int axe = Util.findSlot(p, Util::isAxe);
        if (axe < 0) {
            state = State.LAUNCH;
            return;
        }
        Util.select(p, axe);
        if (p.getAttackCooldownProgress(0.5f) < 0.95f) return;
        Util.hit(mc, target);
        shieldHits++;
        wait = 2;
        if (shieldHits >= c.stunShieldHitRetries) {
            state = State.LAUNCH;
            wait = c.stunLaunchDelay;
        }
    }

    private void launch(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (!targetOk(mc)) { finish(mc, 10); return; }
        if (!target.isOnGround()) {      // already airborne, skip the launch hit
            state = State.AIR;
            timeout = 0;
            return;
        }
        int sword = Util.findSlot(p, Util::isSword);
        Util.select(p, sword >= 0 ? sword : prevSlot);
        if (p.getAttackCooldownProgress(0.5f) < 0.95f) return;
        Util.hit(mc, target);
        state = State.AIR;
        timeout = 0;
        wait = 1;
    }

    private void air(MinecraftClient mc) {
        Config c = Config.I;
        if (target == null || !target.isAlive()) { finish(mc, 10); return; }
        timeout++;
        if (!target.isOnGround() || target.getVelocity().y > 0.1) {
            buildPlan(mc);
            return;
        }
        if (timeout > c.stunHitTimeout) {      // the launch hit did not lift them, try again
            if (++launchTries < 3) {
                state = State.LAUNCH;
            } else {
                finish(mc, c.stunComboCooldown);
            }
        }
    }

    private void place(MinecraftClient mc) {
        Step s = queue.pollFirst();
        if (s == null) {
            state = State.RESTORE;
            wait = Config.I.stunRestoreDelay;
            return;
        }
        execute(mc, s);
        Step next = queue.peekFirst();
        if (next != null) wait = next.delay();
    }

    // ------------------------------------------------------------------ planning

    private void buildPlan(MinecraftClient mc) {
        Config c = Config.I;
        double ticks = head ? c.headPredictTicks : c.stunPredictTicks;
        Vec3d column = Util.predictXZ(target, ticks);
        BlockPos feet = Util.landingFeet(mc.world, column, target.getY());
        if (feet == null) { finish(mc, c.stunComboCooldown); return; }

        queue.clear();
        if (!head) {
            queue.add(new Step(feet, Kind.WEB, c.stunWebDelay, null));
        } else {
            BlockPos h = feet.up();
            queue.add(new Step(h, Kind.WEB, c.headWebDelay, c.headFallbackFeetWeb ? feet : null));

            ClientPlayerEntity p = mc.player;
            if (c.headBoxEnabled && Util.findSlot(p, Util::isBoxBlock) >= 0) {
                Set<BlockPos> planned = new HashSet<>();
                for (Direction d : Direction.Type.HORIZONTAL) {
                    planBlock(mc, h.offset(d), c.headScaffoldDepth, planned);
                }
                if (c.headBoxTop) {
                    // Top block needs something to click on: stack one block on a side wall first.
                    for (Direction d : Direction.Type.HORIZONTAL) {
                        BlockPos support = h.offset(d).up();
                        planBlock(mc, support, 0, planned);
                        if (planned.contains(support)) break;
                    }
                    planBlock(mc, h.up(), 0, planned);
                }
            }
            if (c.headPlate && Util.findSlot(p, Util::isPlate) >= 0) {
                queue.add(new Step(feet, Kind.PLATE, c.headPlateDelay, null));
            }
        }
        state = State.PLACE;
        wait = queue.isEmpty() ? 0 : queue.peekFirst().delay();
    }

    /** Queues a block, first queueing a support block underneath if nothing adjacent can be clicked. */
    private void planBlock(MinecraftClient mc, BlockPos pos, int depth, Set<BlockPos> planned) {
        if (planned.contains(pos) || !mc.world.getBlockState(pos).isReplaceable()) return;
        if (!Util.supported(mc.world, pos, planned)) {
            if (depth <= 0) return;
            BlockPos below = pos.down();
            planBlock(mc, below, depth - 1, planned);
            if (!planned.contains(below)) return;
        }
        queue.add(new Step(pos, Kind.BLOCK, Config.I.headBoxDelay, null));
        planned.add(pos);
    }

    // ------------------------------------------------------------------ helpers

    private void execute(MinecraftClient mc, Step s) {
        ClientPlayerEntity p = mc.player;
        if (s.kind() == Kind.PLATE && fallbackUsed) return;   // the feet cell already holds a web
        int slot = switch (s.kind()) {
            case WEB -> Util.findSlot(p, Util::isWeb);
            case BLOCK -> Util.findSlot(p, Util::isBoxBlock);
            case PLATE -> Util.findSlot(p, Util::isPlate);
        };
        if (slot < 0) return;
        Util.select(p, slot);
        boolean ok = Util.place(mc, s.pos());
        if (!ok && s.fallback() != null) {
            ok = Util.place(mc, s.fallback());
            if (ok) fallbackUsed = true;
        }
        if (!ok) CombatSuite.LOG.debug("Could not place {} at {}", s.kind(), s.pos());
    }

    private boolean targetOk(MinecraftClient mc) {
        return target != null && target.isAlive()
                && Util.validTarget(target, mc.player)
                && Util.reachDist(mc.player, target) <= Config.I.stunRange + 2.0;
    }

    private void finish(MinecraftClient mc, int cd) {
        if (mc.player != null && prevSlot >= 0) Util.select(mc.player, prevSlot);
        reset();
        cooldown = cd;
    }

    private void warn(MinecraftClient mc, String msg) {
        mc.player.sendMessage(Text.literal("[Combat Suite] " + msg), true);
    }
}
