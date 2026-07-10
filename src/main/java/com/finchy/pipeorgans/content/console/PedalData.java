package com.finchy.pipeorgans.content.console;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds configuration and runtime state for the three console pedals.
 *
 * Pedals 0 and 1 are ENCLOSED: each has a name, one frequency pair (two ItemStacks),
 * and outputs a redstone signal equal to the pedal position (0-15) on that frequency pair.
 *
 * Pedal 2 is CRESCENDO: it has a name and up to 15 frequency pairs organised into
 * levels 1-15.  At position N it activates all pairs from level 1 through N (cumulative).
 */
public class PedalData {

    public static final int PEDAL_COUNT = 3;
    public static final int CRESCENDO_INDEX = 2;
    public static final int MAX_POSITION = 15;
    public static final int MAX_CRESCENDO_LEVELS = MAX_POSITION; // levels 1-15

    public enum PedalType { ENCLOSED, CRESCENDO }

    // -----------------------------------------------------------------------
    // Single pedal configuration
    // -----------------------------------------------------------------------
    public static class Pedal {
        public String name = "";
        public final PedalType type;

        // ENCLOSED: exactly one filter pair (items[0] = freq A, items[1] = freq B)
        // CRESCENDO: items[level*2] and items[level*2+1] for level 0..14
        //            (level 0 = position 1, level 14 = position 15)
        private final List<ItemStack> items = new ArrayList<>();

        // Runtime (not persisted to disk, synced via packet)
        public transient int position = 0;

        public Pedal(PedalType type) {
            this.type = type;
            if (type == PedalType.ENCLOSED) {
                items.add(ItemStack.EMPTY);
                items.add(ItemStack.EMPTY);
            }
            // CRESCENDO starts with no levels; levels are added via the edit screen
        }

        // --- Enclosed helpers ---

        public ItemStack getEnclosedFreqA() {
            return items.size() > 0 ? items.get(0) : ItemStack.EMPTY;
        }

        public ItemStack getEnclosedFreqB() {
            return items.size() > 1 ? items.get(1) : ItemStack.EMPTY;
        }

        public void setEnclosedFreqA(ItemStack stack) {
            ensureSize(2);
            items.set(0, stack.copy());
        }

        public void setEnclosedFreqB(ItemStack stack) {
            ensureSize(2);
            items.set(1, stack.copy());
        }

        // --- Crescendo helpers ---

        /** Number of configured levels (0 to MAX_CRESCENDO_LEVELS). */
        public int crescendoLevelCount() {
            return items.size() / 2;
        }

        public ItemStack getCrescendoFreqA(int level) {
            int idx = level * 2;
            return idx < items.size() ? items.get(idx) : ItemStack.EMPTY;
        }

        public ItemStack getCrescendoFreqB(int level) {
            int idx = level * 2 + 1;
            return idx < items.size() ? items.get(idx) : ItemStack.EMPTY;
        }

        public void setCrescendoFreqA(int level, ItemStack stack) {
            ensureCrescendoLevel(level);
            items.set(level * 2, stack.copy());
        }

        public void setCrescendoFreqB(int level, ItemStack stack) {
            ensureCrescendoLevel(level);
            items.set(level * 2 + 1, stack.copy());
        }

        /** Adds a new empty level at the end (up to MAX_CRESCENDO_LEVELS). */
        public boolean addCrescendoLevel() {
            if (crescendoLevelCount() >= MAX_CRESCENDO_LEVELS)
                return false;
            items.add(ItemStack.EMPTY);
            items.add(ItemStack.EMPTY);
            return true;
        }

        /** Removes the last level. */
        public boolean removeCrescendoLevel() {
            int count = crescendoLevelCount();
            if (count == 0)
                return false;
            items.remove(count * 2 - 1);
            items.remove(count * 2 - 2);
            return true;
        }

        private void ensureSize(int size) {
            while (items.size() < size)
                items.add(ItemStack.EMPTY);
        }

        private void ensureCrescendoLevel(int level) {
            int needed = (level + 1) * 2;
            while (items.size() < needed)
                items.add(ItemStack.EMPTY);
        }

        // --- Serialization ---

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", name);
            tag.putString("Type", type.name());
            ListTag list = new ListTag();
            for (ItemStack stack : items) {
                CompoundTag stackTag = new CompoundTag();
                stack.save(stackTag);
                list.add(stackTag);
            }
            tag.put("Items", list);
            return tag;
        }

        public static Pedal fromNbt(CompoundTag tag) {
            PedalType type;
            try {
                type = PedalType.valueOf(tag.getString("Type"));
            } catch (IllegalArgumentException e) {
                type = PedalType.ENCLOSED;
            }
            Pedal pedal = new Pedal(type);
            pedal.name = tag.getString("Name");
            pedal.items.clear();
            ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++)
                pedal.items.add(ItemStack.of(list.getCompound(i)));
            return pedal;
        }
    }

    // -----------------------------------------------------------------------
    // Container
    // -----------------------------------------------------------------------
    private final Pedal[] pedals = new Pedal[PEDAL_COUNT];

    public PedalData() {
        pedals[0] = new Pedal(PedalType.ENCLOSED);
        pedals[1] = new Pedal(PedalType.ENCLOSED);
        pedals[2] = new Pedal(PedalType.CRESCENDO);
    }

    public Pedal getPedal(int index) {
        return pedals[index];
    }

    /** Directly replace a pedal slot (preserves runtime position from the replacement object). */
    public void setPedal(int index, Pedal pedal) {
        if (index >= 0 && index < PEDAL_COUNT)
            pedals[index] = pedal;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Pedal pedal : pedals)
            list.add(pedal.toNbt());
        tag.put("Pedals", list);
        return tag;
    }

    public static PedalData fromNbt(CompoundTag tag) {
        PedalData data = new PedalData();
        ListTag list = tag.getList("Pedals", Tag.TAG_COMPOUND);
        for (int i = 0; i < PEDAL_COUNT && i < list.size(); i++)
            data.pedals[i] = Pedal.fromNbt(list.getCompound(i));
        return data;
    }

    /** Write positions only (for lightweight sync). */
    public void writePositions(net.minecraft.network.FriendlyByteBuf buf) {
        for (Pedal pedal : pedals)
            buf.writeByte(pedal.position);
    }

    /** Read positions only. */
    public void readPositions(net.minecraft.network.FriendlyByteBuf buf) {
        for (Pedal pedal : pedals)
            pedal.position = buf.readByte() & 0xFF;
    }
}
