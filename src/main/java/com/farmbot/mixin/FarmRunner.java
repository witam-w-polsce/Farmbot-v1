package com.farmbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Wykonuje plan krok po kroku: chodzi, kopie, nalewa wodę, motykuje, sadzi. */
public final class FarmRunner {
    public static int a = 3, b = 3;
    public static FarmPlan plan = new FarmPlan(a, b);
    public static BlockPos origin = null; // róg NW farmy (blok ziemi)
    public static int index = 0;
    public static boolean running = false;
    public static String status = "Ustaw start i kliknij Start.";

    private static int ticks = 0, tries = 0;

    private FarmRunner() {}

    public static void setSize(int na, int nb) {
        a = Math.max(1, Math.min(10, na));
        b = Math.max(1, Math.min(10, nb));
        plan = new FarmPlan(a, b);
        index = 0;
        running = false;
        status = "Plan: " + plan.width + " x " + plan.depth + ", marchewek: " + plan.fieldCount + ", wiader z wodą: " + plan.waterCount;
    }

    public static String describe(FarmPlan.Step s) {
        BlockPos p = origin == null ? BlockPos.ZERO : origin.offset(s.dx(), 0, s.dz());
        String where = "(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")";
        return switch (s.type()) {
            case WATER -> "Następny ruch: woda na " + where;
            case HOE -> "Następny ruch: motyką ziemia na " + where;
            case PLANT -> "Następny ruch: marchewka na " + where;
        };
    }

    public static void stop(Minecraft mc) {
        running = false;
        release(mc);
    }

    private static void release(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
    }

    private static int findSlot(LocalPlayer p, FarmPlan.Type t) {
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.isEmpty()) continue;
            boolean ok = switch (t) {
                case WATER -> st.is(Items.WATER_BUCKET);
                case HOE -> st.is(ItemTags.HOES);
                case PLANT -> st.is(Items.CARROT);
            };
            if (ok) return i;
        }
        return -1;
    }

    private static void aim(LocalPlayer p, Vec3 target) {
        Vec3 eye = p.getEyePosition();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        p.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(dy, h)));
    }

    private static void dig(Minecraft mc, BlockPos pos) {
        if (!mc.gameMode.isDestroying()) mc.gameMode.startDestroyBlock(pos, Direction.UP);
        else mc.gameMode.continueDestroyBlock(pos, Direction.UP);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private static void useOn(Minecraft mc, BlockPos pos) {
        Vec3 hit = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, pos, false));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private static void next() {
        index++;
        tries = 0;
    }

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel lvl = mc.level;
        if (p == null || lvl == null || !running || origin == null) return;
        ticks++;

        if (index >= plan.steps.size()) {
            status = "Farma gotowa!";
            stop(mc);
            return;
        }
        FarmPlan.Step s = plan.steps.get(index);
        status = describe(s) + "  [" + index + "/" + plan.steps.size() + "]";
        if (ticks % 10 == 0) p.displayClientMessage(Component.literal(status), true);

        int slot = findSlot(p, s.type());
        if (slot < 0) {
            release(mc);
            status = "Brakuje w pasku szybkiego dostępu: " + switch (s.type()) {
                case WATER -> "wiadro z wodą";
                case HOE -> "motyka";
                case PLANT -> "marchewki";
            };
            return;
        }

        BlockPos cell = origin.offset(s.dx(), 0, s.dz());

        // dojdź do punktu 1,5 bloku na północ od celu (żeby nie stać w kratce, którą sadzimy)
        double sx = cell.getX() + 0.5, sz = cell.getZ() - 1.5;
        double mx = sx - p.getX(), mz = sz - p.getZ();
        if (Math.hypot(mx, mz) > 0.8) {
            p.setYRot((float) Math.toDegrees(Math.atan2(-mx, mz)));
            mc.options.keyUp.setDown(true);
            mc.options.keyJump.setDown(p.horizontalCollision);
            return;
        }
        release(mc);

        aim(p, new Vec3(cell.getX() + 0.5, cell.getY() + 1.0, cell.getZ() + 0.5));
        p.getInventory().setSelectedSlot(slot);
        if (ticks % 4 != 0) return; // nie spamuj serwera

        BlockState ground = lvl.getBlockState(cell);
        BlockState above = lvl.getBlockState(cell.above());

        switch (s.type()) {
            case WATER -> {
                if (ground.is(Blocks.WATER)) { next(); return; }
                if (!above.isAir()) { dig(mc, cell.above()); return; }
                if (!ground.isAir()) { dig(mc, cell); return; }
                useOn(mc, cell.below());
            }
            case HOE -> {
                if (ground.is(Blocks.FARMLAND)) { next(); return; }
                if (!above.isAir()) { dig(mc, cell.above()); return; }
                useOn(mc, cell);
            }
            case PLANT -> {
                if (above.is(Blocks.CARROTS)) { next(); return; }
                useOn(mc, cell);
            }
        }
        if (++tries > 40) next(); // nie utknij na jednym bloku
    }
}
