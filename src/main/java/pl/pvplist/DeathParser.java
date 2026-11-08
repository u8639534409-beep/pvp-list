package pl.pvplist;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Rozpoznaje wiadomosci o smierci na czacie i zapisuje kto kogo zabil. */
public final class DeathParser {
    private DeathParser() {
    }

    private static final String[] DEATH_WORDS = {
            "zabit", "zabi", "zgin", "zastrzel", "slain", "killed", "shot by", "murder", "pokonan", "defeated"
    };
    private static final Pattern ACTIVE = Pattern.compile("(?i)\\b(zabi[lł]a?|zabija|killed|slew|murdered|pokona[lł]a?)\\b");

    public static void handle(Text message) {
        MinecraftClient c = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = c.getNetworkHandler();
        if (c.player == null || c.world == null || handler == null) return;

        String me = c.player.getName().getString();
        String victim;
        String killer;
        String weapon = null;

        TranslatableTextContent t = findDeath(message);
        if (t != null) {
            Object[] args = t.getArgs();
            victim = resolve(handler, argString(args, 0));
            killer = resolve(handler, argString(args, 1));
            if (args.length > 2) weapon = argString(args, 2);
        } else {
            String[] pair = heuristic(handler, message.getString(), me);
            if (pair == null) return;
            victim = pair[0];
            killer = pair[1];
        }

        if (victim == null || killer == null) return;
        boolean meVictim = victim.equalsIgnoreCase(me);
        boolean meKiller = killer.equalsIgnoreCase(me);
        if (meVictim == meKiller) return; // cos niezwiazanego z Toba albo samobojstwo

        long now = System.currentTimeMillis();
        String dim = c.world.getRegistryKey().getValue().getPath();

        if (meVictim) {
            BlockPos pos = c.player.getBlockPos();
            if (!KillData.addDeath(killer, pos, dim, weapon)) return;
            rememberPosition(c, killer, dim, now);
            c.player.sendMessage(Text.literal("[PvpList] " + killer + " Cie zabil - dodany na blackliste.")
                    .formatted(Formatting.RED), false);
        } else {
            BlockPos pos = c.player.getBlockPos();
            AbstractClientPlayerEntity v = findPlayer(c, victim);
            if (v != null) pos = v.getBlockPos();
            if (!KillData.addKill(victim, pos, dim, weapon)) return;
            c.player.sendMessage(Text.literal("[PvpList] Frag: " + victim).formatted(Formatting.GREEN), true);
        }
    }

    private static void rememberPosition(MinecraftClient c, String name, String dim, long now) {
        AbstractClientPlayerEntity p = findPlayer(c, name);
        if (p != null) {
            KillData.lastSeen.put(KillData.key(name),
                    new KillData.Seen(p.getPos().add(0, p.getHeight() / 2, 0), dim, now));
        }
    }

    public static AbstractClientPlayerEntity findPlayer(MinecraftClient c, String name) {
        if (c.world == null) return null;
        for (AbstractClientPlayerEntity p : c.world.getPlayers()) {
            if (p == c.player) continue;
            if (p.getName().getString().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private static TranslatableTextContent findDeath(Text text) {
        if (text.getContent() instanceof TranslatableTextContent t && t.getKey().startsWith("death.")) {
            return t;
        }
        for (Text sibling : text.getSiblings()) {
            TranslatableTextContent r = findDeath(sibling);
            if (r != null) return r;
        }
        return null;
    }

    private static String argString(Object[] args, int i) {
        if (i >= args.length || args[i] == null) return null;
        Object a = args[i];
        if (a instanceof Text text) return text.getString();
        return String.valueOf(a);
    }

    /** Zamienia np. "[VIP] Nick" na prawdziwy nick z listy graczy. */
    private static String resolve(ClientPlayNetworkHandler handler, String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        PlayerListEntry exact = handler.getPlayerListEntry(raw);
        if (exact != null) return exact.getProfile().getName();
        for (PlayerListEntry e : handler.getPlayerList()) {
            String n = e.getProfile().getName();
            if (n.length() >= 3 && indexOfWord(raw, n) >= 0) return n;
        }
        return null;
    }

    /** Rezerwowe rozpoznawanie zwyklego tekstu: szuka dwoch nickow z tab-listy. */
    private static String[] heuristic(ClientPlayNetworkHandler handler, String s, String me) {
        String lower = s.toLowerCase(Locale.ROOT);
        boolean looksDeath = false;
        for (String w : DEATH_WORDS) {
            if (lower.contains(w)) {
                looksDeath = true;
                break;
            }
        }
        if (!looksDeath || indexOfWord(s, me) < 0) return null;

        List<int[]> found = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (PlayerListEntry e : handler.getPlayerList()) {
            String n = e.getProfile().getName();
            if (n.length() < 3) continue;
            int idx = indexOfWord(s, n);
            if (idx >= 0) {
                found.add(new int[]{idx, names.size()});
                names.add(n);
            }
        }
        if (found.size() < 2) return null;
        found.sort((a, b) -> Integer.compare(a[0], b[0]));

        String first = names.get(found.get(0)[1]);
        String second = names.get(found.get(1)[1]);
        boolean passive = lower.contains(" by ") || lower.contains(" przez ");
        if (ACTIVE.matcher(s).find() && !passive) {
            return new String[]{second, first}; // "Y zabil X"
        }
        return new String[]{first, second};     // "X zostal zabity przez Y"
    }

    private static int indexOfWord(String s, String word) {
        int from = 0;
        while (true) {
            int i = s.indexOf(word, from);
            if (i < 0) return -1;
            boolean okLeft = i == 0 || !isNameChar(s.charAt(i - 1));
            int end = i + word.length();
            boolean okRight = end >= s.length() || !isNameChar(s.charAt(end));
            if (okLeft && okRight) return i;
            from = i + 1;
        }
    }

    private static boolean isNameChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }
}
