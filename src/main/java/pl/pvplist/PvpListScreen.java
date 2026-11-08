package pl.pvplist;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Okno: blacklista (kto Cie zabil), lista killi, licznik fragow z ostatniej godziny. */
public class PvpListScreen extends Screen {

    private static final int ROW_H = 30;

    private record Hit(int x1, int y1, int x2, int y2, boolean inList, Runnable action) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private int tab = 0; // 0 = blacklista, 1 = kille
    private double scroll = 0;
    private int listTop;
    private int listBottom;

    public PvpListScreen() {
        super(Text.literal("PvP List"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        hits.clear();
        KillData.purge();

        int pw = 330;
        int ph = Math.min(height - 20, 290);
        int left = (width - pw) / 2;
        int top = (height - ph) / 2;

        ctx.fill(left, top, left + pw, top + ph, 0xE6161616);
        ctx.drawBorder(left, top, pw, ph, 0xFF8B0000);

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("PVP LIST").formatted(Formatting.DARK_RED, Formatting.BOLD),
                left + pw / 2, top + 8, 0xFFFFFFFF);

        drawButton(ctx, left + 10, top + 22, 100, 18, "Blacklista", tab == 0, mouseX, mouseY, false, () -> {
            tab = 0;
            scroll = 0;
        });
        drawButton(ctx, left + 114, top + 22, 100, 18, "Kille", tab == 1, mouseX, mouseY, false, () -> {
            tab = 1;
            scroll = 0;
        });

        int lx1 = left + 8;
        int lx2 = left + pw - 8;
        listTop = top + 46;
        listBottom = top + ph - 34;

        List<KillData.BlackRow> black = tab == 0 ? KillData.blacklist() : List.of();
        List<KillData.Entry> killRows = tab == 1 ? KillData.killsNewestFirst() : List.of();
        int rows = tab == 0 ? black.size() : killRows.size();

        int total = rows * ROW_H;
        int viewH = listBottom - listTop;
        double maxScroll = Math.max(0, total - viewH);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        ctx.fill(lx1, listTop, lx2, listBottom, 0x55000000);

        if (rows == 0) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Brak wpisow z ostatniej godziny").formatted(Formatting.GRAY),
                    (lx1 + lx2) / 2, listTop + viewH / 2 - 4, 0xFFAAAAAA);
        }

        ctx.enableScissor(lx1, listTop, lx2, listBottom);
        for (int i = 0; i < rows; i++) {
            int ry = listTop + i * ROW_H - (int) scroll;
            if (ry + ROW_H < listTop || ry > listBottom) continue;

            ctx.fill(lx1 + 1, ry + 1, lx2 - 1, ry + ROW_H - 1, 0x33FFFFFF);

            if (tab == 0) {
                KillData.BlackRow row = black.get(i);
                KillData.Entry e = row.latest();
                PlayerSkinDrawer.draw(ctx, KillData.skinOf(row.name()), lx1 + 4, ry + 3, 24);
                ctx.drawTextWithShadow(textRenderer, Text.literal(row.name()).formatted(Formatting.RED, Formatting.BOLD),
                        lx1 + 34, ry + 3, 0xFFFFFFFF);
                drawInfo(ctx, lx1 + 34, ry, lx2 - 92,
                        "Zabil Cie " + row.count() + "x, " + ago(e.time()),
                        coords(e));

                boolean on = KillData.tracers.contains(KillData.key(row.name()));
                String name = row.name();
                drawButton(ctx, lx2 - 84, ry + 5, 78, 20, on ? "Tracers: WL" : "Tracers: WYL", on,
                        mouseX, mouseY, true, () -> KillData.toggleTracer(name));
            } else {
                KillData.Entry e = killRows.get(i);
                PlayerSkinDrawer.draw(ctx, KillData.skinOf(e.name()), lx1 + 4, ry + 3, 24);
                ctx.drawTextWithShadow(textRenderer, Text.literal(e.name()).formatted(Formatting.GREEN, Formatting.BOLD),
                        lx1 + 34, ry + 3, 0xFFFFFFFF);
                drawInfo(ctx, lx1 + 34, ry, lx2 - 8,
                        "Zabity przez Ciebie, " + ago(e.time()),
                        coords(e));
            }
        }
        ctx.disableScissor();

        if (maxScroll > 0) {
            int barH = Math.max(12, (int) (viewH * (viewH / (double) total)));
            int barY = listTop + (int) ((viewH - barH) * (scroll / maxScroll));
            ctx.fill(lx2 - 3, barY, lx2 - 1, barY + barH, 0xAAFFFFFF);
        }

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Fragi (1h): " + KillData.kills.size() + "     Zgony (1h): " + KillData.deaths.size())
                        .formatted(Formatting.YELLOW, Formatting.BOLD),
                left + pw / 2, top + ph - 28, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Poza zasiegiem tracer pokazuje ostatnia znana pozycje").formatted(Formatting.DARK_GRAY),
                left + pw / 2, top + ph - 14, 0xFF777777);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawInfo(DrawContext ctx, int x, int rowY, int maxX, String line1, String line2) {
        int w = Math.max(20, maxX - x);
        ctx.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(line1, w)).formatted(Formatting.GRAY),
                x, rowY + 13, 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(line2, w)).formatted(Formatting.DARK_GRAY),
                x, rowY + 21, 0xFF777777);
    }

    private static String coords(KillData.Entry e) {
        String s = e.pos().getX() + " " + e.pos().getY() + " " + e.pos().getZ() + " (" + e.dim() + ")";
        if (e.weapon() != null && !e.weapon().isBlank()) s += " | " + e.weapon();
        return s;
    }

    private static String ago(long time) {
        long sec = Math.max(0, (System.currentTimeMillis() - time) / 1000L);
        if (sec < 60) return sec + "s temu";
        return (sec / 60) + " min temu";
    }

    private void drawButton(DrawContext ctx, int x, int y, int w, int h, String label, boolean active,
                            int mouseX, int mouseY, boolean inList, Runnable action) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
                && (!inList || (mouseY >= listTop && mouseY < listBottom));
        int bg = active ? 0xFF7A1010 : 0xFF333333;
        if (hover) bg = active ? 0xFFA01818 : 0xFF555555;
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.drawBorder(x, y, w, h, 0xFF000000);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        hits.add(new Hit(x, y, x + w, y + h, inList, action));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit hit : hits) {
                boolean inside = mouseX >= hit.x1() && mouseX < hit.x2() && mouseY >= hit.y1() && mouseY < hit.y2();
                if (!inside) continue;
                if (hit.inList() && (mouseY < listTop || mouseY >= listBottom)) continue;
                hit.action().run();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= verticalAmount * 18;
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
