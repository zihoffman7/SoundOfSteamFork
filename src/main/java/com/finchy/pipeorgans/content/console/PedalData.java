package com.finchy.pipeorgans.content.console;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds configuration and runtime state for the three enclosed pedals on a console.
 *
 * Each pedal has a name, one frequency pair (two ItemStacks), and outputs a redstone
 * signal equal to the pedal position (0-15) on that frequency pair.
 */
public class PedalData {

    public static final int PEDAL_COUNT = 2;
    public static final int MAX_POSITION = 15;

    // -----------------------------------------------------------------------
    // Single pedal
    // -----------------------------------------------------------------------
    public static class Pedal {
        public String name = "";
        private ItemStack freqA = ItemStack.EMPTY;
        private ItemStack freqB = ItemStack.EMPTY;

        // Runtime only — synced via notifyUpdate(), persisted to disk
        public int position = 0;

        public Pedal() {}

        public ItemStack getFreqA() { return freqA; }
        public ItemStack getFreqB() { return freqB; }

        public void setFreqA(ItemStack stack) { freqA = stack.isEmpty() ? ItemStack.EMPTY : stack.copy(); }
        public void setFreqB(ItemStack stack) { freqB = stack.isEmpty() ? ItemStack.EMPTY : stack.copy(); }

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", name);
            tag.putInt("Position", position);
            CompoundTag a = new CompoundTag(); freqA.save(a); tag.put("FreqA", a);
            CompoundTag b = new CompoundTag(); freqB.save(b); tag.put("FreqB", b);
            return tag;
        }

        public static Pedal fromNbt(CompoundTag tag) {
            Pedal pedal = new Pedal();
            pedal.name = tag.getString("Name");
            pedal.position = tag.getInt("Position");
            if (tag.contains("FreqA")) pedal.freqA = ItemStack.of(tag.getCompound("FreqA"));
            if (tag.contains("FreqB")) pedal.freqB = ItemStack.of(tag.getCompound("FreqB"));
            return pedal;
        }
    }

    // -----------------------------------------------------------------------
    // Container
    // -----------------------------------------------------------------------
    private final Pedal[] pedals = new Pedal[PEDAL_COUNT];

    public PedalData() {
        for (int i = 0; i < PEDAL_COUNT; i++)
            pedals[i] = new Pedal();
    }

    public Pedal getPedal(int index) {
        return pedals[index];
    }

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
}
