package com.finchy.pipeorgans.network.packet;

import com.finchy.pipeorgans.content.console.OrganConsoleBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

public class PedalPositionPacket extends SimplePacketBase {

    private final BlockPos consolePos;
    private final int pedalIndex;
    private final int position;

    public PedalPositionPacket(BlockPos consolePos, int pedalIndex, int position) {
        this.consolePos = consolePos;
        this.pedalIndex = pedalIndex;
        this.position = position;
    }

    public PedalPositionPacket(FriendlyByteBuf buf) {
        consolePos = buf.readBlockPos();
        pedalIndex = buf.readByte();
        position = buf.readByte();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(consolePos);
        buf.writeByte(pedalIndex);
        buf.writeByte(position);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null)
                return;
            BlockEntity be = player.level().getBlockEntity(consolePos);
            if (be instanceof OrganConsoleBlockEntity console)
                console.setPedalPosition(pedalIndex, position);
        });
        return true;
    }
}
