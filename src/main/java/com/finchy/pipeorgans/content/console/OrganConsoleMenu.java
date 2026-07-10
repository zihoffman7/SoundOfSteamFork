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

import java.util.ArrayList;
import java.util.List;

public class OrganConsoleMenu extends MenuBase<OrganConsoleBlockEntity> {

    public static final int MARGIN = 8;
    public static final int TITLE_Y = 6;
    public static final int SLOT = 18;

    // -----------------------------------------------------------------------
    // Pedal widgets (pedalboard mode only) — rendered above the filter row
    // -----------------------------------------------------------------------
    public static final int PEDAL_AREA_TOP = 18;
    public static final int PEDAL_W = 52;
    public static final int PEDAL_H = 36;
    public static final int PEDAL_GAP = 4;
    private static final int PEDAL_COUNT = PedalData.PEDAL_COUNT; // 2
    public static final int PEDALS_TOTAL_W = PEDAL_COUNT * PEDAL_W + (PEDAL_COUNT - 1) * PEDAL_GAP;

    /** X of the i-th pedal widget, centered within guiWidth. */
    public static int pedalX(int index, int guiW) {
        int startX = (guiW - PEDALS_TOTAL_W) / 2;
        return startX + index * (PEDAL_W + PEDAL_GAP);
    }

    // Total height occupied by pedal widgets row
    public static final int PEDAL_ROW_H = PEDAL_H + 6;

    // -----------------------------------------------------------------------
    // Horizontal filter row near the top. Each section has an OUTPUT and an INPUT filter, grouped together.
    // -----------------------------------------------------------------------
    public static final int FILTERS_X = 8;       // bg-left of the first filter group
    public static final int FILTER_ROW_Y = 18;   // bg-top of the filter row (manuals mode)
    public static final int GROUP_GAP = 6;       // gap between adjacent section groups

    /** Y of the filter row — shifted down in pedalboard mode to leave room for pedal widgets. */
    public static int filterRowY(boolean pedalboard) {
        return pedalboard ? PEDAL_AREA_TOP + PEDAL_ROW_H : FILTER_ROW_Y;
    }

    // Stacked keyboards below the filter row
    public static final int KEYBOARDS_TOP_BASE = FILTER_ROW_Y + SLOT + 8; // 44 in manuals mode
    public static final int ROW_PITCH = 22;      // KEY_H + a tiny gap between manuals
    public static final int KEY_H = 18;
    public static final int KEYBOARD_X = 8;
    public static final int MANUAL_KEY_W = 5;
    public static final int PEDAL_KEY_W = 8;

    public static final int PLAYER_INV_W = 162; // 9 * 18
    public static final int PLAYER_INV_H = 76;  // 3 rows + gap + hotbar
    public static final int INV_GAP = 10;

    private static final int PLAYER_SLOTS = 36;

    private boolean pedalboardMode;
    private boolean hasPedalboard;
    private int manualCount;
    private int[] ghostSlotIndices;

    public ItemStackHandler ghostInventory;

