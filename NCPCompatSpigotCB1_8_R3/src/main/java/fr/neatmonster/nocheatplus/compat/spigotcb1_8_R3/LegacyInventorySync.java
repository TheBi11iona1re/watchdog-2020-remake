package fr.neatmonster.nocheatplus.compat.spigotcb1_8_R3;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.PacketPlayInWindowClick;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Keep translated container clicks ordered without a legacy acknowledgement stall. */
public final class LegacyInventorySync {
    private static final java.lang.reflect.Field PREDICTED_ITEM;

    static {
        try {
            PREDICTED_ITEM = PacketPlayInWindowClick.class.getDeclaredField("item");
            PREDICTED_ITEM.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private LegacyInventorySync() {
    }

    public static void attach(final Player player) {
        final Plugin plugin = Bukkit.getPluginManager().getPlugin("NoCheatPlus");
        final EntityPlayer handle = ((CraftPlayer) player).getHandle();
        final Channel channel = handle.playerConnection.networkManager.channel;
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                if (channel.pipeline().get("ncp-container-sync") != null) {
                    return;
                }
                channel.pipeline().addBefore("packet_handler", "ncp-container-sync", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(final ChannelHandlerContext context, final Object message) throws Exception {
                        if (!(message instanceof PacketPlayInWindowClick) || !plugin.isEnabled()) {
                            context.fireChannelRead(message);
                            return;
                        }
                        final PacketPlayInWindowClick click = (PacketPlayInWindowClick) message;
                        // Use the same main-thread queue as other play packets, including close-window.
                        handle.u().postToMainThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!player.isOnline()) {
                                    return;
                                }
                                final EntityPlayer current = ((CraftPlayer) player).getHandle();
                                if (current.dead || current.activeContainer.windowId != click.a()) {
                                    return;
                                }
                                // Vanilla already applied the preceding click before comparing its
                                // prediction. Do not lose the next translated click while awaiting C0F.
                                current.activeContainer.a(current, true);
                                if (click.f() == 1 && click.b() >= 0 && click.b() < current.activeContainer.c.size()) {
                                    final ItemStack stack = current.activeContainer.getSlot(click.b()).getItem();
                                    try {
                                        // Newer quick-move prediction does not carry the old return-stack contract.
                                        PREDICTED_ITEM.set(click, stack == null ? null : stack.cloneItemStack());
                                    } catch (IllegalAccessException exception) {
                                        throw new IllegalStateException("Cannot reconcile shift-click prediction", exception);
                                    }
                                }
                                current.playerConnection.a(click);
                            }
                        });
                    }
                });
            }
        });
    }
}
