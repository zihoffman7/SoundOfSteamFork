package com.finchy.pipeorgans.content.console;

import com.finchy.pipeorgans.init.AllMenuTypes;
import com.simibubi.create.foundation.gui.menu.MenuBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Menu for editing a single pedal.
 *
 * Ghost inventory layout:
 *   ENCLOSED:  slot 0 = freq A, slot 1 = freq B
 *   CRESCENDO: slot (level*2) = freq A for that level, slot (level*2+1) = freq B
 *              slots are resized dynamically when levels are added/removed
 */
public class PedalEditMenu extends MenuBase<OrganConsoleBlockEntity> {

    public static final int MARGIN = 8;
    public static final int TITLE_Y = 6;
    public static final int NAME_LABEL_Y = 18;
    public static final int NAME_BOX_Y = 28;
    public static final int CONTENT_TOP = 50;   // top of filter area
    public static final int ROW_H = 22;         // height per crescendo level row
    public static final int SLOT_COL_A = 8;     // x for freq A slot bg
    public static final int SLOT_COL_B = 30;    // x for freq B slot bg
    public static final int FIELD_X = 8;
    public static final int FIELD_W = 160;
    public static final int BOX_H = 14;
    public static final int PLAYER_INV_W = 162;
    public static final int PLAYER_INV_H = 76;
    public static final int INV_GAP = 10;

    private static final int PLAYER_SLOTS = 36;

    // Set on the BE before opening
    public int menuPedalIndex;

    public ItemStackHandler ghostInventory;
    public PedalData.PedalType pedalType;
    public int levelCount; // snapshot at open; updated when levels are added/removed