    public OrganConsoleMenu(MenuType<?> type, int id, Inventory inv, FriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    public OrganConsoleMenu(MenuType<?> type, int id, Inventory inv, OrganConsoleBlockEntity be) {
        super(type, id, inv, be);
    }

    public static OrganConsoleMenu create(int id, Inventory inv, OrganConsoleBlockEntity be) {
        return new OrganConsoleMenu(AllMenuTypes.ORGAN_CONSOLE_MENU.get(), id, inv, be);
    }

    public boolean isPedalboardMode() { return pedalboardMode; }
    public boolean isHasPedalboard()   { return hasPedalboard; }
    public OrganConsoleBlockEntity getConsole() { return contentHolder; }
    public int getManualCount() { return manualCount; }

    // Layout helpers
    public static int keyboardRows(boolean pedalboard, int manualCount) {
        return pedalboard ? 1 : Math.max(1, manualCount);
    }

    // Number of filter groups
    public static int emitFilterCount(boolean pedalboard, int manualCount) {
        return keyboardRows(pedalboard, manualCount);
    }

    public static final int FILTER_PITCH = SLOT + GROUP_GAP;

    public static int filterBgX(int i) {
        return FILTERS_X + i * FILTER_PITCH;
    }

    public static int sectionForRow(boolean pedalboard, int row) {
        return pedalboard ? OrganConsoleBlockEntity.PEDAL_SECTION : row;
    }

    public static int keyboardsTop(boolean pedalboard) {
        return filterRowY(pedalboard) + SLOT + 8;
    }

    public static int rowTop(boolean pedalboard, int row) {
        return keyboardsTop(pedalboard) + row * ROW_PITCH;
    }

    // Legacy overload used in screen render — defaults to non-pedalboard (manuals)
    public static int rowTop(int row) {
        return KEYBOARDS_TOP_BASE + row * ROW_PITCH;
    }

    public static int keyWidth(boolean pedalboard) {
        return pedalboard ? PEDAL_KEY_W : MANUAL_KEY_W;
    }

    public static int keyCountFor(boolean pedalboard) {
        return pedalboard ? OrganConsoleBlockEntity.PEDAL_KEY_COUNT : OrganConsoleBlockEntity.MANUAL_KEY_COUNT;
    }

    public static int keyboardWidth(boolean pedalboard) {
        return keyCountFor(pedalboard) * keyWidth(pedalboard);
    }

    /** Bottom of content area. If pedalboard mode with no keyboard, ends after pedal row. */
    public static int contentBottom(boolean pedalboard, boolean hasPedalboard, int manualCount) {
        if (pedalboard && !hasPedalboard)
            return PEDAL_AREA_TOP + PEDAL_ROW_H;
        return keyboardsTop(pedalboard) + keyboardRows(pedalboard, manualCount) * ROW_PITCH;
    }

    public static int playerInvY(boolean pedalboard, boolean hasPedalboard, int manualCount) {
        return contentBottom(pedalboard, hasPedalboard, manualCount) + INV_GAP;
    }

    public static int filtersWidth(boolean pedalboard, boolean hasPedalboard, int manualCount) {
        if (pedalboard && !hasPedalboard) return 0;
        return emitFilterCount(pedalboard, manualCount) * FILTER_PITCH - GROUP_GAP;
    }

    public static int guiWidth(boolean pedalboard, boolean hasPedalboard, int manualCount) {
        int byKeyboard = (pedalboard && !hasPedalboard) ? 0 : KEYBOARD_X + keyboardWidth(pedalboard) + MARGIN;
        int byFilters = FILTERS_X + filtersWidth(pedalboard, hasPedalboard, manualCount) + MARGIN;
        int byPedals = pedalboard ? MARGIN + PEDALS_TOTAL_W + MARGIN : 0;
        return Math.max(Math.max(Math.max(byKeyboard, byFilters), PLAYER_INV_W + 2 * MARGIN), byPedals);
    }

    public static int guiHeight(boolean pedalboard, boolean hasPedalboard, int manualCount) {
        return playerInvY(pedalboard, hasPedalboard, manualCount) + PLAYER_INV_H + MARGIN;
    }

    public static int playerInvX(boolean pedalboard, boolean hasPedalboard, int manualCount) {
        return (guiWidth(pedalboard, hasPedalboard, manualCount) - PLAYER_INV_W) / 2;
    }

    @Override
    protected OrganConsoleBlockEntity createOnClient(FriendlyByteBuf extraData) {
        ClientLevel world = Minecraft.getInstance().level;
        BlockEntity blockEntity = world.getBlockEntity(extraData.readBlockPos());
        if (blockEntity instanceof OrganConsoleBlockEntity console) {
            console.readClient(extraData.readNbt());
            console.menuPedalboardMode = extraData.readBoolean();
            console.menuManualCount = extraData.readVarInt();
            console.menuHasPedalboard = extraData.readBoolean();
            return console;
        }
        return null;
    }

    @Override
    protected void initAndReadInventory(OrganConsoleBlockEntity contentHolder) {
        ghostInventory = contentHolder.getFilterInventory();
        pedalboardMode = contentHolder.menuPedalboardMode;
        manualCount = contentHolder.menuManualCount;
        hasPedalboard = contentHolder.menuHasPedalboard;
    }

    @Override
    protected void addSlots() {
        addPlayerSlots(
                playerInvX(pedalboardMode, hasPedalboard, manualCount),
                playerInvY(pedalboardMode, hasPedalboard, manualCount));

        List<Integer> ghost = new ArrayList<>();
        if (!pedalboardMode || hasPedalboard) {
            int groups = emitFilterCount(pedalboardMode, manualCount);
            for (int i = 0; i < groups; i++) {
                int section = sectionForRow(pedalboardMode, i);
                addSlot(new SlotItemHandler(ghostInventory, OrganConsoleBlockEntity.divisionSlot(section),
                        filterBgX(i) + 1, filterRowY(pedalboardMode) + 1));
                ghost.add(OrganConsoleBlockEntity.divisionSlot(section));
            }
        }
        ghostSlotIndices = ghost.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    protected void saveData(OrganConsoleBlockEntity contentHolder) {
        // Nothing to persist.
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container == playerInventory;
    }

    // Filter-only ghost slot interaction (mirrors TrackerBarMenu)
    private boolean isGhostSlot(int slotId) {
        return slotId >= PLAYER_SLOTS && (slotId - PLAYER_SLOTS) < ghostSlotIndices.length;
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (!isGhostSlot(slotId)) {
            super.clicked(slotId, dragType, clickType, player);
            return;
        }
        if (clickType == ClickType.THROW)
            return;

        int ghostIdx = ghostSlotIndices[slotId - PLAYER_SLOTS];
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

        ItemStack filter;
        if (held.isEmpty()) {
            filter = ItemStack.EMPTY;
        } else {
            filter = held.copy();
            filter.setCount(1);
        }
        ghostInventory.setStackInSlot(ghostIdx, filter);
        getSlot(slotId).setChanged();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem())
            return ItemStack.EMPTY;

        if (isGhostSlot(index)) {
            int ghostIdx = ghostSlotIndices[index - PLAYER_SLOTS];
            ghostInventory.setStackInSlot(ghostIdx, ItemStack.EMPTY);
            getSlot(index).setChanged();
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getItem();
        for (int ordinal = 0; ordinal < ghostSlotIndices.length; ordinal++) {
            int ghostIdx = ghostSlotIndices[ordinal];
            if (ghostInventory.getStackInSlot(ghostIdx).isEmpty()) {
                ItemStack copy = original.copy();
                copy.setCount(1);
                ghostInventory.setStackInSlot(ghostIdx, copy);
                getSlot(PLAYER_SLOTS + ordinal).setChanged();
                break;
            }
        }
        return ItemStack.EMPTY;
    }
}
