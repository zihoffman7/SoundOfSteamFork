package com.finchy.pipeorgans.content.console;

import com.finchy.pipeorgans.network.AllPackets;
import com.finchy.pipeorgans.network.packet.OpenPedalEditPacket;
import com.finchy.pipeorgans.network.packet.OrganConsoleNotePacket;
import com.finchy.pipeorgans.network.packet.PedalPositionPacket;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.SlotItemHandler;

import java.util.HashSet;
import java.util.Set;
public class OrganConsoleScreen extends AbstractSimiContainerScreen<OrganConsoleMenu> {

    private static final int NOTE_VELOCITY = 127;

    private static final int COLOR_PANEL = 0xFF313131;
    private static final int COLOR_PANEL_BORDER = 0xFF161616;
    private static final int COLOR_NATURAL = 0xFFE8E8E8;
    private static final int COLOR_SHARP = 0xFF1E1E1E;
    private static final int COLOR_PRESSED = 0xFFFFC83C;   // locally held (left momentary or right latch)
    private static final int COLOR_RECEIVED = 0xFF50C8FF;  // externally received
    private static final int COLOR_KEY_BORDER = 0xFF000000;
    private static final int COLOR_SLOT_BORDER = 0xFF373737;
    private static final int COLOR_SLOT_INNER = 0xFF8B8B8B;

    private final boolean pedalboardMode;
    private final BlockPos consolePos;
    private final OrganConsoleBlockEntity console;

    private final Set<Long> latched = new HashSet<>();
    private long momentaryKey = -1;
    private final Set<Long> active = new HashSet<>();

    public OrganConsoleScreen(OrganConsoleMenu container, Inventory inv, Component title) {
        super(container, inv, title);
        this.pedalboardMode = container.isPedalboardMode();
        this.console = container.getConsole();
        this.consolePos = console.getBlockPos();
    }

    // Read the count live from the menu so layout always matches the slots
    private int manualCount() {
        return menu.getManualCount();
    }

    private static long encode(int section, int key) {
        return (((long) section) << 32) | (key & 0xFFFFFFFFL);
    }

    @Override
    protected void init() {
        setWindowSize(
                OrganConsoleMenu.guiWidth(pedalboardMode, manualCount()),
                OrganConsoleMenu.guiHeight(pedalboardMode, manualCount()));
        setWindowOffset(0, 0);
        super.init();
    }

    // Geometry
    private int rowCount() {
        return OrganConsoleMenu.keyboardRows(pedalboardMode, manualCount());
    }

    private int sectionForRow(int row) {
        if (pedalboardMode)
            return row == 0 ? OrganConsoleBlockEntity.PEDAL_SECTION : -1;
        return row < manualCount() ? row : -1;
    }

    private long keyAt(int guiX, int guiY) {
        int keyW = OrganConsoleMenu.keyWidth(pedalboardMode);
        int keyboardWidth = OrganConsoleMenu.keyboardWidth(pedalboardMode);
        int keyCount = OrganConsoleMenu.keyCountFor(pedalboardMode);

        if (guiX < OrganConsoleMenu.KEYBOARD_X || guiX >= OrganConsoleMenu.KEYBOARD_X + keyboardWidth)
            return -1;

        for (int row = 0; row < rowCount(); row++) {
            int section = sectionForRow(row);
            if (section < 0)
                continue;
            int keyTop = OrganConsoleMenu.rowTop(pedalboardMode, row);
            if (guiY >= keyTop && guiY < keyTop + OrganConsoleMenu.KEY_H) {
                int key = (guiX - OrganConsoleMenu.KEYBOARD_X) / keyW;
                if (key >= 0 && key < keyCount)
                    return encode(section, key);
            }
        }
        return -1;
    }

    // Note state
    private void updateActive() {
        Set<Long> desired = new HashSet<>(latched);
        if (momentaryKey != -1)
            desired.add(momentaryKey);

        for (long encoded : new HashSet<>(active))
            if (!desired.contains(encoded)) {
                sendNote(encoded, false);
                active.remove(encoded);
            }
        for (long encoded : desired)
            if (active.add(encoded))
                sendNote(encoded, true);
    }

