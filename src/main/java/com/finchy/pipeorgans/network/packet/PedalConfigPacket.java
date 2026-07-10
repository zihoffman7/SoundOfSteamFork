package com.finchy.pipeorgans.network.packet;

import com.finchy.pipeorgans.content.console.OrganConsoleBlockEntity;
import com.finchy.pipeorgans.content.console.PedalData;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

public class PedalConfigPacket extends SimplePacketBase {

    private final BlockPos consolePos;
    private final int pedalIndex;
    private final PedalData.Pedal pedal;

    public PedalConfigPacket(BlockPos consolePos, int pedalIndex, PedalData.Pedal pedal) {
        this.consolePos = consolePos;
        this.pedalIndex = pedalIndex;
        this.pedal = pedal;
    }

    public PedalConfigPacket(FriendlyByteBuf buf) {
        consolePos = buf.readBlockPos();
        pedalIndex = buf.readByte();
        pedal = PedalData.Pedal.fromNbt(buf.readNbt());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(consolePos);
        buf.writeByte(pedalIndex);
        buf.writeNbt(pedal.toNbt());
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null)
                return;
            BlockEntity be = player.level().getBlockEntity(consolePos);
            if (be instanceof OrganConsoleBlockEntity console)
                console.setPedalConfig(pedalIndex, pedal);
        });
        return true;
    }
}
