package com.clonemod;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;

public class CloneWandItem extends Item {

    public CloneWandItem(Item.Properties props) {
        super(props);
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide) {
            return ActionResult.success(stack);
        }
        ServerPlayerEntity sp = (ServerPlayerEntity) player;
        boolean ok = CloneMod.createBody(sp);
        if (ok) {
            if (!player.abilities.instabuild) {
                stack.shrink(1);
            }
            return ActionResult.consume(stack);
        } else {
            sp.displayClientMessage(new StringTextComponent("Tanalar to'la!"), true);
            return ActionResult.fail(stack);
        }
    }
}
