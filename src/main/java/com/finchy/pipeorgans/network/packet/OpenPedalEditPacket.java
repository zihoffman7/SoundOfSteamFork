package com.finchy.pipeorgans.network.packet;

import com.finchy.pipeorgans.content.console.OrganConsoleBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

public class OpenPedalEditPacket extends SimplePacketBase {

    private final BlockPos consolePos;
    private final int pedalIndex;

    public OpenPedalEditPacket(BlockPos consolePos, int pedalIndex) {
        this.consolePos = consolePos;
        this.pedalIndex = pedalIndex;
    }

    public OpenPedalEditPacket(FriendlyByteBuf buf) {
        consolePos = buf.readBlockPos();
        pedalIndex = buf.readByte();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(consolePos);
        buf.writeByte(pedalIndex);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null)
                return;
            BlockEntity be = player.level().getBlockEntity(consolePos);
            if (be instanceof OrganConsoleBlockEntity console)
                console.openPedalEditMenu(player, pedalIndex);
        });
        return true;
    }
}
