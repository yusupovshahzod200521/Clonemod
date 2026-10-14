package com.clonemod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

@Mod("clonemod")
public class CloneMod {

    public static final String TAG = "clonemod";
    public static SimpleChannel CHANNEL;

    public CloneMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(FMLCommonSetupEvent e) {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation("clonemod", "main"),
                () -> "1", "1"::equals, "1"::equals);
        CHANNEL.registerMessage(0, SwitchPacket.class,
                SwitchPacket::encode, SwitchPacket::decode, SwitchPacket::handle);
    }

    private void clientSetup(FMLClientSetupEvent e) {
        ClientHandler h = new ClientHandler();
        for (KeyBinding k : ClientHandler.keys) {
            ClientRegistry.registerKeyBinding(k);
        }
        MinecraftForge.EVENT_BUS.register(h);
    }

    // ---------- Klaviatura (faqat klient) ----------
    public static class ClientHandler {
        public static final KeyBinding[] keys = new KeyBinding[] {
            new KeyBinding("Tana 1", InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_F6, "Clone Mod"),
            new KeyBinding("Tana 2", InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_F7, "Clone Mod"),
            new KeyBinding("Tana 3", InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_F8, "Clone Mod")
        };

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            if (Minecraft.getInstance().player == null) return;
            for (int i = 0; i < 3; i++) {
                while (keys[i].consumeClick()) {
                    CHANNEL.sendToServer(new SwitchPacket(i));
                }
            }
        }
    }

    // ---------- Paket ----------
    public static class SwitchPacket {
        public int slot;

        public SwitchPacket(int slot) {
            this.slot = slot;
        }

        public static void encode(SwitchPacket p, PacketBuffer b) {
            b.writeInt(p.slot);
        }

        public static SwitchPacket decode(PacketBuffer b) {
            return new SwitchPacket(b.readInt());
        }

        public static void handle(SwitchPacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayerEntity player = ctx.get().getSender();
                if (player != null && p.slot >= 0 && p.slot < 3) {
                    switchBody(player, p.slot);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // ---------- Ma'lumotlar ----------
    static CompoundNBT data(ServerPlayerEntity p) {
        CompoundNBT root = p.getPersistentData();
        if (!root.contains(PlayerEntity.PERSISTED_NBT_TAG, 10)) {
            root.put(PlayerEntity.PERSISTED_NBT_TAG, new CompoundNBT());
        }
        CompoundNBT per = root.getCompound(PlayerEntity.PERSISTED_NBT_TAG);
        if (!per.contains(TAG, 10)) {
            per.put(TAG, new CompoundNBT());
        }
        return per.getCompound(TAG);
    }

    static void saveCurrent(ServerPlayerEntity p, CompoundNBT d) {
        int cur = d.getInt("cur");
        CompoundNBT b = new CompoundNBT();
        b.putBoolean("exists", true);
        b.putBoolean("alive", true);
        b.put("inv", p.inventory.save(new ListNBT()));
        b.putFloat("hp", p.getHealth());
        b.putDouble("x", p.getX());
        b.putDouble("y", p.getY());
        b.putDouble("z", p.getZ());
        b.putString("dim", p.level.dimension().location().toString());
        d.put("b" + cur, b);
    }

    static void loadBody(ServerPlayerEntity p, CompoundNBT b) {
        p.inventory.clearContent();
        p.inventory.load(b.getList("inv", 10));
        p.removeAllEffects();
        p.clearFire();
        p.fallDistance = 0;
        p.setHealth(Math.max(b.getFloat("hp"), 1.0F));
        ServerWorld w = p.server.getLevel(RegistryKey.create(
                Registry.DIMENSION_REGISTRY, new ResourceLocation(b.getString("dim"))));
        if (w == null) w = p.getLevel();
        p.teleportTo(w, b.getDouble("x"), b.getDouble("y"), b.getDouble("z"), p.yRot, p.xRot);
    }

    static void switchBody(ServerPlayerEntity p, int slot) {
        CompoundNBT d = data(p);
        if (!d.getBoolean("init")) {
            d.putBoolean("init", true);
            d.putInt("cur", 0);
            saveCurrent(p, d);
        }
        int cur = d.getInt("cur");
        if (slot == cur) return;

        CompoundNBT target = d.getCompound("b" + slot);
        if (target.getBoolean("exists") && !target.getBoolean("alive")) {
            p.displayClientMessage(new StringTextComponent("Bu tana o'lgan!"), true);
            return;
        }

        saveCurrent(p, d);

        if (!target.getBoolean("exists")) {
            target = new CompoundNBT();
            target.putBoolean("exists", true);
            target.putBoolean("alive", true);
            target.put("inv", new ListNBT());
            target.putFloat("hp", 20.0F);
            target.putDouble("x", p.getX());
            target.putDouble("y", p.getY());
            target.putDouble("z", p.getZ());
            target.putString("dim", p.level.dimension().location().toString());
            d.put("b" + slot, target);
        }

        loadBody(p, target);
        d.putInt("cur", slot);
        p.displayClientMessage(new StringTextComponent("Tana " + (slot + 1)), true);
    }

    // ---------- O'lim ----------
    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity p = (ServerPlayerEntity) e.getEntity();
        CompoundNBT d = data(p);
        if (!d.getBoolean("init")) return;

        int cur = d.getInt("cur");
        int next = -1;
        for (int i = 0; i < 3; i++) {
            CompoundNBT b = d.getCompound("b" + i);
            if (i != cur && b.getBoolean("exists") && b.getBoolean("alive")) {
                next = i;
                break;
            }
        }

        if (next < 0) {
            p.getPersistentData().getCompound(PlayerEntity.PERSISTED_NBT_TAG).remove(TAG);
            return;
        }

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < p.inventory.getContainerSize(); i++) {
            ItemStack s = p.inventory.getItem(i);
            if (!s.isEmpty()) items.add(s.copy());
        }

        d.getCompound("b" + cur).putBoolean("alive", false);
        e.setCanceled(true);

        loadBody(p, d.getCompound("b" + next));
        for (ItemStack s : items) {
            if (!p.inventory.add(s)) {
                p.drop(s, false);
            }
        }
        d.putInt("cur", next);
        p.displayClientMessage(new StringTextComponent("Tana o'ldi! Tana " + (next + 1) + " ga o'tdingiz"), true);
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone e) {
        if (e.isWasDeath()) return;
        CompoundNBT o = e.getOriginal().getPersistentData();
        if (o.contains(PlayerEntity.PERSISTED_NBT_TAG, 10)) {
            e.getPlayer().getPersistentData().put(PlayerEntity.PERSISTED_NBT_TAG,
                    o.getCompound(PlayerEntity.PERSISTED_NBT_TAG).copy());
        }
    }
}
