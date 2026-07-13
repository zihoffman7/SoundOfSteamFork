package com.finchy.pipeorgans.content.console;

import com.finchy.pipeorgans.network.AllPackets;
import com.finchy.pipeorgans.network.packet.PedalConfigPacket;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PedalEditScreen extends AbstractSimiContainerScreen<PedalEditMenu> {

    private static final int COLOR_PANEL        = 0xFF313131;
    private static final int COLOR_PANEL_BORDER = 0xFF161616;
    private static final int COLOR_SLOT_BORDER  = 0xFF373737;
    private static final int COLOR_SLOT_INNER   = 0xFF8B8B8B;
    private static final int COLOR_LABEL        = 0xFFB0B0B0;

    private final BlockPos consolePos;
    private final int pedalIndex;

    private EditBox nameBox;

    public PedalEditScreen(PedalEditMenu container, Inventory inv, Component title) {
        super(container, inv, title);
        this.consolePos = container.getConsoleBE().getBlockPos();
        this.pedalIndex = container.getPedalIndex();
    }

    @Override
    protected void init() {
        setWindowSize(PedalEditMenu.guiWidth(), PedalEditMenu.guiHeight());
        setWindowOffset(0, 0);
        super.init();

        PedalData.Pedal pedal = menu.getConsoleBE().getPedalData().getPedal(pedalIndex);

        // Name field — greyed-out placeholder when empty
        nameBox = new EditBox(font,
                leftPos + PedalEditMenu.FIELD_X,
                topPos + PedalEditMenu.NAME_BOX_Y,
                PedalEditMenu.FIELD_W, PedalEditMenu.BOX_H,
                Component.empty());
        nameBox.setMaxLength(32);
        nameBox.setValue(pedal.name);
        nameBox.setHint(Component.translatable("gui.pipeorgans.pedal_edit.name_hint")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        addRenderableWidget(nameBox);

        // Save button — sits between freq slots and inventory
        int bw = 50;
        int by = topPos + PedalEditMenu.BUTTONS_Y;
        int bx = leftPos + PedalEditMenu.FIELD_X + (PedalEditMenu.FIELD_W - bw) / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.pipeorgans.pedal_edit.save"), b -> sendSave())
                .bounds(bx, by, bw, 18).build());
    }

    private void sendSave() {
        PedalData.Pedal built = menu.buildPedal(nameBox.getValue());
        AllPackets.getChannel().sendToServer(new PedalConfigPacket(consolePos, pedalIndex, built));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox.isFocused()) {
            if (keyCode == 256) { sendSave(); return true; }
            nameBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (keyCode == 256 || minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            sendSave();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (nameBox.isFocused()) return nameBox.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() { super.onClose(); } // no auto-save; only via Save button

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, COLOR_PANEL_BORDER);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, COLOR_PANEL);

        drawSlotBg(graphics, PedalEditMenu.SLOT_A_X + 1, PedalEditMenu.FREQ_ROW_Y + 1);
        drawSlotBg(graphics, PedalEditMenu.SLOT_B_X + 1, PedalEditMenu.FREQ_ROW_Y + 1);

        int invX = PedalEditMenu.playerInvX();
        int invY = PedalEditMenu.playerInvY();
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawSlotBg(graphics, invX + col * 18, invY + row * 18);
        for (int col = 0; col < 9; col++)
            drawSlotBg(graphics, invX + col * 18, invY + 58);
    }

    private void drawSlotBg(GuiGraphics graphics, int gx, int gy) {
        int x = leftPos + gx, y = topPos + gy;
        graphics.fill(x - 1, y - 1, x + 17, y + 17, COLOR_SLOT_BORDER);
        graphics.fill(x, y, x + 16, y + 16, COLOR_SLOT_INNER);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, PedalEditMenu.FIELD_X, PedalEditMenu.TITLE_Y, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.pipeorgans.pedal_edit.name"),
                PedalEditMenu.FIELD_X, PedalEditMenu.NAME_LABEL_Y, COLOR_LABEL, false);
        graphics.drawString(font, Component.translatable("gui.pipeorgans.pedal_edit.frequency_pair"),
                PedalEditMenu.SLOT_A_X, PedalEditMenu.FREQ_LABEL_Y, COLOR_LABEL, false);
    }
}
