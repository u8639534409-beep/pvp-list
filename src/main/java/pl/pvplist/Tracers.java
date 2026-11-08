package pl.pvplist;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Tracery rysowane na ekranie: linia od dolu ekranu do gracza.
 * Nad linia: serca, przedmiot w rece i zbroja gracza.
 * Gracz w zasiegu: pozycja na zywo. Poza zasiegiem: ostatnia znana pozycja (linia przerywana).
 */
public final class Tracers {
    private Tracers() {
    }

    private static final int COLOR_LIVE = 0xFFFF3030;
    private static final int COLOR_OLD = 0xFFFFAA00;

    private static KillData.Info snapshot(AbstractClientPlayerEntity p, long now) {
        return new KillData.Info(
                p.getHealth() / 2f,
                p.getAbsorptionAmount() / 2f,
                p.getMainHandStack().copy(),
                p.getEquippedStack(EquipmentSlot.HEAD).copy(),
                p.getEquippedStack(EquipmentSlot.CHEST).copy(),
                p.getEquippedStack(EquipmentSlot.LEGS).copy(),
                p.getEquippedStack(EquipmentSlot.FEET).copy(),
                now);
    }

    /** Aktualizuje ostatnia znana pozycje i wyposazenie sledzonych graczy, ktorych widac. */
    public static void updateLastSeen(MinecraftClient c) {
        if (c.world == null || KillData.tracers.isEmpty()) return;
        String dim = c.world.getRegistryKey().getValue().getPath();
        long now = System.currentTimeMillis();
        for (AbstractClientPlayerEntity p : c.world.getPlayers()) {
            if (p == c.player) continue;
            String key = KillData.key(p.getName().getString());
            if (KillData.tracers.contains(key)) {
                KillData.lastSeen.put(key, new KillData.Seen(p.getPos().add(0, p.getHeight() / 2, 0), dim, now));
                KillData.lastInfo.put(key, snapshot(p, now));
            }
        }
    }

    public static void render(DrawContext ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.world == null || c.options.hudHidden) return;
        if (KillData.tracers.isEmpty()) return;

        Camera cam = c.gameRenderer.getCamera();
        Vec3d camPos = cam.getPos();
        double yaw = Math.toRadians(cam.getYaw());
        double pitch = Math.toRadians(cam.getPitch());

        Vec3d fwd = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3d up = right.crossProduct(fwd);

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        double tanHalf = Math.tan(Math.toRadians(c.options.getFov().getValue()) / 2.0);
        double aspect = (double) w / h;
        String dim = c.world.getRegistryKey().getValue().getPath();
        long now = System.currentTimeMillis();

        for (String key : new ArrayList<>(KillData.tracers)) {
            AbstractClientPlayerEntity live = null;
            for (AbstractClientPlayerEntity p : c.world.getPlayers()) {
                if (p != c.player && KillData.key(p.getName().getString()).equals(key)) {
                    live = p;
                    break;
                }
            }

            Vec3d target;
            boolean isLive;
            long age = 0;
            KillData.Info info;
            if (live != null) {
                target = live.getPos().add(0, live.getHeight() / 2, 0);
                isLive = true;
                info = snapshot(live, now);
            } else {
                KillData.Seen seen = KillData.lastSeen.get(key);
                if (seen == null || !seen.dim().equals(dim)) continue;
                target = seen.pos();
                isLive = false;
                age = (now - seen.time()) / 1000L;
                info = KillData.lastInfo.get(key);
            }

            Vec3d d = target.subtract(camPos);
            double x = d.dotProduct(right);
            double y = d.dotProduct(up);
            double z = d.dotProduct(fwd);

            double ndcX;
            double ndcY;
            if (z > 0.1) {
                ndcX = x / (z * tanHalf * aspect);
                ndcY = y / (z * tanHalf);
            } else {
                double len = Math.hypot(x, y);
                if (len < 1e-4) {
                    x = 0;
                    y = -1;
                    len = 1;
                }
                ndcX = x / len * 10;
                ndcY = y / len * 10;
            }
            double m = Math.max(Math.abs(ndcX), Math.abs(ndcY));
            if (m > 1) {
                ndcX /= m;
                ndcY /= m;
            }

            float sx = (float) (w / 2.0 * (1 + ndcX * 0.95));
            float sy = (float) (h / 2.0 * (1 - ndcY * 0.9));
            int color = isLive ? COLOR_LIVE : COLOR_OLD;

            drawLine(ctx, w / 2f, h, sx, sy, color, !isLive);

            String name = displayName(key, live);
            String label = isLive
                    ? name + " " + Math.round(d.length()) + "m"
                    : name + " (ost. poz. " + age + "s temu) " + Math.round(d.length()) + "m";

            int labelY = Math.max((int) sy - 12, 46);
            ctx.drawCenteredTextWithShadow(c.textRenderer, Text.literal(label), (int) sx, labelY, color);

            if (info != null) {
                drawInfo(ctx, c, info, (int) sx, labelY - 20);
            }
        }
    }

    /** Serca i przedmiot w rece nad ikonami zbroi i przedmiotu. */
    private static void drawInfo(DrawContext ctx, MinecraftClient c, KillData.Info info, int cx, int iconsY) {
        ItemStack[] icons = {info.head(), info.chest(), info.legs(), info.feet(), info.hand()};
        int totalW = 4 * 18 + 6 + 16;
        int x0 = cx - totalW / 2;
        for (int i = 0; i < icons.length; i++) {
            int ix = x0 + i * 18 + (i == 4 ? 6 : 0);
            ctx.fill(ix - 1, iconsY - 1, ix + 17, iconsY + 17, 0x66000000);
            if (!icons[i].isEmpty()) {
                ctx.drawItem(icons[i], ix, iconsY);
            }
        }

        String hearts = String.format(Locale.ROOT, "%.1f", info.hearts());
        Text line = Text.literal("\u2665 " + hearts).formatted(Formatting.RED);
        if (info.absorption() > 0.05f) {
            line = line.copy().append(Text.literal(String.format(Locale.ROOT, " +%.1f", info.absorption()))
                    .formatted(Formatting.GOLD));
        }
        String hand = info.hand().isEmpty() ? "pusta reka" : info.hand().getName().getString();
        line = line.copy()
                .append(Text.literal(" | ").formatted(Formatting.GRAY))
                .append(Text.literal(hand).formatted(Formatting.WHITE));
        ctx.drawCenteredTextWithShadow(c.textRenderer, line, cx, iconsY - 11, 0xFFFFFFFF);
    }

    private static String displayName(String key, AbstractClientPlayerEntity live) {
        if (live != null) return live.getName().getString();
        for (KillData.Entry e : KillData.deaths) {
            if (KillData.key(e.name()).equals(key)) return e.name();
        }
        return key;
    }

    /** Linia jako obrocony prostokat (dashed = przerywana). */
    private static void drawLine(DrawContext ctx, float x0, float y0, float x1, float y1, int color, boolean dashed) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x0, y0, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(dy, dx)));
        if (dashed) {
            for (float p = 0; p < len; p += 12f) {
                ctx.fill((int) p, -1, (int) Math.min(len, p + 6f), 1, color);
            }
        } else {
            ctx.fill(0, -1, (int) len, 1, color);
        }
        m.pop();
    }
}
