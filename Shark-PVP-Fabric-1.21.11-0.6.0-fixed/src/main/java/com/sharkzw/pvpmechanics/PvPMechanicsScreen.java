package com.sharkzw.pvpmechanics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.util.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class PvPMechanicsScreen extends Screen {
    private static final int BG = 0xF20A0D12;
    private static final int PANEL = 0xEA141922;
    private static final int PANEL_2 = 0xE61C2430;
    private static final int BORDER = 0xFF293241;
    private static final int TEXT = 0xFFE8EDF5;
    private static final int MUTED = 0xFF8E99A8;
    private static final int ACCENT = 0xFF3D8DFF;
    private static final int ACCENT_DARK = 0xFF245B9F;
    private static final int HOVER = 0xEE1C2A3A;

    private final Screen parent;
    private int selectedGamemode;
    private int selectedMechanic = -1;
    private int scroll;
    private boolean settings;
    private boolean reducedMotion;
    private boolean compactCards;
    private boolean savedOnly;
    private boolean allMechanics;
    private boolean recentOnly;
    private boolean resourcePacks;
    private EditBox resourcePackSearch;
    private String resourcePackStatus = "";
    private boolean resourcePackBusy;
    private Button resourcePackSearchButton;
    private Button resourcePackDownloadButton;
    private PacksMcApi.Page packsPage = new PacksMcApi.Page(List.of(), "");
    private String selectedPackId = "";
    private PacksMcApi.Pack selectedPack;
    private int selectedStep = 0;
    private double previewTime = 0.0;
    private boolean previewPlaying;
    private EditBox search;

    private record Gamemode(String id, String name, String description, String icon, List<Mechanic> mechanics) {}
    private record Mechanic(String name, String difficulty, String description, String[] tags, String[] steps, String[] tips) {}
    private record MechanicRef(Gamemode gamemode, Mechanic mechanic, String id) {}

    private static final List<Gamemode> MODES = List.of(
            new Gamemode("sword", "Sword", "Spacing, movement, sprint resets and combo fundamentals.", "S", List.of(
                    new Mechanic("W-Tap", "Intermediate", "Study sprint-reset timing and movement control during melee exchanges.", new String[]{"Movement", "Timing"},
                            new String[]{"Approach while maintaining spacing.", "Attack during the demonstrated timing window.", "Practice the sprint-reset input manually.", "Recover and prepare for the next exchange."},
                            new String[]{"Watch spacing before speed.", "Keep your movement controlled.", "Practice timing consistently."}),
                    new Mechanic("3-Block Hit", "Intermediate", "Practice spacing and attack timing around the three-block range.", new String[]{"Spacing", "Timing"},
                            new String[]{"Establish the desired spacing.", "Approach without losing control.", "Align the crosshair with the target.", "Attack during the timing window.", "Recover and maintain spacing."},
                            new String[]{"Maintain consistent spacing.", "Watch target movement.", "Practice timing before speed."}),
                    new Mechanic("Strafing", "Beginner", "Learn controlled lateral movement while tracking a target.", new String[]{"Movement", "Aim"},
                            new String[]{"Start moving while maintaining aim.", "Change direction around the target.", "Maintain tracking while moving."},
                            new String[]{"Avoid predictable movement.", "Keep movement controlled."})
            )),
            new Gamemode("axe", "Axe", "Spacing, timing and defensive shield interactions.", "A", List.of(
                    new Mechanic("Shield Pressure", "Intermediate", "Study attack timing and positioning around defensive play.", new String[]{"Shield", "Timing"},
                            new String[]{"Establish appropriate distance.", "Observe the target's defensive state.", "Choose an appropriate attack window."},
                            new String[]{"Watch the target instead of using a fixed rhythm.", "Maintain spacing."})
            )),
            new Gamemode("crystal", "Crystal", "Positioning, placement concepts and timing.", "C", List.of(
                    new Mechanic("Crystal Placement", "Advanced", "Study positioning and timing around crystal placement.", new String[]{"Placement", "Timing"},
                            new String[]{"Choose an appropriate position.", "Study the placement interaction.", "Understand the timing relationship."},
                            new String[]{"Learn positioning before speed.", "Study timing carefully."})
            )),
            new Gamemode("mace", "Mace", "Vertical positioning, fall timing and hit-window concepts.", "M", List.of())
    );

    public PvPMechanicsScreen(Screen parent) {
        super(Component.translatable("pvpmechanics.title"));
        this.parent = parent;
        this.compactCards = PvPMechanicsConfig.compactCards;
        this.reducedMotion = PvPMechanicsConfig.reducedMotion;
        for (int i = 0; i < MODES.size(); i++) {
            if (MODES.get(i).id.equals(PvPMechanicsConfig.lastGamemode)) {
                selectedGamemode = i;
                break;
            }
        }
    }

    @Override
    protected void init() {
        clearWidgets();
        search = new EditBox(font, 158, 58, Math.min(300, Math.max(160, width / 3)), 20, Component.literal("Search"));
        search.setHint(Component.literal("Search mechanics..."));
        search.setMaxLength(64);
        search.setValue(PvPMechanicsConfig.lastSearch);
        search.setVisible(!settings && !resourcePacks && selectedMechanic < 0);
        addRenderableWidget(search);

        resourcePackSearch = new EditBox(font, 168, 108, Math.min(360, width - 300), 20, Component.literal("Search PacksMC"));
        resourcePackSearch.setHint(Component.literal("Search packs..."));
        resourcePackSearch.setMaxLength(96);
        resourcePackSearch.setVisible(resourcePacks);
        addRenderableWidget(resourcePackSearch);

        resourcePackSearchButton = Button.builder(Component.literal("Search"), b -> searchPacks())
                .bounds(536, 108, 70, 20).build();
        resourcePackSearchButton.visible = resourcePacks;
        addRenderableWidget(resourcePackSearchButton);
        resourcePackDownloadButton = Button.builder(Component.literal("Download"), b -> downloadSelectedPack())
                .bounds(614, 108, 92, 20).build();
        resourcePackDownloadButton.visible = resourcePacks;
        addRenderableWidget(resourcePackDownloadButton);

        int bottom = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Controls"), b ->
                Minecraft.getInstance().setScreen(new ControlsScreen(this, Minecraft.getInstance().options)))
                .bounds(width - 118, bottom, 52, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width - 61, bottom, 52, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (previewPlaying && selectedMechanic >= 0 && !reducedMotion) {
            previewTime += delta;
            if (previewTime > Math.max(4.0, currentMechanic().steps.length * 1.2)) { previewTime=0.0; selectedStep=0; }
            else selectedStep=Math.min(currentMechanic().steps.length-1, (int)(previewTime/1.2));
        }
        g.fill(0, 0, width, height, BG);
        drawHeader(g);
        drawSidebar(g, mouseX, mouseY);
        if (settings) drawSettings(g, mouseX, mouseY);
        else if (resourcePacks) drawResourcePacks(g, mouseX, mouseY);
        else if (selectedMechanic >= 0) drawMechanic(g, mouseX, mouseY);
        else drawContent(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);
    }

    private void drawHeader(GuiGraphics g) {
        g.fill(0, 0, width, 48, PANEL);
        g.fill(0, 47, width, 48, BORDER);
        g.drawString(font, "SharkzW", 18, 10, TEXT, false);
        g.drawString(font, "PvP Mechanics", 18, 25, MUTED, false);
        if (!settings && selectedMechanic < 0) {
            String hint = "Right Shift  •  / Search  •  F Saved";
            g.drawString(font, hint, 18, 37, 0xFF687383, false);
        }
        String title = settings ? "Settings" : resourcePacks ? "Resource Packs" : selectedMechanic >= 0 ? "Mechanic" : MODES.get(selectedGamemode).name();
        g.drawString(font, title, width - 18 - font.width(title), 18, MUTED, false);
    }

    private void drawSidebar(GuiGraphics g, int mouseX, int mouseY) {
        int w = 148, y = 48;
        g.fill(0, y, w, height - 1, PANEL);
        g.fill(w - 1, y, w, height - 1, BORDER);
        g.drawString(font, "LIBRARY", 16, 62, ACCENT, false);

        navRow(g, mouseX, mouseY, 75, "⌂", "Overview", !settings && selectedMechanic < 0 && !allMechanics && !recentOnly && !savedOnly);
        navRow(g, mouseX, mouseY, 99, "A", "All Mechanics", !settings && selectedMechanic < 0 && allMechanics);
        navRow(g, mouseX, mouseY, 123, "R", "Recent", !settings && selectedMechanic < 0 && recentOnly);
        navRow(g, mouseX, mouseY, 147, "★", "Saved", !settings && selectedMechanic < 0 && savedOnly);
        navRow(g, mouseX, mouseY, 171, "P", "Resource Packs", resourcePacks);

        g.drawString(font, "GAMEMODES", 16, 208, ACCENT, false);
        for (int i = 0; i < MODES.size(); i++) {
            int yy = 221 + i * 24;
            boolean active = !settings && selectedMechanic < 0 && !allMechanics && !recentOnly && !savedOnly && selectedGamemode == i;
            boolean hover = mouseX >= 8 && mouseX < w - 8 && mouseY >= yy - 4 && mouseY <= yy + 18;
            if (active || hover) g.fill(8, yy - 4, w - 8, yy + 18, active ? HOVER : 0xA8222D3B);
            if (active) g.fill(8, yy - 4, 10, yy + 18, ACCENT);
            g.fill(16, yy, 30, yy + 14, active ? ACCENT : 0xFF303946);
            g.drawCenteredString(font, Component.literal(MODES.get(i).icon), 23, yy + 3, active ? BG : TEXT);
            g.drawString(font, MODES.get(i).name, 39, yy + 3, active ? TEXT : MUTED, false);
        }
        int sy = height - 72;
        navRow(g, mouseX, mouseY, sy, "S", "Settings", settings);
    }

    private void navRow(GuiGraphics g, int mouseX, int mouseY, int yy, String icon, String label, boolean active) {
        boolean hover = mouseX >= 8 && mouseX < 140 && mouseY >= yy - 4 && mouseY <= yy + 18;
        if (active || hover) g.fill(8, yy - 4, 140, yy + 18, active ? HOVER : 0xA8222D3B);
        if (active) g.fill(8, yy - 4, 10, yy + 18, ACCENT);
        g.fill(16, yy, 30, yy + 14, active ? ACCENT : 0xFF303946);
        g.drawCenteredString(font, Component.literal(icon), 23, yy + 3, active ? BG : TEXT);
        g.drawString(font, label, 39, yy + 3, active ? TEXT : MUTED, false);
    }

    private void drawContent(GuiGraphics g, int mouseX, int mouseY) {
        Gamemode mode = MODES.get(selectedGamemode);
        int left = 168;
        String heading = savedOnly ? "Saved Mechanics" : recentOnly ? "Recent Mechanics" : allMechanics ? "All Mechanics" : mode.name;
        String subheading = savedOnly ? "Your bookmarked mechanics." : recentOnly ? "Mechanics you opened recently." : allMechanics ? "Browse the complete local mechanics library." : mode.description;
        g.drawString(font, heading, left, 92, TEXT, false);
        drawWrapped(g, subheading, left, 107, Math.min(600, width-left-24), MUTED);
        if (!savedOnly && !recentOnly && !allMechanics) {
            drawFilter(g, left, 126, "ALL", true);
        }
        String query = search == null ? "" : search.getValue().trim().toLowerCase();
        List<MechanicRef> pool = new java.util.ArrayList<>();
        for (Gamemode gm : MODES) for (Mechanic m : gm.mechanics) {
            String id = mechanicId(gm, m);
            if (allMechanics || recentOnly || savedOnly) pool.add(new MechanicRef(gm, m, id));
            else if (gm == mode) pool.add(new MechanicRef(gm, m, id));
        }
        if (recentOnly) pool.removeIf(r -> !PvPMechanicsConfig.recent.contains(r.id));
        if (savedOnly) pool.removeIf(r -> !PvPMechanicsConfig.favorites.contains(r.id));
        List<MechanicRef> visible = pool.stream().filter(r -> {
            Mechanic m=r.mechanic;
            return query.isEmpty() || m.name.toLowerCase().contains(query) || m.description.toLowerCase().contains(query) || List.of(m.tags).stream().anyMatch(t -> t.toLowerCase().contains(query)) || r.gamemode.name.toLowerCase().contains(query);
        }).toList();
        int favoriteCount = (int) visible.stream().filter(r -> PvPMechanicsConfig.favorites.contains(r.id)).count();
        g.drawString(font, visible.size() + " mechanics", left, 148, MUTED, false);
        if (favoriteCount > 0) {
            String fav = favoriteCount + " saved";
            g.drawString(font, fav, left + 78, 148, ACCENT, false);
        }
        if (visible.isEmpty()) {
            g.drawString(font, "No mechanics match your search.", left, 160, TEXT, false);
            g.drawString(font, "Try a different name or tag.", left, 176, MUTED, false);
            return;
        }
        int top = 164 - scroll;
        int cardW = Math.max(300, Math.min(520, width - left - 270));
        if (left + cardW > width - 24) cardW = width - left - 24;
        int cardH = compactCards ? 68 : 84;
        for (int i = 0; i < visible.size(); i++) {
            MechanicRef ref = visible.get(i);
            Mechanic m = ref.mechanic;
            int y = top + i * (cardH + 10);
            if (y < 154 || y > height - 42) continue;
            boolean hover = mouseX >= left && mouseX <= left + cardW && mouseY >= y && mouseY <= y + cardH;
            boolean favorite = PvPMechanicsConfig.favorites.contains(ref.id);
            g.fill(left, y, left + cardW, y + cardH, hover ? HOVER : PANEL);
            g.fill(left, y, left + 3, y + cardH, i == 0 ? ACCENT : ACCENT_DARK);
            g.drawString(font, m.name, left + 16, y + 11, TEXT, false);
            if (allMechanics || recentOnly || savedOnly) g.drawString(font, ref.gamemode.name, left + 16, y + 25, ACCENT, false);
            if (favorite) g.drawString(font, "★", left + cardW - 12, y + 10, ACCENT, false);
            g.drawString(font, m.difficulty, left + cardW - font.width(m.difficulty) - 14, y + 11, difficultyColor(m.difficulty), false);
            if (!compactCards) drawWrapped(g, m.description, left + 16, y + 39, cardW - 32, MUTED);
            int ty = compactCards ? y + 39 : y + 69;
            int tx = left + 16;
            for (String tag : m.tags) {
                int tw = font.width(tag) + 12;
                if (tx + tw > left + cardW - 12) break;
                g.fill(tx, ty, tx + tw, ty + 14, PANEL_2);
                g.drawString(font, tag, tx + 6, ty + 3, MUTED, false);
                tx += tw + 6;
            }
            g.fill(left, y + cardH - 1, left + cardW, y + cardH, BORDER);
        }
    }

    private void drawFilter(GuiGraphics g, int x, int y, String label, boolean active) {
        int w = label.equals("SAVED") ? 48 : 46;
        g.fill(x, y, x + w, y + 18, active ? ACCENT_DARK : PANEL_2);
        g.drawCenteredString(font, Component.literal(label), x + w / 2, y + 5, active ? TEXT : MUTED);
    }

    private void drawMechanic(GuiGraphics g, int mouseX, int mouseY) {
        Mechanic m = currentMechanic();
        int x = 168;
        addBackHint(g, x, 66);
        g.drawString(font, m.name, x, 91, TEXT, false);
        g.drawString(font, m.difficulty, x, 107, difficultyColor(m.difficulty), false);
        String favoriteLabel = PvPMechanicsConfig.favorites.contains(mechanicId(MODES.get(selectedGamemode), m)) ? "★ Saved" : "☆ Save";
        g.drawString(font, favoriteLabel, x + 100, 107, ACCENT, false);
        drawWrapped(g, m.description, x, 125, Math.min(560, width - x - 30), MUTED);
        int panelW = Math.min(560, width - x - 30);
        int y = 160;
        section(g, x, y, panelW, "STEPS");
        y += 28;
        for (int i = 0; i < m.steps.length; i++) {
            g.fill(x, y, x + panelW, y + 34, PANEL);
            g.drawString(font, String.valueOf(i + 1), x + 12, y + 11, ACCENT, false);
            drawWrapped(g, m.steps[i], x + 34, y + 7, panelW - 48, TEXT);
            y += 42;
        }
        section(g, x, y + 4, panelW, "TIPS");
        y += 32;
        for (String tip : m.tips) {
            drawWrapped(g, "• " + tip, x + 4, y, panelW - 8, MUTED);
            y += 18;
        }
        if (x + panelW + 20 < width) {
            int rx = x + panelW + 20;
            g.fill(rx, 160, width - 16, height - 42, PANEL);
            g.drawString(font, "PREVIEW", rx + 14, 176, ACCENT, false);
            g.fill(rx + 14, 192, width - 30, 310, 0xFF0C1118);
            int px1=rx+28, px2=width-44, py=252;
            g.fill(px1, py, px2, py+2, BORDER);
            double duration=Math.max(4.0, m.steps.length*1.2);
            double normalized=Math.min(1.0, previewTime/duration);
            g.fill(px1, py, px1+(int)((px2-px1)*normalized), py+2, ACCENT);
            int markerX=px1+(int)((px2-px1)*(Math.min(1.0, selectedStep/Math.max(1.0,m.steps.length-1.0))));
            g.fill(markerX-2, py-7, markerX+2, py+7, ACCENT);
            g.drawCenteredString(font, Component.literal(previewPlaying ? "Playing preview" : "Preview ready"), (px1+px2)/2, 216, MUTED);
            g.drawString(font, "[SPACE] Play/Pause", rx+14, 275, TEXT, false);
            g.drawString(font, "[←/→] Step", rx+14, 291, MUTED, false);
            g.drawString(font, String.format(java.util.Locale.ROOT, "%.1fs / %.1fs", previewTime, duration), px2-font.width(String.format(java.util.Locale.ROOT, "%.1fs / %.1fs", previewTime, duration)), 275, MUTED, false);
            g.drawString(font, "TRAINING", rx + 14, 335, ACCENT, false);
            drawWrapped(g, "Practice scenarios are linked to mechanics through the content data.", rx + 14, 353, width - rx - 44, MUTED);
        }
    }

    private void drawResourcePacks(GuiGraphics g, int mouseX, int mouseY) {
        int x = 168;
        int right = width - 24;
        g.drawString(font, "Resource Packs", x, 67, ACCENT, false);
        g.drawString(font, "Browse the PacksMC catalog from inside Shark PVP.", x, 84, MUTED, false);
        g.drawString(font, "Pack metadata, previews and creator information stay in the client. Official downloads still follow PacksMC's download rules.", x, 96, MUTED, false);

        int y = 150;
        if (packsPage.packs().isEmpty()) {
            g.drawString(font, "No packs loaded yet. Search the catalog to begin.", x, y, TEXT, false);
        } else {
            for (int i = 0; i < packsPage.packs().size(); i++) {
                PacksMcApi.Pack pack = packsPage.packs().get(i);
                int rowY = y + i * 42;
                if (rowY > height - 60) break;
                boolean selected = pack.id().equals(selectedPackId);
                g.fill(x, rowY, right, rowY + 36, selected ? HOVER : PANEL);
                g.fill(x, rowY, x + 3, rowY + 36, selected ? ACCENT : ACCENT_DARK);
                g.drawString(font, pack.name(), x + 12, rowY + 6, TEXT, false);
                String meta = pack.resolution() + "  •  " + pack.author() + "  •  " + pack.downloads() + " downloads";
                g.drawString(font, meta, x + 12, rowY + 21, MUTED, false);
                if (pack.verified()) g.drawString(font, "✓", right - 14, rowY + 8, ACCENT, false);
            }
        }
        if (selectedPack != null) {
            int detailX = Math.max(x + 360, right - 300);
            g.fill(detailX, 150, right, height - 52, PANEL_2);
            g.drawString(font, selectedPack.name(), detailX + 12, 164, TEXT, false);
            g.drawString(font, "by " + selectedPack.author(), detailX + 12, 181, MUTED, false);
            drawWrapped(g, selectedPack.description(), detailX + 12, 201, right - detailX - 24, MUTED);
            g.drawString(font, selectedPack.resolution() + "  •  " + selectedPack.downloads() + " downloads", detailX + 12, height - 88, MUTED, false);
            g.drawString(font, "Download is handled by the official PacksMC flow.", detailX + 12, height - 70, ACCENT, false);
        }
        if (!resourcePackStatus.isBlank()) g.drawString(font, resourcePackStatus, x, height - 36, resourcePackStatus.startsWith("Ready") ? 0xFF7FD6A4 : 0xFFFFA33D, false);
    }

    private void searchPacks() {
        String key = PvPMechanicsConfig.packsMcApiKey;
        if (key == null || key.isBlank()) {
            resourcePackStatus = "Add your PacksMC API key in Shark PVP settings.";
            return;
        }
        resourcePackStatus = "Searching PacksMC...";
        String q = resourcePackSearch == null ? "" : resourcePackSearch.getValue().trim();
        PacksMcApi.list(key, q, "", "downloads", 20, "").whenComplete((page, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
                resourcePackStatus = "PacksMC search failed: " + cause.getMessage();
                return;
            }
            packsPage = page;
            selectedPackId = "";
            selectedPack = null;
            resourcePackStatus = "Ready — " + page.packs().size() + " packs loaded.";
        }));
    }

    private void downloadSelectedPack() {
        if (selectedPack == null) {
            resourcePackStatus = "Select a pack first.";
            return;
        }
        // PacksMC intentionally exposes a web download page rather than a raw file URL.
        // Shark PVP does not bypass that protection or re-host creator files.
        Util.getPlatform().openUri(selectedPack.downloadUrl());
        resourcePackStatus = "Opened the official PacksMC download flow for " + selectedPack.name() + ".";
    }

    private void drawSettings(GuiGraphics g, int mouseX, int mouseY) {
        int x = 168, w = Math.min(560, width - x - 24);
        g.drawString(font, "Settings", x, 67, ACCENT, false);
        g.drawString(font, "Client-side presentation and input preferences.", x, 83, MUTED, false);
        settingRow(g, x, 112, w, "Open menu key", PvPMechanicsClient.OPEN_MENU.getTranslatedKeyMessage().getString(), "Change through Controls");
        settingRow(g, x, 170, w, "Compact cards", compactCards ? "ON" : "OFF", "Use shorter mechanic cards");
        settingRow(g, x, 228, w, "Reduced motion", reducedMotion ? "ON" : "OFF", "Keeps transitions and effects minimal");
        settingRow(g, x, 286, w, "Theme", "Blue / Black / Gray", "Low-contrast transparent panels");
        settingRow(g, x, 344, w, "Data", "Local content", "No network activity required");
        g.drawString(font, "Click a toggle row to change it.", x, 412, MUTED, false);
        g.drawString(font, "Settings are saved automatically.", x, 428, 0xFF687383, false);
    }

    private void settingRow(GuiGraphics g, int x, int y, int w, String label, String value, String sub) {
        g.fill(x, y, x + w, y + 46, PANEL);
        g.drawString(font, label, x + 14, y + 9, TEXT, false);
        g.drawString(font, value, x + w - font.width(value) - 14, y + 9, ACCENT, false);
        g.drawString(font, sub, x + 14, y + 27, MUTED, false);
    }

    private void section(GuiGraphics g, int x, int y, int w, String title) {
        g.drawString(font, title, x, y, ACCENT, false);
        g.fill(x, y + 12, x + w, y + 13, BORDER);
    }

    private void addBackHint(GuiGraphics g, int x, int y) {
        g.drawString(font, "ESC  Back to mechanics", x, y, MUTED, false);
    }

    private Mechanic currentMechanic() {
        return MODES.get(selectedGamemode).mechanics.get(selectedMechanic);
    }

    private void drawWrapped(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 20) return;
        String[] words = text.split(" ");
        String line = "";
        int yy = y;
        for (String word : words) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (font.width(next) > maxWidth && !line.isEmpty()) {
                g.drawString(font, line, x, yy, color, false);
                yy += 10;
                line = word;
            } else line = next;
        }
        if (!line.isEmpty()) g.drawString(font, line, x, yy, color, false);
    }

    private int difficultyColor(String difficulty) {
        return switch (difficulty) {
            case "Beginner" -> 0xFF9AD18B;
            case "Intermediate" -> 0xFFE0C56E;
            case "Advanced" -> 0xFFE79A72;
            default -> 0xFFE67EAE;
        };
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && search != null && search.isFocused() && selectedMechanic < 0) {
            search.setFocused(false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && selectedMechanic >= 0) {
            selectedMechanic = -1;
            scroll = 0;
            if (search != null) search.setVisible(!settings);
            return true;
        }
        if (!settings && selectedMechanic < 0 && keyCode == GLFW.GLFW_KEY_SLASH && search != null) {
            search.setFocused(true);
            return true;
        }
        if (!settings && selectedMechanic >= 0 && keyCode == GLFW.GLFW_KEY_F) {
            toggleFavorite(currentMechanic());
            return true;
        }
        if (!settings && selectedMechanic >= 0 && keyCode == GLFW.GLFW_KEY_SPACE) {
            previewPlaying = !previewPlaying; return true;
        }
        if (!settings && selectedMechanic >= 0 && (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT)) {
            Mechanic m=currentMechanic();
            selectedStep = Math.max(0, Math.min(m.steps.length-1, selectedStep + (keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : -1)));
            previewTime = selectedStep * 1.2; return true;
        }
        if (!settings && selectedMechanic < 0 && keyCode == GLFW.GLFW_KEY_F) {
            savedOnly = !savedOnly;
            scroll = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME && !settings) {
            selectedMechanic = -1;
            resourcePacks = false;
            scroll = 0;
            if (search != null) search.setVisible(true);
            if (resourcePackSearch != null) resourcePackSearch.setVisible(false);
            if (resourcePackSearchButton != null) resourcePackSearchButton.visible=false;
            if (resourcePackDownloadButton != null) resourcePackDownloadButton.visible=false;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button != 0) return super.mouseClicked(event, doubleClick);
        int sy = height - 72;
        if (mouseX >= 8 && mouseX <= 140 && mouseY >= sy - 4 && mouseY <= sy + 18) {
            settings = true; resourcePacks=false; allMechanics=false; recentOnly=false; savedOnly=false; selectedMechanic=-1; scroll=0;
            if (search != null) search.setVisible(false);
            if (resourcePackSearch != null) resourcePackSearch.setVisible(false);
            if (resourcePackSearchButton != null) resourcePackSearchButton.visible=false;
            if (resourcePackDownloadButton != null) resourcePackDownloadButton.visible=false;
            return true;
        }
        if (!settings && !resourcePacks && selectedMechanic < 0 && mouseX >= 8 && mouseX <= 140) {
            if (mouseY >= 71 && mouseY < 95) { allMechanics=false; recentOnly=false; savedOnly=false; scroll=0; return true; }
            if (mouseY >= 95 && mouseY < 119) { allMechanics=true; recentOnly=false; savedOnly=false; scroll=0; return true; }
            if (mouseY >= 119 && mouseY < 143) { allMechanics=false; recentOnly=true; savedOnly=false; scroll=0; return true; }
            if (mouseY >= 143 && mouseY < 167) { allMechanics=false; recentOnly=false; savedOnly=true; scroll=0; return true; }
            if (mouseY >= 167 && mouseY < 191) { resourcePacks=true; allMechanics=false; recentOnly=false; savedOnly=false; selectedMechanic=-1; scroll=0; if (search != null) search.setVisible(false); if (resourcePackSearch != null) resourcePackSearch.setVisible(true); if (resourcePackSearchButton != null) resourcePackSearchButton.visible=true; if (resourcePackDownloadButton != null) resourcePackDownloadButton.visible=true; searchPacks(); return true; }
        }
        if (!settings && resourcePacks && mouseX >= 8 && mouseX <= 140 && mouseY >= 167 && mouseY < 191) return true;
        if (!settings && !resourcePacks && selectedMechanic < 0 && mouseX < 148 && mouseY >= 217 && mouseY < 221 + MODES.size()*24) {
            int index=(int)((mouseY-217)/24);
            if(index>=0 && index<MODES.size()){ selectedGamemode=index; allMechanics=false; recentOnly=false; savedOnly=false; scroll=0; PvPMechanicsConfig.lastGamemode=MODES.get(index).id; PvPMechanicsConfig.save(); return true; }
        }
        if (!settings && selectedMechanic >= 0 && mouseX >= 260 && mouseX <= 340 && mouseY >= 96 && mouseY <= 116) {
            toggleFavorite(currentMechanic());
            return true;
        }
        if (!settings && selectedMechanic >= 0 && mouseX >= 168 && mouseY >= 188 && mouseY < 188 + currentMechanic().steps.length * 42) {
            int idx=(int)((mouseY-188)/42);
            if(idx>=0 && idx<currentMechanic().steps.length){ selectedStep=idx; previewTime=idx*1.2; return true; }
        }
        if (!settings && selectedMechanic >= 0 && mouseX >= 148 && mouseY >= 52 && mouseY <= 82) {
            selectedMechanic = -1;
            if (search != null) search.setVisible(true);
            if (resourcePackSearch != null) resourcePackSearch.setVisible(false);
            return true;
        }
        if (!settings && resourcePacks && mouseX >= 168 && mouseX <= width - 24 && mouseY >= 150 && mouseY < 150 + Math.min(20, packsPage.packs().size()) * 42) {
            int index = (int)((mouseY - 150) / 42);
            if (index >= 0 && index < packsPage.packs().size()) {
                selectedPack = packsPage.packs().get(index);
                selectedPackId = selectedPack.id();
                resourcePackStatus = "Selected " + selectedPack.name() + ".";
                return true;
            }
        }
        if (settings) {
            int x = 168, w = Math.min(560, width - x - 24);
            if (mouseX >= x && mouseX <= x + w && mouseY >= 170 && mouseY < 216) {
                compactCards = !compactCards;
                PvPMechanicsConfig.compactCards = compactCards;
                PvPMechanicsConfig.save();
                return true;
            }
            if (mouseX >= x && mouseX <= x + w && mouseY >= 228 && mouseY < 274) {
                reducedMotion = !reducedMotion;
                PvPMechanicsConfig.reducedMotion = reducedMotion;
                PvPMechanicsConfig.save();
                return true;
            }
        } else if (selectedMechanic < 0) {
            Gamemode mode = MODES.get(selectedGamemode);
            String query = search == null ? "" : search.getValue().trim().toLowerCase();
            List<MechanicRef> pool = new java.util.ArrayList<>();
            for (Gamemode gm : MODES) for (Mechanic m : gm.mechanics) pool.add(new MechanicRef(gm,m,mechanicId(gm,m)));
            if (!allMechanics && !recentOnly && !savedOnly) pool.removeIf(r -> r.gamemode != mode);
            if (recentOnly) pool.removeIf(r -> !PvPMechanicsConfig.recent.contains(r.id));
            if (savedOnly) pool.removeIf(r -> !PvPMechanicsConfig.favorites.contains(r.id));
            List<MechanicRef> visible = pool.stream().filter(r -> {
                Mechanic m=r.mechanic;
                return query.isEmpty() || m.name.toLowerCase().contains(query) || m.description.toLowerCase().contains(query) || List.of(m.tags).stream().anyMatch(t -> t.toLowerCase().contains(query)) || r.gamemode.name.toLowerCase().contains(query);
            }).toList();
            int left = 168;
            int top = 164 - scroll;
            int cardW = Math.max(300, Math.min(520, width - left - 270));
            if (left + cardW > width - 24) cardW = width - left - 24;
            int cardH = compactCards ? 68 : 84;
            for (int i = 0; i < visible.size(); i++) {
                int y = top + i * (cardH + 10);
                if (mouseX >= left && mouseX <= left + cardW && mouseY >= y && mouseY <= y + cardH) {
                    MechanicRef ref=visible.get(i);
                    selectedGamemode = MODES.indexOf(ref.gamemode);
                    selectedMechanic = ref.gamemode.mechanics.indexOf(ref.mechanic);
                    String openedId = ref.id;
                    PvPMechanicsConfig.recent.remove(openedId);
                    PvPMechanicsConfig.recent.add(openedId);
                    while (PvPMechanicsConfig.recent.size() > 8) {
                        String first = PvPMechanicsConfig.recent.iterator().next();
                        PvPMechanicsConfig.recent.remove(first);
                    }
                    scroll = 0;
                    selectedStep = 0;
                    previewTime = 0.0;
                    previewPlaying = false;
                    PvPMechanicsConfig.lastSearch = query;
                    PvPMechanicsConfig.save();
                    if (search != null) search.setVisible(false);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!settings && selectedMechanic >= 0 && mouseX >= 148) {
            scroll -= (int)(verticalAmount*28); scroll=Math.max(0,Math.min(scroll,240)); return true;
        }
        if (!settings && selectedMechanic < 0 && mouseX >= 148) {
            int count = (allMechanics || recentOnly || savedOnly) ? 12 : MODES.get(selectedGamemode).mechanics.size();
            int cardH = compactCards ? 68 : 84;
            scroll -= (int) (verticalAmount * 28);
            int max = Math.max(0, count * (cardH + 10) - (height - 212));
            scroll = Math.max(0, Math.min(scroll, max));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private String mechanicId(Gamemode mode, Mechanic mechanic) {
        String base = mechanic.name.toLowerCase().replace(" ", "_").replace("-", "_");
        return mode.id + "." + base;
    }

    private void toggleFavorite(Mechanic mechanic) {
        String id = mechanicId(MODES.get(selectedGamemode), mechanic);
        if (!PvPMechanicsConfig.favorites.add(id)) PvPMechanicsConfig.favorites.remove(id);
        PvPMechanicsConfig.save();
    }

    @Override
    public void onClose() {
        if (search != null) PvPMechanicsConfig.lastSearch = search.getValue();
        PvPMechanicsConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }
}
