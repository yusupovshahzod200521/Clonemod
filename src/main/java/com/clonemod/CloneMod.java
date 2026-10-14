package com.clonemod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.GlobalEntityTypeAttributes;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod("clonemod")
public class CloneMod {

    public static final String TAG = "clonemod";
    public static SimpleChannel CHANNEL;

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, "clonemod");
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITIES, "clonemod");

    public static final RegistryObject<Item> WAND = ITEMS.register("clone_wand",
            () -> new CloneWandItem(new Item.Properties().tab(ItemGroup.TAB_TOOLS).stacksTo(1)));

    public static final RegistryObject<EntityType<CloneBodyEntity>> BODY = ENTITIES.register("clone_body",
            () -> EntityType.Builder.<CloneBodyEntity>of(CloneBodyEntity::new, EntityClassification.MISC)
                    .sized(0.6F, 1.8F)
                    .build("clone_body"));

    public CloneMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(this::setup);
        bus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(FMLCommonSetupEvent e) {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation("clonemod", "main"),
                () -> "1", "1"::equals, "1"::equals);
        CHANNEL.registerMessage(0, SwitchPacket.class,
                SwitchPacket::encode, SwitchPacket::decode, SwitchPacket::handle);
        e.enqueueWork(() -> GlobalEntityTypeAttributes.put(BODY.get(),
                CloneBodyEntity.createAttributes().build()));
    }

    private void clientSetup(FMLClientSetupEvent e) {
        for (KeyBinding k : ClientHandler.keys) {
            ClientRegistry.registerKeyBinding(k);
        }
        MinecraftForge.EVENT_BUS.register(new ClientHandler());
        RenderingRegistry.registerEntityRenderingHandler(BODY.get(), CloneBodyRenderer::new);
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
    // s0,s1,s2: 0 = yo'q, 1 = tirik, 2 = o'lgan.  b0,b1,b2: tana ma'lumoti.
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

    static void init(ServerPlayerEntity p, CompoundNBT d) {
        if (!d.getBoolean("init")) {
            d.putBoolean("init", true);
            d.putInt("cur", 0);
            d.putInt("s0", 1);
        }
    }

    static CompoundNBT snapshot(ServerPlayerEntity p) {
        CompoundNBT b = new CompoundNBT();
        b.put("inv", p.inventory.save(new ListNBT()));
        b.putFloat("hp", p.getHealth());
        b.putDouble("x", p.getX());
        b.putDouble("y", p.getY());
        b.putDouble("z", p.getZ());
        b.putString("dim", p.level.dimension().location().toString());
        return b;
    }

    static void spawnBodyEntity(ServerWorld w, UUID owner, int idx, double x, double y, double z, float yaw) {
        CloneBodyEntity c = new CloneBodyEntity(BODY.get(), w);
        c.moveTo(x, y, z, yaw, 0.0F);
        c.yHeadRot = yaw;
        c.setOwnerId(owner);
        c.setBodyIndex(idx);
        w.addFreshEntity(c);
    }
        static void spawnBodyEntity(ServerWorld w, UUID owner, int idx, double x, double y, double z, float yaw, ServerPlayerEntity src) {
        CloneBodyEntity c = new CloneBodyEntity(BODY.get(), w);
        c.moveTo(x, y, z, yaw, 0.0F);
        c.yHeadRot = yaw;
        c.setOwnerId(owner);
        c.setBodyIndex(idx);
        for (net.minecraft.inventory.EquipmentSlotType slot : net.minecraft.inventory.EquipmentSlotType.values()) {
            c.setItemSlot(slot, src.getItemBySlot(slot).copy());
            c.setDropChance(slot, 0.0F);
        }
        w.addFreshEntity(c);
    }

    static float takeBodyEntity(ServerWorld w, double x, double y, double z, UUID owner, int idx) {
        float hp = -1.0F;
        AxisAlignedBB box = new AxisAlignedBB(x - 6, y - 20, z - 6, x + 6, y + 20, z + 6);
        for (CloneBodyEntity c : w.getEntitiesOfClass(CloneBodyEntity.class, box)) {
            if (owner.equals(c.getOwnerId()) && c.getBodyIndex() == idx) {
                hp = c.getHealth();
                c.remove();
            }
        }
        return hp;
    }

    static void enterBody(ServerPlayerEntity p, CompoundNBT b, int idx) {
        ServerWorld w = p.server.getLevel(RegistryKey.create(
                Registry.DIMENSION_REGISTRY, new ResourceLocation(b.getString("dim"))));
        if (w == null) w = p.getLevel();
        double x = b.getDouble("x");
        double y = b.getDouble("y");
        double z = b.getDouble("z");
        p.teleportTo(w, x, y, z, p.yRot, p.xRot);
        float entHp = takeBodyEntity(w, x, y, z, p.getUUID(), idx);
        p.inventory.clearContent();
        p.inventory.load(b.getList("inv", 10));
        p.removeAllEffects();
        p.clearFire();
        p.fallDistance = 0;
        float hp = entHp > 0 ? entHp : b.getFloat("hp");
        p.setHealth(Math.max(hp, 1.0F));
    }

    static void giveItems(ServerPlayerEntity p, List<ItemStack> items) {
        for (ItemStack s : items) {
            if (!p.inventory.add(s)) {
                p.drop(s, false);
            }
        }
    }

    // ---------- Yangi tana yaratish (tayoq) ----------
    public static boolean createBody(ServerPlayerEntity p) {
        CompoundNBT d = data(p);
        init(p, d);
        int cur = d.getInt("cur");
        int free = -1;
        for (int i = 0; i < 3; i++) {
            if (i != cur && d.getInt("s" + i) != 1) {
                free = i;
                break;
            }
        }
        if (free < 0) return false;

        Vector3d look = p.getLookAngle();
        double x = p.getX() + look.x * 1.5;
        double z = p.getZ() + look.z * 1.5;
        double y = p.getY();

        CompoundNBT b = new CompoundNBT();
        b.put("inv", new ListNBT());
        b.putFloat("hp", 20.0F);
        b.putDouble("x", x);
        b.putDouble("y", y);
        b.putDouble("z", z);
        b.putString("dim", p.level.dimension().location().toString());
        d.put("b" + free, b);
        d.putInt("s" + free, 1);

        spawnBodyEntity(p.getLevel(), p.getUUID(), free, x, y, z, p.yRot);
        p.displayClientMessage(new StringTextComponent("Yangi tana yaratildi (Tana " + (free + 1) + ")"), true);
        return true;
    }

    // ---------- Tanalar orasida o'tish ----------
    static void switchBody(ServerPlayerEntity p, int slot) {
        CompoundNBT d = data(p);
        init(p, d);
        int cur = d.getInt("cur");
        if (slot == cur) return;

        int state = d.getInt("s" + slot);
        if (state == 0) {
            p.displayClientMessage(new StringTextComponent("Bu tana hali yaratilmagan"), true);
            return;
        }
        if (state == 2) {
            p.displayClientMessage(new StringTextComponent("Bu tana o'lgan!"), true);
            return;
        }

        // Hozirgi tanani saqlab, dunyoda AFK qoldiramiz
        CompoundNBT snap = snapshot(p);
        d.put("b" + cur, snap);
        d.putInt("s" + cur, 1);
        spawnBodyEntity(p.getLevel(), p.getUUID(), cur, p.getX(), p.getY(), p.getZ(), p.yRot, p);

        // Tanlangan tanaga o'tamiz
        enterBody(p, d.getCompound("b" + slot), slot);
        d.putInt("cur", slot);
        p.displayClientMessage(new StringTextComponent("Tana " + (slot + 1)), true);
    }

    // ---------- O'lim ----------
    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        // AFK tana o'ldi
        if (e.getEntity() instanceof CloneBodyEntity) {
            CloneBodyEntity c = (CloneBodyEntity) e.getEntity();
            if (c.level.isClientSide) return;
            UUID id = c.getOwnerId();
            if (id == null) return;
            ServerPlayerEntity owner = ((ServerWorld) c.level).getServer().getPlayerList().getPlayer(id);
            if (owner == null) return;
            CompoundNBT d = data(owner);
            int idx = c.getBodyIndex();
            if (d.getInt("s" + idx) != 1) return;
            d.putInt("s" + idx, 2);
            ListNBT inv = d.getCompound("b" + idx).getList("inv", 10);
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = ItemStack.of(inv.getCompound(i));
                if (!s.isEmpty()) items.add(s);
            }
            giveItems(owner, items);
            owner.displayClientMessage(new StringTextComponent("Tana " + (idx + 1) + " o'ldi!"), true);
            return;
        }

        // O'yinchi turgan tana o'ldi
        if (!(e.getEntity() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity p = (ServerPlayerEntity) e.getEntity();
        CompoundNBT d = data(p);
        if (!d.getBoolean("init")) return;

        int cur = d.getInt("cur");
        int next = -1;
        for (int i = 0; i < 3; i++) {
            if (i != cur && d.getInt("s" + i) == 1) {
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

        d.putInt("s" + cur, 2);
        e.setCanceled(true);

        enterBody(p, d.getCompound("b" + next), next);
        giveItems(p, items);
        d.putInt("cur", next);
        p.displayClientMessage(new StringTextComponent("Tana o'ldi! Tana " + (next + 1) + " ga o'tdingiz"), true);
    }

    // ---------- Moblar tanalarga hujum qilsin ----------
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent e) {
        if (e.getWorld().isClientSide) return;
        if (e.getEntity() instanceof MonsterEntity) {
            MonsterEntity m = (MonsterEntity) e.getEntity();
            m.targetSelector.addGoal(3, new NearestAttackableTargetGoal<CloneBodyEntity>(m, CloneBodyEntity.class, true));
        }
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone e) {
        CompoundNBT o = e.getOriginal().getPersistentData();
        if (e.isWasDeath()) {
            CompoundNBT np = e.getPlayer().getPersistentData();
            if (np.contains(PlayerEntity.PERSISTED_NBT_TAG, 10)) {
                np.getCompound(PlayerEntity.PERSISTED_NBT_TAG).remove(TAG);
            }
            return;
        }
        if (o.contains(PlayerEntity.PERSISTED_NBT_TAG, 10)) {
            e.getPlayer().getPersistentData().put(PlayerEntity.PERSISTED_NBT_TAG,
                    o.getCompound(PlayerEntity.PERSISTED_NBT_TAG).copy());
        }
    }
}