    private void sendNote(long encoded, boolean on) {
        int section = (int) (encoded >> 32);
        int key = (int) (encoded & 0xFFFFFFFFL);
        AllPackets.getChannel().sendToServer(new OrganConsoleNotePacket(consolePos, section, key, on, NOTE_VELOCITY));
    }

    private void clearAll() {
        latched.clear();
        momentaryKey = -1;
        updateActive();
    }

    // Input
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int guiX = (int) mouseX - leftPos;
        int guiY = (int) mouseY - topPos;

        // Pedal +/- buttons
        int pedalBtn = pedalButtonAt(guiX, guiY);
        if (pedalBtn != 0) {
            int pedalIndex = Math.abs(pedalBtn) / 10 - 1;
            boolean plus = pedalBtn > 0;
            int currentPos = console.getPedalData().getPedal(pedalIndex).position;
            int newPos = plus ? Math.min(PedalData.MAX_POSITION, currentPos + 1)
                              : Math.max(0, currentPos - 1);
            if (newPos != currentPos) {
                console.getPedalData().getPedal(pedalIndex).position = newPos; // optimistic client update
                AllPackets.getChannel().sendToServer(
                        new PedalPositionPacket(consolePos, pedalIndex, newPos));
            }
            return true;
        }

        // Right-click pedal widget body → open edit screen
        if (button == 1) {
            int pedalIndex = pedalAt(guiX, guiY);
            if (pedalIndex >= 0) {
                AllPackets.getChannel().sendToServer(
                        new OpenPedalEditPacket(consolePos, pedalIndex));
                return true;
            }
        }

        long key = keyAt(guiX, guiY);
        if (key != -1) {
            if (button == 0) {
                momentaryKey = key;
                updateActive();
                return true;
            } else if (button == 1) {
                if (!latched.remove(key))
                    latched.add(key);
                updateActive();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && momentaryKey != -1) {
            long key = keyAt((int) mouseX - leftPos, (int) mouseY - topPos);
            if (key != momentaryKey) {
                momentaryKey = key;
                updateActive();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && momentaryKey != -1) {
            momentaryKey = -1;
            updateActive();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        clearAll();
        super.onClose();
    }

    @Override
    public void removed() {
        clearAll();
        super.removed();
    }

    // Rendering
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int mc = manualCount();

        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, COLOR_PANEL_BORDER);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, COLOR_PANEL);

        // Pedal widgets (pedalboard mode only)
        if (pedalboardMode) {
            for (int i = 0; i < PedalData.PEDAL_COUNT; i++)
                drawPedalWidget(graphics, i, mouseX, mouseY);
        }

        // Division filter slot background per section
        int groups = OrganConsoleMenu.emitFilterCount(pedalboardMode, mc);
        int filterY = OrganConsoleMenu.filterRowY(pedalboardMode);
        for (int i = 0; i < groups; i++)
            drawSlotBackground(graphics, OrganConsoleMenu.filterBgX(i) + 1, filterY + 1);

        // Keyboards (only shown in pedalboard mode if pedalboard keyboard is enabled)
        if (!pedalboardMode || console.hasPedalboard()) {
            for (int row = 0; row < rowCount(); row++) {
                int section = sectionForRow(row);
                if (section >= 0)
                    drawKeyboard(graphics, row, section);
            }
        }

