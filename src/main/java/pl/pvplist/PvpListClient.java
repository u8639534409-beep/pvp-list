package pl.pvplist;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class PvpListClient implements ClientModInitializer {

    private static KeyBinding openKey;
    private static int timer = 0;

    @Override
    public void onInitializeClient() {
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

        HudRenderCallback.EVENT.register((context, tickCounter) -> Tracers.render(context));
    }
}
