package pl.pvplist;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Liczy fragi niezaleznie od czatu: jesli uderzyles gracza i w ciagu 10 sekund jego
 * zdrowie spadlo do zera, zapisuje frag.
 */
public final class FragTracker {
    private FragTracker() {
    }

    private static final long WINDOW_MS = 10_000L;

    private static final Map<String, Long> attacked = new HashMap<>();
    private static final Set<String> counted = new HashSet<>();

    public static void init() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && entity instanceof PlayerEntity target
                    && player == MinecraftClient.getInstance().player) {
                attacked.put(KillData.key(target.getName().getString()), System.currentTimeMillis());
            }
            return ActionResult.PASS;
        });
    }

    public static void tick(MinecraftClient c) {
        if (c.world == null || c.player == null) return;
        long now = System.currentTimeMillis();
        attacked.values().removeIf(t -> now - t > WINDOW_MS);

        String dim = c.world.getRegistryKey().getValue().getPath();
        for (AbstractClientPlayerEntity p : c.world.getPlayers()) {
            if (p == c.player) continue;
            String name = p.getName().getString();
            String key = KillData.key(name);

            boolean dead = p.isDead() || p.getHealth() <= 0.0F;
            if (!dead) {
                counted.remove(key);
                continue;
            }
            if (counted.contains(key)) continue;
            if (!attacked.containsKey(key)) continue;

            counted.add(key);
            attacked.remove(key);
            if (KillData.addKill(name, p.getBlockPos(), dim, null)) {
                c.player.sendMessage(Text.literal("[PvpList] Frag: " + name).formatted(Formatting.GREEN), true);
            }
        }
    }
}