    public PedalEditMenu(MenuType<?> type, int id, Inventory inv, FriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    public PedalEditMenu(MenuType<?> type, int id, Inventory inv, OrganConsoleBlockEntity be) {
        super(type, id, inv, be);
    }

    public static PedalEditMenu create(int id, Inventory inv, OrganConsoleBlockEntity be, int pedalIndex) {
        be.menuPedalIndex = pedalIndex;
        return new PedalEditMenu(AllMenuTypes.PEDAL_EDIT_MENU.get(), id, inv, be);
    }

    public OrganConsoleBlockEntity getConsoleBE() {
        return contentHolder;
    }

    public int getPedalIndex() {
        return menuPedalIndex;
    }

    // --- Layout helpers ---

    public static int filterRowY(int level) {
        return CONTENT_TOP + level * ROW_H;
    }

    /** Total height of filter content area. */
    public int filterAreaHeight() {
        if (pedalType == PedalData.PedalType.ENCLOSED)
            return ROW_H; // exactly one row
        return levelCount * ROW_H + 24; // rows + add/remove buttons
    }

    public int buttonsY() {
        return CONTENT_TOP + filterAreaHeight();
    }

    public static int playerInvY(PedalData.PedalType type, int levelCount) {
        int filterH = type == PedalData.PedalType.ENCLOSED ? ROW_H : levelCount * ROW_H + 24;
        return CONTENT_TOP + filterH + INV_GAP;
    }

    public static int guiWidth() {
        return Math.max(FIELD_X + FIELD_W + MARGIN, PLAYER_INV_W + 2 * MARGIN);
    }

    public static int guiHeight(PedalData.PedalType type, int levelCount) {
        return playerInvY(type, levelCount) + PLAYER_INV_H + MARGIN;
    }

    public static int playerInvX() {
        return (guiWidth() - PLAYER_INV_W) / 2;
    }

    // --- Ghost slot helpers ---

    /** Total ghost slots needed. */
    private static int ghostSlotCount(PedalData.PedalType type, int levelCount) {
        return type == PedalData.PedalType.ENCLOSED ? 2 : Math.max(2, levelCount * 2);
    }

    private boolean isGhostSlot(int slotId) {
        return slotId >= PLAYER_SLOTS && slotId < PLAYER_SLOTS + ghostInventory.getSlots();
    }

    // --- MenuBase wiring ---

    @Override
    protected OrganConsoleBlockEntity createOnClient(FriendlyByteBuf extraData) {
        ClientLevel world = Minecraft.getInstance().level;
        BlockEntity blockEntity = world.getBlockEntity(extraData.readBlockPos());
        if (blockEntity instanceof OrganConsoleBlockEntity be) {
            be.readClient(extraData.readNbt());
            be.menuPedalIndex = extraData.readVarInt();
            return be;
        }
        return null;
    }

    @Override
    protected void initAndReadInventory(OrganConsoleBlockEntity contentHolder) {
        menuPedalIndex = contentHolder.menuPedalIndex;
        PedalData.Pedal pedal = contentHolder.getPedalData().getPedal(menuPedalIndex);
        pedalType = pedal.type;
        levelCount = pedalType == PedalData.PedalType.CRESCENDO ? pedal.crescendoLevelCount() : 0;

        int slots = ghostSlotCount(pedalType, levelCount);
        ghostInventory = new ItemStackHandler(slots);

        if (pedalType == PedalData.PedalType.ENCLOSED) {
            ghostInventory.setStackInSlot(0, pedal.getEnclosedFreqA().copy());
            ghostInventory.setStackInSlot(1, pedal.getEnclosedFreqB().copy());
        } else {
            for (int lvl = 0; lvl < levelCount; lvl++) {
                ghostInventory.setStackInSlot(lvl * 2,     pedal.getCrescendoFreqA(lvl).copy());
                ghostInventory.setStackInSlot(lvl * 2 + 1, pedal.getCrescendoFreqB(lvl).copy());
            }
        }
    }

    @Override
    protected void addSlots() {
        addPlayerSlots(playerInvX(), playerInvY(pedalType, levelCount));

        int rows = pedalType == PedalData.PedalType.ENCLOSED ? 1 : levelCount;
        for (int i = 0; i < rows; i++) {
            int y = filterRowY(i) + 1;
            addSlot(new SlotItemHandler(ghostInventory, i * 2,     SLOT_COL_A + 1, y));
            addSlot(new SlotItemHandler(ghostInventory, i * 2 + 1, SLOT_COL_B + 1, y));
        }
    }

    @Override
    protected void saveData(OrganConsoleBlockEntity contentHolder) {
        // Saving is explicit via the Save button → PedalConfigPacket
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container == playerInventory;
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (!isGhostSlot(slotId)) {
            super.clicked(slotId, dragType, clickType, player);
            return;
        }
        if (clickType == ClickType.THROW)
            return;

        int ghostIdx = slotId - PLAYER_SLOTS;
        ItemStack held = getCarried();

        if (clickType == ClickType.CLONE) {
            if (player.isCreative() && held.isEmpty()) {
                ItemStack copy = ghostInventory.getStackInSlot(ghostIdx).copy();
                if (!copy.isEmpty()) {
                    copy.setCount(copy.getMaxStackSize());
                    setCarried(copy);
                }
            }
            return;
        }

        ItemStack filter = held.isEmpty() ? ItemStack.EMPTY : held.copy();
        if (!filter.isEmpty()) filter.setCount(1);
        ghostInventory.setStackInSlot(ghostIdx, filter);
        getSlot(slotId).setChanged();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem())
            return ItemStack.EMPTY;

        if (isGhostSlot(index)) {
            ghostInventory.setStackInSlot(index - PLAYER_SLOTS, ItemStack.EMPTY);
            getSlot(index).setChanged();
            return ItemStack.EMPTY;
        }

        // Try to fill first empty ghost slot
        for (int i = 0; i < ghostInventory.getSlots(); i++) {
            if (ghostInventory.getStackInSlot(i).isEmpty()) {
                ItemStack copy = slot.getItem().copy();
                copy.setCount(1);
                ghostInventory.setStackInSlot(i, copy);
                getSlot(PLAYER_SLOTS + i).setChanged();
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Build a Pedal from the current ghost inventory state + given name. */
    public PedalData.Pedal buildPedal(String name) {
        PedalData.Pedal pedal = new PedalData.Pedal(pedalType);
        pedal.name = name;
        if (pedalType == PedalData.PedalType.ENCLOSED) {
            pedal.setEnclosedFreqA(ghostInventory.getStackInSlot(0));
            pedal.setEnclosedFreqB(ghostInventory.getStackInSlot(1));
        } else {
            for (int lvl = 0; lvl < levelCount; lvl++) {
                pedal.addCrescendoLevel();
                pedal.setCrescendoFreqA(lvl, ghostInventory.getStackInSlot(lvl * 2));
                pedal.setCrescendoFreqB(lvl, ghostInventory.getStackInSlot(lvl * 2 + 1));
            }
        }
        return pedal;
    }
}