        // Player inventory slot backgrounds
        int invX = OrganConsoleMenu.playerInvX(pedalboardMode, mc);
        int invY = OrganConsoleMenu.playerInvY(pedalboardMode, mc);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawSlotBackground(graphics, invX + col * 18, invY + row * 18);
        for (int col = 0; col < 9; col++)
            drawSlotBackground(graphics, invX + col * 18, invY + 58);
    }

    private static final int COLOR_PEDAL_BG      = 0xFF2A2A2A;
    private static final int COLOR_PEDAL_BORDER   = 0xFF555555;
    private static final int COLOR_PEDAL_HOVER    = 0xFF444444;
    private static final int COLOR_PEDAL_NAME     = 0xFFCCCCCC;
    private static final int COLOR_PEDAL_POS      = 0xFFFFFFFF;
    private static final int COLOR_BTN_BG         = 0xFF404040;
    private static final int COLOR_BTN_HOVER      = 0xFF606060;

    private void drawPedalWidget(GuiGraphics graphics, int index, int mouseX, int mouseY) {
        int wx = leftPos + OrganConsoleMenu.pedalX(index);
        int wy = topPos + OrganConsoleMenu.PEDAL_AREA_TOP;
        int ww = OrganConsoleMenu.PEDAL_W;
        int wh = OrganConsoleMenu.PEDAL_H;

        boolean hovered = mouseX >= wx && mouseX < wx + ww && mouseY >= wy && mouseY < wy + wh;

        graphics.fill(wx, wy, wx + ww, wy + wh, hovered ? COLOR_PEDAL_HOVER : COLOR_PEDAL_BG);
        graphics.fill(wx, wy, wx + ww, wy + 1, COLOR_PEDAL_BORDER);
        graphics.fill(wx, wy + wh - 1, wx + ww, wy + wh, COLOR_PEDAL_BORDER);
        graphics.fill(wx, wy, wx + 1, wy + wh, COLOR_PEDAL_BORDER);
        graphics.fill(wx + ww - 1, wy, wx + ww, wy + wh, COLOR_PEDAL_BORDER);

        PedalData.Pedal pedal = console.getPedalData().getPedal(index);
        String name = pedal.name.isEmpty()
                ? (pedal.type == PedalData.PedalType.CRESCENDO ? "Crescendo" : "Swell " + (index + 1))
                : pedal.name;

        // Name (truncated)
        String truncName = font.width(name) > ww - 8 ? font.plainSubstrByWidth(name, ww - 10) + "…" : name;
        graphics.drawString(font, truncName, wx + 4, wy + 4, COLOR_PEDAL_NAME, false);

        // Position: - [pos] +
        int pos = pedal.position;
        int btnY = wy + wh - 14;
        int btnW = 10;

        // "-" button
        boolean minusHov = mouseX >= wx + 2 && mouseX < wx + 2 + btnW && mouseY >= btnY && mouseY < btnY + 10;
        graphics.fill(wx + 2, btnY, wx + 2 + btnW, btnY + 10, minusHov ? COLOR_BTN_HOVER : COLOR_BTN_BG);
        graphics.drawString(font, "-", wx + 4, btnY + 1, COLOR_PEDAL_POS, false);

        // Position number
        String posStr = String.valueOf(pos);
        int posX = wx + 14;
        graphics.drawString(font, posStr, posX, btnY + 1, COLOR_PEDAL_POS, false);

        // "+" button
        int plusX = wx + ww - 2 - btnW;
        boolean plusHov = mouseX >= plusX && mouseX < plusX + btnW && mouseY >= btnY && mouseY < btnY + 10;
        graphics.fill(plusX, btnY, plusX + btnW, btnY + 10, plusHov ? COLOR_BTN_HOVER : COLOR_BTN_BG);
        graphics.drawString(font, "+", plusX + 2, btnY + 1, COLOR_PEDAL_POS, false);
    }

    /** Returns the pedal index if the click is inside a pedal widget body (excluding +/- buttons), else -1. */
    private int pedalAt(int guiX, int guiY) {
        if (!pedalboardMode) return -1;
        for (int i = 0; i < PedalData.PEDAL_COUNT; i++) {
            int wx = OrganConsoleMenu.pedalX(i);
            int wy = OrganConsoleMenu.PEDAL_AREA_TOP;
            if (guiX >= wx && guiX < wx + OrganConsoleMenu.PEDAL_W
                    && guiY >= wy && guiY < wy + OrganConsoleMenu.PEDAL_H)
                return i;
        }
        return -1;
    }

    /** Returns +1 or -1 if the click is on a +/- button for a pedal, 0 otherwise. */
    private int pedalButtonAt(int guiX, int guiY) {
        if (!pedalboardMode) return 0;
        for (int i = 0; i < PedalData.PEDAL_COUNT; i++) {
            int wx = OrganConsoleMenu.pedalX(i);
            int wy = OrganConsoleMenu.PEDAL_AREA_TOP;
            int wh = OrganConsoleMenu.PEDAL_H;
            int btnY = wy + wh - 14;
            if (guiY < btnY || guiY >= btnY + 10) continue;
            // "-" button
            if (guiX >= wx + 2 && guiX < wx + 12) return -(i + 1) * 10; // encode pedal index as negative
            // "+" button
            int plusX = wx + OrganConsoleMenu.PEDAL_W - 12;
            if (guiX >= plusX && guiX < plusX + 10) return (i + 1) * 10; // positive = plus
        }
        return 0;
    }

    private void drawKeyboard(GuiGraphics graphics, int row, int section) {
        int keyW = OrganConsoleMenu.keyWidth(pedalboardMode);
        int keyCount = OrganConsoleMenu.keyCountFor(pedalboardMode);
        int x0 = leftPos + OrganConsoleMenu.KEYBOARD_X;
        int y0 = topPos + OrganConsoleMenu.rowTop(pedalboardMode, row);
        int low = OrganConsoleBlockEntity.sectionLowPitch(section);
        int h = OrganConsoleMenu.KEY_H;

        for (int key = 0; key < keyCount; key++) {
            int kx = x0 + key * keyW;
            int pitch = low + key;
            int pc = pitch % 12;
            boolean sharp = pc == 1 || pc == 3 || pc == 6 || pc == 8 || pc == 10;

            boolean locallyOn = active.contains(encode(section, key));
            boolean received = console.isReceivedPitch(section, pitch);

            int color;
            if (locallyOn)
                color = COLOR_PRESSED;
            else if (received)
                color = COLOR_RECEIVED;
            else
                color = sharp ? COLOR_SHARP : COLOR_NATURAL;

            graphics.fill(kx, y0, kx + keyW - 1, y0 + h, color);
            graphics.fill(kx + keyW - 1, y0, kx + keyW, y0 + h, COLOR_KEY_BORDER);
        }
        graphics.fill(x0, y0 - 1, x0 + keyCount * keyW, y0, COLOR_KEY_BORDER);
        graphics.fill(x0, y0 + h, x0 + keyCount * keyW, y0 + h + 1, COLOR_KEY_BORDER);
        graphics.fill(x0 - 1, y0 - 1, x0, y0 + h + 1, COLOR_KEY_BORDER);
    }

    private void drawSlotBackground(GuiGraphics graphics, int guiX, int guiY) {
        int x = leftPos + guiX;
        int y = topPos + guiY;
        graphics.fill(x - 1, y - 1, x + 17, y + 17, COLOR_SLOT_BORDER);
        graphics.fill(x, y, x + 16, y + 16, COLOR_SLOT_INNER);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, OrganConsoleMenu.FILTERS_X, OrganConsoleMenu.TITLE_Y, 0xFFFFFF, false);
    }

    // Filter tooltips
    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        if (hoveredSlot instanceof SlotItemHandler sih && sih.getItemHandler() == menu.ghostInventory) {
            graphics.renderTooltip(font, filterRoleLabel(sih.getSlotIndex()), x, y);
            return;
        }
        super.renderTooltip(graphics, x, y);
    }

    private static Component filterRoleLabel(int section) {
        return section == OrganConsoleBlockEntity.PEDAL_SECTION
                ? Component.translatable("gui.pipeorgans.organ_console.pedalboard_division")
                : Component.translatable("gui.pipeorgans.organ_console.manual_division", section + 1);
    }
}
