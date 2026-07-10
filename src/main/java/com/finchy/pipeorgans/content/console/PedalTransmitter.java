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
 * Manages the redstone link transmitters for all three pedals on a console.
 *
 * ENCLOSED pedal: one transmitter on its configured frequency pair,
 *   strength = pedal position (0-15).
 *
 * CRESCENDO pedal: one transmitter per configured level whose level index
 *   is <= current position; all transmit strength 15 (on/off cumulative).
 *   Levels above the current position transmit 0.
 */
public class PedalTransmitter {

    private final OrganConsoleBlockEntity be;
    private final List<PedalLinkable> active = new ArrayList<>();

    public PedalTransmitter(OrganConsoleBlockEntity be) {
        this.be = be;
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void initialize(Level level) {
        removeAll(level);
        rebuildAll(level);
    }

    public void unload(Level level) {
        removeAll(level);
    }

    /** Called when pedal position changes or pedal config changes. */
    public void onPedalChanged(int pedalIndex, Level level) {
        // Remove existing transmitters for this pedal, then rebuild them
        active.removeIf(t -> {
            if (t.pedalIndex == pedalIndex) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, t);
                return true;
            }
            return false;
        });
        buildPedal(pedalIndex, level);
        // Force network update for all remaining transmitters of this pedal
        for (PedalLinkable t : active)
            if (t.pedalIndex == pedalIndex)
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, t);
    }

    /** Full rebuild — call after loading or when pedal config changes. */
    public void rebuildAll(Level level) {
        for (int i = 0; i < PedalData.PEDAL_COUNT; i++)
            buildPedal(i, level);
    }

    private void buildPedal(int pedalIndex, Level level) {
        PedalData.Pedal pedal = be.getPedalData().getPedal(pedalIndex);

        if (pedal.type == PedalData.PedalType.ENCLOSED) {
            ItemStack freqA = pedal.getEnclosedFreqA();
            ItemStack freqB = pedal.getEnclosedFreqB();
            if (!freqA.isEmpty() && !freqB.isEmpty()) {
                PedalLinkable link = new PedalLinkable(pedalIndex, -1,
                        Couple.create(Frequency.of(freqA), Frequency.of(freqB)));
                active.add(link);
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, link);
            }
        } else {
            // CRESCENDO: one transmitter per configured level
            int levelCount = pedal.crescendoLevelCount();
            for (int lvl = 0; lvl < levelCount; lvl++) {
                ItemStack freqA = pedal.getCrescendoFreqA(lvl);
                ItemStack freqB = pedal.getCrescendoFreqB(lvl);
                if (!freqA.isEmpty() && !freqB.isEmpty()) {
                    PedalLinkable link = new PedalLinkable(pedalIndex, lvl,
                            Couple.create(Frequency.of(freqA), Frequency.of(freqB)));
                    active.add(link);
                    Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, link);
                }
            }
        }
    }

    private void removeAll(Level level) {
        for (PedalLinkable link : active)
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, link);
        active.clear();
    }

    // -----------------------------------------------------------------------
    // IRedstoneLinkable implementation
    // -----------------------------------------------------------------------

    private class PedalLinkable implements IRedstoneLinkable {

        /** Which pedal (0, 1 = enclosed; 2 = crescendo). */
        final int pedalIndex;

        /**
         * For enclosed: -1 (ignored).
         * For crescendo: the 0-based level index this transmitter represents.
         *   Level is active when pedal.position >= levelIndex + 1.
         */
        final int levelIndex;

        private final Couple<Frequency> networkKey;

        PedalLinkable(int pedalIndex, int levelIndex, Couple<Frequency> networkKey) {
            this.pedalIndex = pedalIndex;
            this.levelIndex = levelIndex;
            this.networkKey = networkKey;
        }

        @Override
        public int getTransmittedStrength() {
            PedalData.Pedal pedal = be.getPedalData().getPedal(pedalIndex);
            if (pedal.type == PedalData.PedalType.ENCLOSED) {
                return pedal.position; // 0-15 directly
            } else {
                // Crescendo: active if position covers this level (levels are 1-indexed; levelIndex is 0-based)
                return pedal.position >= levelIndex + 1 ? 15 : 0;
            }
        }

        @Override
        public void setReceivedStrength(int power) {
            // Pedals are transmitters only
        }

        @Override
        public boolean isListening() {
            return false;
        }

        @Override
        public boolean isAlive() {
            return !be.isRemoved()
                    && be.getLevel() != null
                    && be.getLevel().getBlockEntity(be.getBlockPos()) == be;
        }

        @Override
        public BlockPos getLocation() {
            return be.getBlockPos();
        }

        @Override
        public Couple<Frequency> getNetworkKey() {
            return networkKey;
        }
    }
}
