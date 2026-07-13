package com.finchy.pipeorgans.network.packet;

import com.finchy.pipeorgans.content.swell.SwellControlBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

// Sent when Recalibrate button pressed in the swell control GUI
// Forces a full flood-fill rescan
public class SwellRecalibratePacket extends SimplePacketBase {

    private final BlockPos pos;

    public SwellRecalibratePacket(BlockPos pos) {
        this.pos = pos;
    }

    public SwellRecalibratePacket(FriendlyByteBuf buf) {
        pos = buf.readBlockPos();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof SwellControlBlockEntity control)
                control.forceRescan();
        });
        return true;
    }
}
