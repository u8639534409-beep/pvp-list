package pl.pvplist;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class PvpListClient implements ClientModInitializer {

    private static KeyBinding openKey;
    private static int timer = 0;

    @Override
    public void onInitializeClient() {
        FragTracker.init();

        // Domyslny klawisz: J (zmienisz w Opcje -> Sterowanie).
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pvplist.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.pvplist"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    client.setScreen(new PvpListScreen());
                }
            }
            if (client.player == null || client.world == null) return;

            Tracers.updateLastSeen(client);
            FragTracker.tick(client);
            if (++timer >= 20) {
                timer = 0;
                KillData.purge();
            }
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                DeathParser.handle(message);
            }
        });

        // /pvplist add <nick>  oraz  /pvplist remove <nick>
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("pvplist")
                        .then(literal("add").then(argument("nick", StringArgumentType.word()).executes(ctx -> {
                            String nick = StringArgumentType.getString(ctx, "nick");
                            boolean ok = KillData.addManual(nick);
                            ctx.getSource().sendFeedback(Text.literal(ok
                                    ? "[PvpList] Dodano na blackliste: " + nick
                                    : "[PvpList] Nie mozna dodac: " + nick)
                                    .formatted(ok ? Formatting.GREEN : Formatting.RED));
                            return ok ? 1 : 0;
                        })))
                        .then(literal("frag").then(argument("nick", StringArgumentType.word()).executes(ctx -> {
                            String nick = StringArgumentType.getString(ctx, "nick");
                            var c = net.minecraft.client.MinecraftClient.getInstance();
                            boolean ok = c.player != null && KillData.addKill(nick, c.player.getBlockPos(),
                                    c.world.getRegistryKey().getValue().getPath(), null);
                            ctx.getSource().sendFeedback(Text.literal(ok
                                    ? "[PvpList] Dodano frag: " + nick
                                    : "[PvpList] Nie dodano (duplikat?): " + nick)
                                    .formatted(ok ? Formatting.GREEN : Formatting.RED));
                            return ok ? 1 : 0;
                        })))
                        .then(literal("remove").then(argument("nick", StringArgumentType.word()).executes(ctx -> {
                            String nick = StringArgumentType.getString(ctx, "nick");
                            KillData.remove(nick);
                            ctx.getSource().sendFeedback(Text.literal("[PvpList] Usunieto: " + nick)
                                    .formatted(Formatting.YELLOW));
                            return 1;
                        })))));

        HudRenderCallback.EVENT.register((context, tickCounter) -> Tracers.render(context));
    }
}
