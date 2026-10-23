package com.farmbot;

import java.util.ArrayList;
import java.util.List;

/** Czysty plan farmy (bez kodu Minecrafta): siatka pojedynczej wody co 9 bloków, między nimi 8 pól. */
public class FarmPlan {
    public enum Type { WATER, HOE, PLANT }

    public record Step(Type type, int dx, int dz) {}

    public final int a, b, width, depth;
    public final List<Step> steps = new ArrayList<>();
    public int waterCount, fieldCount;

    public FarmPlan(int a, int b) {
        this.a = a;
        this.b = b;
        this.width = a * 9;
        this.depth = b * 9;
        // 1) woda
        for (int dz = 4; dz < depth; dz += 9) {
            for (int dx = 4; dx < width; dx += 9) {
                steps.add(new Step(Type.WATER, dx, dz));
                waterCount++;
            }
        }
        // 2) pole: wężykiem po wierszach, każda kratka: motyka, potem marchewka
        for (int dz = 0; dz < depth; dz++) {
            List<Integer> xs = new ArrayList<>();
            for (int dx = 0; dx < width; dx++) {
                if (isWater(dx, dz)) continue;
                xs.add(dx);
            }
            if (dz % 2 == 1) java.util.Collections.reverse(xs);
            for (int dx : xs) {
                steps.add(new Step(Type.HOE, dx, dz));
                steps.add(new Step(Type.PLANT, dx, dz));
                fieldCount++;
            }
        }
    }

    public static boolean isWater(int dx, int dz) {
        return dx % 9 == 4 && dz % 9 == 4;
    }
}
