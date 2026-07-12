package com.finchy.pipeorgans.content.swell;

import com.finchy.pipeorgans.init.AllMenuTypes;
import com.simibubi.create.foundation.gui.menu.MenuBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SwellControlMenu extends MenuBase<SwellControlBlockEntity> {

    public static final int MARGIN       = 8;
    public static final int TITLE_Y      = 6;
    public static final int CAP_LABEL_Y  = 18;
    public static final int CAP_ROW_Y    = 28;
    public static final int PLAYER_INV_W = 162;
    public static final int PLAYER_INV_H = 76;
    public static final int INV_GAP      = 10;

    public SwellControlMenu(MenuType<?> type, int id, Inventory inv, FriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    public SwellControlMenu(MenuType<?> type, int id, Inventory inv, SwellControlBlockEntity be) {
        super(type, id, inv, be);
    }

    public static SwellControlMenu create(int id, Inventory inv, SwellControlBlockEntity be) {
        return new SwellControlMenu(AllMenuTypes.SWELL_CONTROL_MENU.get(), id, inv, be);
    }

    public SwellControlBlockEntity getControlBE() { return contentHolder; }

    public static int contentBottom() { return CAP_ROW_Y + 18; }
    public static int playerInvY()    { return contentBottom() + INV_GAP; }
    public static int guiWidth()      { return PLAYER_INV_W + 2 * MARGIN; }
    public static int guiHeight()     { return playerInvY() + PLAYER_INV_H + MARGIN; }
    public static int playerInvX()    { return (guiWidth() - PLAYER_INV_W) / 2; }

    @Override
    protected SwellControlBlockEntity createOnClient(FriendlyByteBuf extraData) {
        ClientLevel world = Minecraft.getInstance().level;
        BlockPos pos = extraData.readBlockPos();
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof SwellControlBlockEntity control) {
            control.readClient(extraData.readNbt());
            return control;
        }
        return null;
    }

    @Override
    protected void initAndReadInventory(SwellControlBlockEntity contentHolder) {}

    @Override
    protected void addSlots() {
        addPlayerSlots(playerInvX(), playerInvY());
    }

    @Override
    protected void saveData(SwellControlBlockEntity contentHolder) {}

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, net.minecraft.world.inventory.Slot slot) {
        return slot.container == playerInventory;
    }

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
        return ItemStack.EMPTY;
    }
}
