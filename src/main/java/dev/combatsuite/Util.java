package dev.combatsuite;

import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EmptyBlockView;

import java.util.Set;
import java.util.function.Predicate;

/** Shared helpers: targeting, hotbar, aiming, block placement, landing prediction. */
public final class Util {
    private Util() {}

    /** Every module is gated on this. Singleplayer (integrated server) only, no screen open. */
    public static boolean active(MinecraftClient mc) {
        return mc.player != null
                && mc.world != null
                && mc.interactionManager != null
                && mc.getNetworkHandler() != null
                && mc.isInSingleplayer()
                && mc.currentScreen == null
                && !mc.player.isSpectator();
    }

    // ------------------------------------------------------------------ targeting

    public static boolean validTarget(Entity e, ClientPlayerEntity self) {
        if (!(e instanceof LivingEntity le) || e == self || !le.isAlive() || e instanceof ArmorStandEntity) return false;
        Config c = Config.I;
        if (c.ignoreInvisible && e.isInvisible()) return false;
        if (e instanceof PlayerEntity p) {
            if (p.isSpectator()) return false;
            if (!c.targetPlayers) return false;
        } else if (e instanceof Monster) {
            if (!c.targetHostile) return false;
        } else if (e instanceof Angerable) {
            if (!c.targetNeutral) return false;
        } else if (!c.targetPassive) {
            return false;
        }
        return !c.requireLineOfSight || self.canSee(e);
    }

    /** Distance from the player's eyes to the closest point on the target's hitbox. */
    public static double reachDist(ClientPlayerEntity self, Entity e) {
        Vec3d eye = self.getEyePos();
        Box b = e.getBoundingBox();
        Vec3d closest = new Vec3d(
                MathHelper.clamp(eye.x, b.minX, b.maxX),
                MathHelper.clamp(eye.y, b.minY, b.maxY),
                MathHelper.clamp(eye.z, b.minZ, b.maxZ));
        return eye.distanceTo(closest);
    }

    public static LivingEntity nearest(MinecraftClient mc, double range) {
        ClientPlayerEntity self = mc.player;
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!validTarget(e, self)) continue;
            double d = reachDist(self, e);
            if (d <= range && d < bestD) {
                bestD = d;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ hotbar

    public static int findSlot(ClientPlayerEntity p, Predicate<ItemStack> pred) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && pred.test(s)) return i;
        }
        return -1;
    }

    public static void select(ClientPlayerEntity p, int slot) {
        if (slot >= 0 && slot < 9 && p.getInventory().getSelectedSlot() != slot) {
            p.getInventory().setSelectedSlot(slot);
        }
    }

    public static boolean isWeb(ItemStack s) { return s.isOf(Items.COBWEB); }
    public static boolean isAxe(ItemStack s) { return s.isIn(ItemTags.AXES); }
    public static boolean isSword(ItemStack s) { return s.isIn(ItemTags.SWORDS); }

    public static boolean isWeapon(ItemStack s) {
        return isSword(s) || isAxe(s) || s.isOf(Items.MACE) || s.isOf(Items.TRIDENT);
    }

    public static boolean isPlate(ItemStack s) {
        return s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof AbstractPressurePlateBlock;
    }

    /** Any full solid block from the hotbar that is not a cobweb, plate or falling block. */
    public static boolean isBoxBlock(ItemStack s) {
        if (!(s.getItem() instanceof BlockItem bi)) return false;
        if (bi.getBlock() == Blocks.COBWEB || bi.getBlock() instanceof FallingBlock
                || bi.getBlock() instanceof AbstractPressurePlateBlock) return false;
        return bi.getBlock().getDefaultState().isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
    }

    // ------------------------------------------------------------------ aiming

    public static Vec3d aimPoint(Entity e) {
        return switch (Config.I.aimPoint) {
            case 1 -> e.getBoundingBox().getCenter();
            case 2 -> new Vec3d(e.getX(), e.getY() + 0.2, e.getZ());
            default -> new Vec3d(e.getX(), e.getEyeY(), e.getZ());
        };
    }

    /**
     * Runs an action. With silent aim on, the server is told we look at the point just before the
     * action and told our real rotation again right after, so the camera never moves.
     */
    public static void withAim(MinecraftClient mc, Vec3d point, Runnable action) {
        ClientPlayerEntity p = mc.player;
        if (!Config.I.silentAim) {
            action.run();
            return;
        }
        Vec3d eye = p.getEyePos();
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        mc.getNetworkHandler().sendPacket(
                yaw, MathHelper.clamp(pitch, -90f, 90f), p.isOnGround(), p.horizontalCollision));
        action.run();
        if (Config.I.silentAimRestore) {
            mc.getNetworkHandler().sendPacket(
                    p.getYaw(), p.getPitch(), p.isOnGround(), p.horizontalCollision));
        }
    }

    public static void hit(MinecraftClient mc, Entity target) {
        withAim(mc, aimPoint(target), () -> {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
        });
    }

    // ------------------------------------------------------------------ placing

    /** True if some neighbour of pos is a solid face we could click, counting planned blocks. */
    public static boolean supported(ClientWorld w, BlockPos pos, Set<BlockPos> planned) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            if (planned.contains(n)) return true;
            if (w.getBlockState(n).isSideSolidFullSquare(w, n, d.getOpposite())) return true;
        }
        return false;
    }

    /** Places the held item at pos by clicking an adjacent solid face. Returns true if accepted. */
    public static boolean place(MinecraftClient mc, BlockPos pos) {
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        if (!w.getBlockState(pos).isReplaceable()) return false;
        if (p.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > 4.5 * 4.5) return false;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            BlockState ns = w.getBlockState(n);
            Direction face = d.getOpposite();
            if (ns.isAir() || !ns.isSideSolidFullSquare(w, n, face)) continue;
            Vec3d hit = Vec3d.ofCenter(n).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            BlockHitResult bhr = new BlockHitResult(hit, face, n, false);
            boolean[] ok = {false};
            withAim(mc, hit, () -> {
                ActionResult r = mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, bhr);
                if (r.isAccepted()) {
                    p.swingHand(Hand.MAIN_HAND);
                    ok[0] = true;
                }
            });
            if (ok[0]) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ prediction

    /** Horizontal landing estimate: current position plus decaying air drift. */
    public static Vec3d predictXZ(LivingEntity t, double ticks) {
        Vec3d v = t.getVelocity();
        double f = (1.0 - Math.pow(0.91, ticks)) / 0.09;
        return new Vec3d(t.getX() + v.x * f, t.getY(), t.getZ() + v.z * f);
    }

    /** Feet cell the target will stand in when it comes down at the given column, or null. */
    public static BlockPos landingFeet(ClientWorld w, Vec3d column, double fromY) {
        BlockPos.Mutable m = new BlockPos.Mutable(MathHelper.floor(column.x), MathHelper.floor(fromY), MathHelper.floor(column.z));
        for (int i = 0; i < 48; i++) {
            BlockPos below = m.down();
            if (!w.getBlockState(below).getCollisionShape(w, below).isEmpty()) return m.toImmutable();
            m.move(Direction.DOWN);
        }
        return null;
    }
}
