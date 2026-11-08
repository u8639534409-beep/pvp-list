package pl.pvplist;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Dane logu: kto Cie zabil (blacklista), kogo Ty zabiles, tracery. Wpisy znikaja po godzinie. */
public final class KillData {
    private KillData() {
    }

    public static final long EXPIRE_MS = 60L * 60L * 1000L;

    public record Entry(String name, long time, BlockPos pos, String dim, String weapon) {
    }

    public record Seen(Vec3d pos, String dim, long time) {
    }

    public record BlackRow(String name, int count, Entry latest) {
    }

    /** Zabojstwa Ciebie (kto Cie zabil). */
    public static final List<Entry> deaths = new ArrayList<>();
    /** Twoje fragi. */
    public static final List<Entry> kills = new ArrayList<>();
    /** Nicki (male litery) z wlaczonymi tracerami. */
    public static final Set<String> tracers = new HashSet<>();
    /** Ostatnia znana pozycja sledzonych graczy. */
    public static final Map<String, Seen> lastSeen = new HashMap<>();

    private static final Map<String, SkinTextures> skins = new HashMap<>();

    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static void purge() {
        long now = System.currentTimeMillis();
        deaths.removeIf(e -> now - e.time() > EXPIRE_MS);
        kills.removeIf(e -> now - e.time() > EXPIRE_MS);

        Set<String> alive = new HashSet<>();
        for (Entry e : deaths) alive.add(key(e.name()));
        tracers.retainAll(alive);
        lastSeen.keySet().retainAll(alive);
    }

    /** Blacklista: jeden wiersz na gracza, najnowsze na gorze. */
    public static List<BlackRow> blacklist() {
        Map<String, List<Entry>> groups = new LinkedHashMap<>();
        for (Entry e : deaths) {
            groups.computeIfAbsent(key(e.name()), k -> new ArrayList<>()).add(e);
        }
        List<BlackRow> rows = new ArrayList<>();
        for (List<Entry> list : groups.values()) {
            Entry latest = list.get(0);
            for (Entry e : list) {
                if (e.time() > latest.time()) latest = e;
            }
            rows.add(new BlackRow(latest.name(), list.size(), latest));
        }
        rows.sort(Comparator.comparingLong((BlackRow r) -> r.latest().time()).reversed());
        return rows;
    }

    public static List<Entry> killsNewestFirst() {
        List<Entry> copy = new ArrayList<>(kills);
        copy.sort(Comparator.comparingLong(Entry::time).reversed());
        return copy;
    }

    /** Skin (glowa) gracza; pamieta ostatnio widziany, nawet gdy gracz wyszedl. */
    public static SkinTextures skinOf(String name) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.getNetworkHandler() != null) {
            PlayerListEntry entry = c.getNetworkHandler().getPlayerListEntry(name);
            if (entry != null) {
                skins.put(key(name), entry.getSkinTextures());
            }
        }
        SkinTextures cached = skins.get(key(name));
        if (cached != null) return cached;
        return DefaultSkinHelper.getSkinTextures(Uuids.getOfflinePlayerUuid(name));
    }

    public static void toggleTracer(String name) {
        String k = key(name);
        if (!tracers.remove(k)) {
            tracers.add(k);
        }
    }
}
