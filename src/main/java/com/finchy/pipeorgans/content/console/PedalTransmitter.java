package com.finchy.pipeorgans.content.console;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the redstone link transmitters for the two enclosed pedals.
 * Each pedal transmits its position (0-15) on its configured frequency pair.
 */
public class PedalTransmitter {

    private final OrganConsoleBlockEntity be;
    private final List<PedalLinkable> active = new ArrayList<>();

    public PedalTransmitter(OrganConsoleBlockEntity be) {
        this.be = be;
    }

    public void initialize(Level level) {
        removeAll(level);
        rebuildAll(level);
    }

    public void unload(Level level) {
        removeAll(level);
    }

    public void onPedalChanged(int pedalIndex, Level level) {
        // Remove old transmitter for this pedal, add new one
        active.removeIf(t -> {
            if (t.pedalIndex == pedalIndex) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, t);
                return true;
            }
            return false;
        });
        buildPedal(pedalIndex, level);
        for (PedalLinkable t : active)
            if (t.pedalIndex == pedalIndex)
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, t);
    }

    public void rebuildAll(Level level) {
        for (int i = 0; i < PedalData.PEDAL_COUNT; i++)
            buildPedal(i, level);
    }

    private void buildPedal(int pedalIndex, Level level) {
        PedalData.Pedal pedal = be.getPedalData().getPedal(pedalIndex);
        ItemStack freqA = pedal.getFreqA();
        ItemStack freqB = pedal.getFreqB();
        if (!freqA.isEmpty() && !freqB.isEmpty()) {
            PedalLinkable link = new PedalLinkable(pedalIndex,
                    Couple.create(Frequency.of(freqA), Frequency.of(freqB)));
            active.add(link);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, link);
        }
    }

    private void removeAll(Level level) {
        for (PedalLinkable link : active)
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, link);
        active.clear();
    }

    private class PedalLinkable implements IRedstoneLinkable {
        final int pedalIndex;
        private final Couple<Frequency> networkKey;

        PedalLinkable(int pedalIndex, Couple<Frequency> networkKey) {
            this.pedalIndex = pedalIndex;
            this.networkKey = networkKey;
        }

        @Override
        public int getTransmittedStrength() {
            return be.getPedalData().getPedal(pedalIndex).position;
        }

        @Override public void setReceivedStrength(int power) {}
        @Override public boolean isListening() { return false; }

        @Override
        public boolean isAlive() {
            return !be.isRemoved()
                    && be.getLevel() != null
                    && be.getLevel().getBlockEntity(be.getBlockPos()) == be;
        }

        @Override public BlockPos getLocation() { return be.getBlockPos(); }
        @Override public Couple<Frequency> getNetworkKey() { return networkKey; }
    }
}
