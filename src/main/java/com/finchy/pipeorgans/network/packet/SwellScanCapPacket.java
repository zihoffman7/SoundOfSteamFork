package com.finchy.pipeorgans.network.packet;

import com.finchy.pipeorgans.content.swell.SwellControlBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

public class SwellScanCapPacket extends SimplePacketBase {

    private final BlockPos pos;
    private final int cap;

    public SwellScanCapPacket(BlockPos pos, int cap) {
        this.pos = pos;
        this.cap = cap;
    }

    public SwellScanCapPacket(FriendlyByteBuf buf) {
        pos = buf.readBlockPos();
        cap = buf.readInt();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(cap);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof SwellControlBlockEntity control)
                control.setScanCap(cap);
        });
        return true;
    }
}
