package com.farmbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FarmScreen extends Screen {
    private EditBox boxA, boxB;
    private StringWidget nextMove, originInfo;
    private Button startButton;

    public FarmScreen() {
        super(Component.literal("Farm Bot - marchewki"));
    }

    @Override
    protected void init() {
        int x = 20, y = 24;
        addRenderableWidget(new StringWidget(x, y, 150, 10, Component.literal("Źródeł wody w osi X:"), font));
        boxA = new EditBox(font, x, y + 12, 60, 18, Component.literal("A"));
        boxA.setFilter(s -> s.matches("\\d{0,2}"));
        boxA.setValue(String.valueOf(FarmRunner.a));
        addRenderableWidget(boxA);

        addRenderableWidget(new StringWidget(x, y + 38, 150, 10, Component.literal("Źródeł wody w osi Z:"), font));
        boxB = new EditBox(font, x, y + 50, 60, 18, Component.literal("B"));
        boxB.setFilter(s -> s.matches("\\d{0,2}"));
        boxB.setValue(String.valueOf(FarmRunner.b));
        addRenderableWidget(boxB);

        addRenderableWidget(Button.builder(Component.literal("Zatwierdź wymiary"), b -> applySize())
                .bounds(x, y + 78, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Ustaw start tu (róg NW)"), b -> setOrigin())
                .bounds(x, y + 102, 130, 20).build());
        startButton = Button.builder(Component.literal(FarmRunner.running ? "Stop" : "Start"), b -> toggle())
                .bounds(x, y + 126, 130, 20).build();
        addRenderableWidget(startButton);
        addRenderableWidget(Button.builder(Component.literal("Zamknij"), b -> onClose())
                .bounds(x, y + 150, 130, 20).build());

        originInfo = new StringWidget(x, height - 40, width - 40, 10, Component.literal(""), font);
        nextMove = new StringWidget(x, height - 26, width - 40, 10, Component.literal(""), font);
        addRenderableWidget(originInfo);
        addRenderableWidget(nextMove);
    }

    private void applySize() {
        int a = parse(boxA.getValue()), b = parse(boxB.getValue());
        FarmRunner.setSize(a, b);
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 1; }
    }

    private void setOrigin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        FarmRunner.origin = mc.player.blockPosition().below();
        FarmRunner.index = 0;
        FarmRunner.status = "Start ustawiony. Kliknij Start.";
    }

    private void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (FarmRunner.running) {
            FarmRunner.stop(mc);
        } else {
            if (FarmRunner.origin == null) { FarmRunner.status = "Najpierw ustaw start."; return; }
            applySize();
            FarmRunner.running = true;
        }
        startButton.setMessage(Component.literal(FarmRunner.running ? "Stop" : "Start"));
    }

    @Override
    public void tick() {
        nextMove.setMessage(Component.literal(FarmRunner.status));
        originInfo.setMessage(Component.literal(FarmRunner.origin == null
                ? "Start: nie ustawiony (stań w rogu farmy i kliknij 'Ustaw start')"
                : "Start (róg NW): " + FarmRunner.origin.toShortString() + "  | farma idzie na wschód (+X) i południe (+Z)"));
        startButton.setMessage(Component.literal(FarmRunner.running ? "Stop" : "Start"));
    }

    @Override
    public boolean isPauseScreen() {
        return false; // gra działa dalej, bot się rusza
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        FarmPlan plan = FarmRunner.plan;
        int px = 180, py = 24;
        int cell = Math.max(1, Math.min(4, 230 / Math.max(plan.width, plan.depth)));
        int w = plan.width * cell, d = plan.depth * cell;

        g.fill(px - 2, py - 2, px + w + 2, py + d + 2, 0xFF000000);
        g.fill(px, py, px + w, py + d, 0xFF7B5A2E); // pole

        // woda
        for (int dz = 4; dz < plan.depth; dz += 9)
            for (int dx = 4; dx < plan.width; dx += 9)
                g.fill(px + dx * cell, py + dz * cell, px + (dx + 1) * cell, py + (dz + 1) * cell, 0xFF2E86DE);

        // zrobione
        int limit = Math.min(FarmRunner.index, plan.steps.size());
        for (int i = 0; i < limit; i++) {
            FarmPlan.Step s = plan.steps.get(i);
            if (s.type() == FarmPlan.Type.PLANT)
                g.fill(px + s.dx() * cell, py + s.dz() * cell, px + (s.dx() + 1) * cell, py + (s.dz() + 1) * cell, 0xFF4CAF50);
        }
        // następny ruch: czerwona kropka
        if (FarmRunner.index < plan.steps.size()) {
            FarmPlan.Step s = plan.steps.get(FarmRunner.index);
            int m = Math.max(cell, 3);
            int cx = px + s.dx() * cell + cell / 2, cy = py + s.dz() * cell + cell / 2;
            g.fill(cx - m, cy - m, cx + m + 1, cy + m + 1, 0xFFFF2020);
        }
    }
}
