package com.finchy.pipeorgans.content.swell;

import com.finchy.pipeorgans.network.AllPackets;
import com.finchy.pipeorgans.network.packet.SwellScanCapPacket;
import com.finchy.pipeorgans.network.packet.SwellRecalibratePacket;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class SwellControlScreen extends AbstractSimiContainerScreen<SwellControlMenu> {

    private static final int COLOR_PANEL = 0xFF313131;
    private static final int COLOR_PANEL_BORDER = 0xFF161616;
    private static final int COLOR_LABEL = 0xFFB0B0B0;

    private final BlockPos pos;
    private Button recalibrateButton;

    public SwellControlScreen(SwellControlMenu container, Inventory inv, Component title) {
        super(container, inv, title);
        this.pos = container.getControlBE().getBlockPos();
    }

    @Override
    protected void init() {
        setWindowSize(SwellControlMenu.guiWidth(), SwellControlMenu.guiHeight());
        setWindowOffset(0, 0);
        super.init();

        int btnY = topPos + SwellControlMenu.CAP_ROW_Y;
        int bx = leftPos + SwellControlMenu.MARGIN;

        addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustCap(false))
                .bounds(bx, btnY, 16, 14).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustCap(true))
                .bounds(bx + 60, btnY, 16, 14).build());

        int recalY = topPos + SwellControlMenu.RECAL_ROW_Y;
        recalibrateButton = Button.builder(Component.literal("Recalibrate"), b -> recalibrate())
                .bounds(bx, recalY, SwellControlMenu.PLAYER_INV_W, 14).build();
        addRenderableWidget(recalibrateButton);
    }

    private void recalibrate() {
        AllPackets.getChannel().sendToServer(new SwellRecalibratePacket(pos));
        // Only allow one recalibration per GUI open.
        if (recalibrateButton != null) recalibrateButton.active = false;
    }

    private void adjustCap(boolean increase) {
        int current = menu.getControlBE().getScanCap();
        int step;
        if (current < 500) step = 100;
        else if (current < 2000) step = 500;
        else if (current < 5000) step = 1000;
        else if (current < 15000) step = 2500;
        else step = 5000;

        int newCap = increase
                ? current + step
                : Math.max(100, current - step);
        menu.getControlBE().setScanCap(newCap);
        AllPackets.getChannel().sendToServer(new SwellScanCapPacket(pos, newCap));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, COLOR_PANEL_BORDER);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, COLOR_PANEL);

        int invX = SwellControlMenu.playerInvX();
        int invY = SwellControlMenu.playerInvY();
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawSlotBg(graphics, invX + col * 18, invY + row * 18);
        for (int col = 0; col < 9; col++)
            drawSlotBg(graphics, invX + col * 18, invY + 58);
    }

    private void drawSlotBg(GuiGraphics graphics, int gx, int gy) {
        int x = leftPos + gx, y = topPos + gy;
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        graphics.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, SwellControlMenu.MARGIN, SwellControlMenu.TITLE_Y, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal("Scan Cap:"),
                SwellControlMenu.MARGIN, SwellControlMenu.CAP_LABEL_Y, COLOR_LABEL, false);
        int cap = menu.getControlBE().getScanCap();
        graphics.drawString(font, Component.literal(String.valueOf(cap)),
                leftPos + SwellControlMenu.MARGIN + 20 - leftPos, SwellControlMenu.CAP_ROW_Y + 3, 0xFFFFFF, false);
    }
}
