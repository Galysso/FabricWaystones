package wraith.fwaystones.screen;

import com.glisco.numismaticoverhaul.ModComponents;
import com.glisco.numismaticoverhaul.item.NumismaticOverhaulItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import wraith.fwaystones.FabricWaystones;
import wraith.fwaystones.access.PlayerEntityMixinAccess;
import wraith.fwaystones.block.WaystoneBlockEntity;
import wraith.fwaystones.packets.SyncPlayerFromClientPacket;
import wraith.fwaystones.packets.WaystoneGUISlotClickPacket;
import wraith.fwaystones.util.FWConfigModel;
import wraith.fwaystones.util.NumismaticUtils;
import wraith.fwaystones.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class UniversalWaystoneScreen extends HandledScreen<ScreenHandler> {

    protected final PlayerInventory inventory;
    protected final ArrayList<Button> buttons = new ArrayList<>();
    protected Identifier texture;
    protected float scrollAmount;
    protected boolean mouseClicked;
    protected int scrollOffset;
    protected boolean ignoreTypedCharacter;
    protected boolean mousePressed;
    private TextFieldWidget searchField;
    private final int maxLineNumber = 7;

    public UniversalWaystoneScreen(ScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.inventory = inventory;
        this.backgroundWidth = 234;
        this.backgroundHeight = 176;
        buttons.add(new Button(139, 16, 13, 13, 234, 120) {
            @Override
            public void onClick() {
                if (!isVisible()) {
                    return;
                }
                super.onClick();
                ((UniversalWaystoneScreenHandler) handler).toggleSearchType();
                searchField.setFocused(((PlayerEntityMixinAccess) client.player).fabricWaystones$autofocusWaystoneFields());
            }

            @Override
            public boolean isVisible() {
                return searchVisible();
            }

            @Override
            public boolean hasToolTip() {
                return true;
            }

            @Override
            public Text tooltip() {
                return ((UniversalWaystoneScreenHandler) handler).getSearchTypeTooltip();
            }
        });

        //Autoselect search lock
        buttons.add(new ToggleableButton(24, 17, 8, 11, 234, 33, 242, 33) {
            @Override
            public void setup() {
                this.toggled = ((PlayerEntityMixinAccess) inventory.player).fabricWaystones$autofocusWaystoneFields();
                setupTooltip();
            }

            @Override
            public boolean isVisible() {
                return !(UniversalWaystoneScreen.this instanceof WaystoneBlockScreen waystoneBlockScreen) || waystoneBlockScreen.page == WaystoneBlockScreen.Page.WAYSTONES;
            }

            @Override
            public void onClick() {
                if (!isVisible()) {
                    return;
                }
                super.onClick();
                ((PlayerEntityMixinAccess) inventory.player).fabricWaystones$toggleAutofocusWaystoneFields();
                searchField.setFocused(((PlayerEntityMixinAccess) inventory.player).fabricWaystones$autofocusWaystoneFields());
                setupTooltip();
            }

            private void setupTooltip() {
                this.tooltip = this.toggled
                    ? Text.translatable("fwaystones.config.tooltip.unlock_search")
                    : Text.translatable("fwaystones.config.tooltip.lock_search");
            }
        });

        setupButtons();
    }

    @Override
    public void close() {
        super.close();
        ClientPlayNetworking.send(new SyncPlayerFromClientPacket(((PlayerEntityMixinAccess) inventory.player).fabricWaystones$toTagW(new NbtCompound())));
    }

    protected void setupButtons() {
        for (Button button : buttons) {
            button.setup();
        }
    }

    protected boolean searchVisible() {
        return true;
    }

    @Override
    protected void init() {
        super.init();

        this.searchField = new TextFieldWidget(textRenderer, this.x + 37, this.y + 18, 98, 10, Text.literal("")) {
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                boolean bl = mouseX >= (double) this.getX() && mouseX < (double) (this.getX() + this.width) && mouseY >= (double) this.getY() && mouseY < (double) (this.getY() + this.height);
                if (bl && button == 1) {
                    this.setText("");
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }
        };
        this.searchField.setMaxLength(16);
        this.searchField.setEditableColor(0xFFFFFF);
        this.searchField.setVisible(true);
        this.searchField.setDrawsBackground(false);
        this.searchField.setFocusUnlocked(true);
        this.searchField.setText("");
        this.searchField.setChangedListener((s) -> {
            this.scrollAmount = 0;
            this.scrollOffset = (int) ((double) (this.scrollAmount * (float) this.getMaxScroll()) + 0.5D);
            ((UniversalWaystoneScreenHandler) handler).setFilter(this.searchField != null ? this.searchField.getText() : "");
            ((UniversalWaystoneScreenHandler) handler).filterWaystones();
        });
        this.addSelectableChild(this.searchField);
    }

    @Override
    public void handledScreenTick() {
        if (this.searchField != null && this.searchField.isVisible()) {
//            this.searchField.tick();
            if (((PlayerEntityMixinAccess) client.player).fabricWaystones$autofocusWaystoneFields()) {
                this.searchField.setFocused(true);
            }
        }
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        String string = this.searchField.getText();
        this.init(client, width, height);
        this.searchField.setText(string);
        super.resize(client, width, height);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        context.drawTexture(texture, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight);
        int k = (int) (111.0F * this.scrollAmount);
        context.drawTexture(texture, x + 198, y + 31 + k, 234 + (this.shouldScroll() ? 0 : 11), 0, 11, 15);
        int n = this.scrollOffset + maxLineNumber;
        // TODO: Merge some of these
        this.renderNumismaticBalance(context, this.x + 154, this.y + 5);
        this.renderForgetButtons(context, mouseX, mouseY, this.x + 24, this.y + 36);
        renderButtons(context, mouseX, mouseY);
        this.renderCostItem(context, this.x + 23, this.y + 127);
        this.renderWaystoneTooltips(context, mouseX, mouseY, this.x + 36, this.y + 30, n);
        this.renderLines(context, mouseX, mouseY, this.x + 36, this.y + 30, n);
        this.searchField.render(context, mouseX, mouseY, delta);
        this.renderForgetTooltips(context, mouseX, mouseY, this.x + 24, this.y + 36);
        this.renderButtonTooltips(context, mouseX, mouseY);
        this.renderBalanceTooltip(context, mouseX, mouseY);
    }

    protected void renderLines(DrawContext context, int mouseX, int mouseY, int x, int y, int m) {
        if (FabricWaystones.WAYSTONE_STORAGE == null)
            return;

        // Get waystones
        ArrayList<String> waystones = getDiscoveredWaystones();

        // Get origin hash
        String originHash = "";
        if (this.handler instanceof WaystoneBlockScreenHandler) {
            originHash = ((WaystoneBlockScreenHandler) this.handler).getWaystone();
        }

        // Check whether Numismatic coins should be used
        final boolean useNumismatic = FabricWaystones.CONFIG.teleportation_cost.cost_type().equals(FWConfigModel.CostType.NUMISMATIC);

        // Get player balance
        long playerBalance = Long.MAX_VALUE;
        if (useNumismatic) {
            playerBalance = ModComponents.CURRENCY.get(inventory.player).getValue();
        }

        // Render lines
        for (int n = this.scrollOffset; n < m && n < getDiscoveredCount(); ++n) {
            // Get the destination
            var destinationHash = waystones.get(n);

            // Compute the cost
            int cost = Utils.getCostWrapper(client.player, originHash, destinationHash);

            // Base rendering positions
            int o = n - this.scrollOffset;
            int r = y + o * 18 + 2;
            int s = this.backgroundHeight;

            // Check whether the player has the balance
            final boolean playerHasBalance = playerBalance >= cost;

            // Display background
            if (Objects.equals(originHash, destinationHash) || !playerHasBalance) {
                s += 18;
            } else if (mouseX >= x && mouseY >= r && mouseX < x + 158 && mouseY < r + 18) {
                s += mouseClicked ? 18 : 36;
            }
            context.drawTexture(texture, x, r - 1, 0, s, 158, 18);

            // Display name
            String name = FabricWaystones.WAYSTONE_STORAGE.getName(waystones.get(n));
            context.drawText(textRenderer, name, x + 4, r + 4, 0x161616, false);

            // Skip cost if the waystone is the same as the origin or if Numismatic is not used
            if (!useNumismatic || Objects.equals(originHash, destinationHash)) {
                continue; // Skip if the waystone is the same as the origin
            }

            // Get the cost color based on the player's balance
            var colorCostDigits = playerHasBalance ? 0xEEEEEE : 0xE06666;

            // Get the number of each coins
            NumismaticUtils.CoinsTuple coins = NumismaticUtils.convertCostToCoins(cost);

            // Base rendering positions for costs
            int costHorizontalOffset = 138;
            int costVerticalOffset = -1;
            int costDigitsHorizontalOffset = 5;
            int costDigitsVerticalOffset = 9;

            // Display costs
            if (coins.goldCoins > 0) {
                ItemStack goldStack = new ItemStack(NumismaticOverhaulItems.GOLD_COIN, (int) coins.goldCoins);
                context.drawItem(goldStack, x + costHorizontalOffset - 2*18, r + costVerticalOffset);
                context.getMatrices().push();
                context.getMatrices().translate(0.0, 0.0, 200.0);
                context.drawText(this.textRenderer, Text.literal(Long.toString(coins.goldCoins)), x + costHorizontalOffset + costDigitsHorizontalOffset - 2*18, r + costVerticalOffset + costDigitsVerticalOffset, colorCostDigits, false);
                context.getMatrices().pop();
            }
            if (coins.silverCoins > 0) {
                ItemStack silverStack = new ItemStack(NumismaticOverhaulItems.SILVER_COIN, (int) coins.silverCoins);
                context.drawItem(silverStack, x + costHorizontalOffset - 18, r + costVerticalOffset);
                context.getMatrices().push();
                context.getMatrices().translate(0.0, 0.0, 200.0);
                context.drawText(this.textRenderer, Text.literal(Long.toString(coins.silverCoins)), x + costHorizontalOffset + costDigitsHorizontalOffset - 18, r + costVerticalOffset + costDigitsVerticalOffset, colorCostDigits, false);
                context.getMatrices().pop();
            }
            if (coins.bronzeCoins > 0) {
                ItemStack bronzeStack = new ItemStack(NumismaticOverhaulItems.BRONZE_COIN, (int) coins.bronzeCoins);
                context.drawItem(bronzeStack, x + costHorizontalOffset, r + costVerticalOffset);
                context.getMatrices().push();
                context.getMatrices().translate(0.0, 0.0, 200.0);
                context.drawText(this.textRenderer, Text.literal(Long.toString(coins.bronzeCoins)), x + costHorizontalOffset + costDigitsHorizontalOffset, r + costVerticalOffset + costDigitsVerticalOffset, colorCostDigits, false);
                context.getMatrices().pop();
            }
        }
    }

    protected void renderButtonTooltips(DrawContext context, int mouseX, int mouseY) {
        for (Button button : buttons) {
            if (!button.isVisible() || !button.hasToolTip() || !button.isInBounds(mouseX - this.x, mouseY - this.y)) {
                continue;
            }

            context.drawTooltip(textRenderer, button.tooltip(), mouseX, mouseY);
        }
    }

    protected void renderBalanceTooltip(DrawContext context, int mouseX, int mouseY) {
        if (mouseX >= this.x + 153 && mouseX <= this.x + 209 && mouseY >= this.y + 5 && mouseY <= this.y + 20) {
            context.drawTooltip(textRenderer, Text.translatable("numismatic.current_balance"), mouseX, mouseY);
        }
    }

    private void renderWaystoneAmount(DrawContext context, int x, int y) {
        context.drawText(textRenderer, Text.translatable("fwaystones.gui.displayed_waystones", this.getDiscoveredCount()), x, y, 0x161616, false);
    }

    protected void renderButtons(DrawContext context, int mouseX, int mouseY) {
        for (Button button : buttons) {
            if (!button.isVisible()) {
                continue;
            }
            int u = button.getU();
            int v = button.getV();
            if (button.isInBounds(mouseX - this.x, mouseY - this.y)) {
                v += button.getHeight() * (this.mousePressed ? 1 : 2);
            }
            context.drawTexture(texture, this.x + button.getX(), this.y + button.getY(), u, v, button.getWidth(), button.getHeight());
        }
    }

    @Override
    public boolean charTyped(char chr, int keyCode) {
        if (this.ignoreTypedCharacter) {
            return false;
        } else {
            return this.searchField.charTyped(chr, keyCode);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        this.ignoreTypedCharacter = false;
        if (InputUtil.fromKeyCode(keyCode, scanCode).toInt().isPresent() && this.handleHotbarKeyPressed(keyCode, scanCode)) {
            this.ignoreTypedCharacter = true;
            return true;
        } else {
            if (this.searchField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            } else {
                return this.searchField.isFocused() && this.searchField.isVisible() && keyCode != 256 || super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    protected void renderCostItem(DrawContext context, int x, int y) {
        var config = FabricWaystones.CONFIG.teleportation_cost;
        MutableText text;
        switch (config.cost_type()) {
            case NUMISMATIC -> {
                return;
            }
            case HEALTH -> {
                context.drawTexture(texture, x, y + 4, 243, 15, 9, 9);
                text = Text.translatable("fwaystones.cost.health");
            }
            case HUNGER -> {
                context.drawTexture(texture, x, y + 4, 234, 24, 9, 9);
                text = Text.translatable("fwaystones.cost.hunger");
            }
            case EXPERIENCE -> {
                context.drawTexture(texture, x, y + 4, 234, 15, 9, 9);
                text = Text.translatable("fwaystones.cost.xp");
            }
            case LEVEL -> {
                context.drawItem(new ItemStack(Items.EXPERIENCE_BOTTLE), x - 4, y);
                text = Text.translatable("fwaystones.cost.level");
            }
            case ITEM -> {
                var item = Registries.ITEM.get(Utils.getTeleportCostItem());
                context.drawItem(new ItemStack(item), x - 4, y);
                text = (MutableText) item.getName();
            }
            default -> {
                context.drawTexture(texture, x, y + 4, 243, 24, 9, 9);
                text = Text.translatable("fwaystones.cost.free");
            }
        }

        renderCostText(context, x, y, text);
    }

    protected void renderCostText(DrawContext context, int x, int y, MutableText text) {
        renderCostText(context, x, y, text, 0x161616);
    }

    protected void renderCostText(DrawContext context, int x, int y, MutableText text, int color) {
        if (!FabricWaystones.CONFIG.teleportation_cost.cost_type().equals(FWConfigModel.CostType.NONE)) {
            text = text.append(Text.literal(": " + FabricWaystones.CONFIG.teleportation_cost.base_cost()));
        }
        context.drawText(textRenderer, text, x + 16, y + 5, color, false);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, this.title, 3 + (150 - textRenderer.getWidth(this.title))/2, this.titleY, 4210752, false);
    }


    protected void renderForgetButtons(DrawContext context, int mouseX, int mouseY, int x, int y) {
        int n = getDiscoveredCount();
        for (int i = 0; i < maxLineNumber; ++i) {
            int r = y + i * 18;
            int v = 159;
            if (i >= n) {
                v += 8;
            } else if (mouseX >= x && mouseY >= r && mouseX < x + 8 && mouseY < r + 8) {
                v += 8 * (mouseClicked ? 1 : 2);
            }
            context.drawTexture(texture, x, r, 234, v, 8, 8);
        }
    }

    protected void renderForgetTooltips(DrawContext context, int mouseX, int mouseY, int x, int y) {
        int n = getDiscoveredCount();
        for (int i = 0; i < n; ++i) {
            int r = y + i * 18;
            if (mouseX < x || mouseY < r || mouseX > x + 8 || mouseY >= r + 8) {
                continue;
            }
            context.drawTooltip(textRenderer, Text.translatable("fwaystones.gui.forget_tooltip"), mouseX, mouseY);
        }
    }

    protected void renderWaystoneBackground(DrawContext context, int mouseX, int mouseY, int x, int y, int m) {
        for (int n = this.scrollOffset; n < m && n < getDiscoveredCount(); ++n) {
            int o = n - this.scrollOffset;
            int r = y + o * 18 + 2;
            int s = this.backgroundHeight;
            if (mouseX >= x && mouseY >= r && mouseX < x + 158 && mouseY < r + 18) {
                s += mouseClicked ? 18 : 36;
            }
            context.drawTexture(texture, x, r - 1, 0, s, 158, 18);
        }
    }

    /*
    int costHorizontalOffset = 138;
            int costVerticalOffset = -1;
            int costDigitsHorizontalOffset = 5;
            int costDigitsVerticalOffset = 9;

            // Display costs
            if (coins.goldCoins > 0) {
                ItemStack goldStack = new ItemStack(NumismaticOverhaulItems.GOLD_COIN, (int) coins.goldCoins);
                context.drawItem(goldStack, x + costHorizontalOffset - 2*18, r + costVerticalOffset);
                context.getMatrices().push();
                context.getMatrices().translate(0.0, 0.0, 200.0);
                context.drawText(this.textRenderer, Text.literal(Long.toString(coins.goldCoins)), x + costHorizontalOffset + costDigitsHorizontalOffset - 2*18, r + costVerticalOffset + costDigitsVerticalOffset, colorCostDigits, false);
                context.getMatrices().pop();
            }
     */

    protected void renderNumismaticBalance(DrawContext context, int x, int y) {
        int costDigitsHorizontalOffset = 5;
        int costDigitsVerticalOffset = 9;
        long balance = ModComponents.CURRENCY.get(inventory.player).getValue();
        NumismaticUtils.CoinsTuple coins = NumismaticUtils.convertCostToCoins(balance);
        if (coins.goldCoins > 0) {
            ItemStack goldStack = new ItemStack(NumismaticOverhaulItems.GOLD_COIN, (int) coins.goldCoins);
            context.drawItem(goldStack, x, y);
            context.getMatrices().push();
            context.getMatrices().translate(0.0, 0.0, 200.0);
            context.drawText(this.textRenderer, Text.literal(Long.toString(coins.goldCoins)), x + costDigitsHorizontalOffset, y + costDigitsVerticalOffset, 0xEEEEEE, false);
            context.getMatrices().pop();
        }
        if (coins.silverCoins > 0) {
            ItemStack silverStack = new ItemStack(NumismaticOverhaulItems.SILVER_COIN, (int) coins.silverCoins);
            context.drawItem(silverStack, x + 18, y);
            context.getMatrices().push();
            context.getMatrices().translate(0.0, 0.0, 200.0);
            context.drawText(this.textRenderer, Text.literal(Long.toString(coins.silverCoins)), x + costDigitsHorizontalOffset + 18, y + costDigitsVerticalOffset, 0xEEEEEE, false);
            context.getMatrices().pop();
        }
        if (coins.bronzeCoins > 0) {
            ItemStack bronzeStack = new ItemStack(NumismaticOverhaulItems.BRONZE_COIN, (int) coins.bronzeCoins);
            context.drawItem(bronzeStack, x + 2*18, y);
            context.getMatrices().push();
            context.getMatrices().translate(0.0, 0.0, 200.0);
            context.drawText(this.textRenderer, Text.literal(Long.toString(coins.bronzeCoins)), x + costDigitsHorizontalOffset + 2*18, y + costDigitsVerticalOffset, 0xEEEEEE, false);
            context.getMatrices().pop();
        }
    }

    protected void renderWaystoneTooltips(DrawContext context, int mouseX, int mouseY, int x, int y, int m) {
        ArrayList<String> waystones = getDiscoveredWaystones();
        for (int n = this.scrollOffset; n < m && n < getDiscoveredCount(); ++n) {
            int o = n - this.scrollOffset;
            int r = y + o * 18 + 2;
            if (mouseX < x || mouseY < r || mouseX >= x + 158 || mouseY >= r + 18) {
                continue;
            }
            var waystoneData = FabricWaystones.WAYSTONE_STORAGE.getWaystoneData(waystones.get(n));
            if (waystoneData == null) {
                continue;
            }
            List<Text> tooltipContents = new ArrayList<>();
            if (!FabricWaystones.CONFIG.teleportation_cost.cost_type().equals(FWConfigModel.CostType.NUMISMATIC)) {
                //var cost = Utils.getCost(Vec3d.ofCenter(waystoneData.way_getPos()), client.player.getPos(), startDim, endDim);
                String hashOrigin = null;
                if (this.handler instanceof WaystoneBlockScreenHandler waystoneBlockScreenHandler) {
                    hashOrigin = waystoneBlockScreenHandler.getWaystone();
                }
                var cost = Utils.getCostWrapper(this.inventory.player, hashOrigin, waystoneData.getHash());
                tooltipContents.add(Text.translatable("fwaystones.gui.cost_tooltip", cost == 0 ? Text.translatable("fwaystones.cost.free").getString() : cost));
                if (hasShiftDown()) {
                    tooltipContents.add(Text.translatable("fwaystones.gui.dimension_tooltip", waystoneData.getWorldName()));
                }
                context.drawTooltip(textRenderer, tooltipContents, mouseX, mouseY);
            } else {
                if (hasShiftDown()) {
                    tooltipContents.add(Text.translatable("fwaystones.gui.dimension_tooltip", waystoneData.getWorldName()));
                    context.drawTooltip(textRenderer, tooltipContents, mouseX, mouseY);
                }
            }
        }
    }

    protected void renderWaystoneNames(DrawContext context, int x, int y, int m) {
        if (FabricWaystones.WAYSTONE_STORAGE == null)
            return;
        ArrayList<String> waystones = getDiscoveredWaystones();
        for (int n = this.scrollOffset; n < m && n < waystones.size(); ++n) {
            int o = n - this.scrollOffset;
            int r = y + o * 18 + 2;

            String name = FabricWaystones.WAYSTONE_STORAGE.getName(waystones.get(n));
            context.drawText(textRenderer, name, x + 4, r + 4, 0x161616, false);
        }
    }

    protected void renderWaystoneCosts(DrawContext context, int x, int y, int m) {
        if (FabricWaystones.WAYSTONE_STORAGE == null)
            return;

        String originHash = "";
        if (this.handler instanceof WaystoneBlockScreenHandler) {
            originHash = ((WaystoneBlockScreenHandler) this.handler).getWaystone();
        }
        ArrayList<String> waystones = getDiscoveredWaystones();
        for (int n = this.scrollOffset; n < m && n < waystones.size(); ++n) {
            int o = n - this.scrollOffset;
            int r = y + o * 18 + 3;

            var destinationHash = waystones.get(n);

            if (Objects.equals(originHash, destinationHash)) {
                continue;
            }
            int cost = Utils.getCostWrapper(client.player, originHash, destinationHash);
            NumismaticUtils.CoinsTuple coins = NumismaticUtils.convertCostToCoins(cost);
            if (coins.goldCoins > 0) {
                ItemStack goldStack = new ItemStack(NumismaticOverhaulItems.GOLD_COIN, (int) coins.goldCoins);
                context.drawItem(goldStack, x - 20 - 2*18, r - 3);
                context.drawItemInSlot(this.textRenderer, goldStack, x - 20 - 2*18, r - 3);
            }
            if (coins.silverCoins > 0) {
                ItemStack silverStack = new ItemStack(NumismaticOverhaulItems.SILVER_COIN, (int) coins.silverCoins);
                context.drawItem(silverStack, x - 20 - 18, r - 3);
                context.drawItemInSlot(this.textRenderer, silverStack, x - 20 - 18, r - 3);
            }
            if (coins.bronzeCoins > 0) {
                ItemStack bronzeStack = new ItemStack(NumismaticOverhaulItems.BRONZE_COIN, (int) coins.bronzeCoins);
                context.drawItem(bronzeStack, x - 20, r - 3);
                context.drawItemInSlot(this.textRenderer, bronzeStack, x - 20, r - 3);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.mousePressed = true;
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        this.mouseClicked = false;
        if (this.hasWaystones() && canClickWaystones() && tryClick(mouseX, mouseY)) {
            return true;
        }
        for (Button guiButton : buttons) {
            if (!guiButton.isVisible() || !guiButton.isInBounds((int) mouseX - this.x, (int) mouseY - this.y)) {
                continue;
            }
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            guiButton.onClick();
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected boolean canClickWaystones() {
        return true;
    }

    protected boolean tryClick(double mouseX, double mouseY) {
        int forgetButtonX = this.x + 24;
        int forgetButtonY = this.y + 36;
        int waystoneButtonX = this.x + 36;
        int waystoneButtonY = this.y + 31;
        int adjustedScrollOffset = this.scrollOffset + maxLineNumber;

        int n = getDiscoveredCount();
        for (int currentWaystone = this.scrollOffset; currentWaystone < adjustedScrollOffset && currentWaystone < n; ++currentWaystone) {
            int currentWaystoneOffsetPosition = currentWaystone - this.scrollOffset;
            int forgetButtonStartX = (int) (mouseX - forgetButtonX);
            int forgetButtonStartY = (int) (mouseY - (forgetButtonY + currentWaystoneOffsetPosition * 18));

            int waystoneButtonStartX = (int) (mouseX - waystoneButtonX);
            int waystoneButtonStartY = (int) (mouseY - (waystoneButtonY + currentWaystoneOffsetPosition * 18));
            if (currentWaystoneOffsetPosition < n && forgetButtonStartX >= 0.0D && forgetButtonStartY >= 0.0D && forgetButtonStartX < 8 && forgetButtonStartY < 8 && (this.handler).onButtonClick(this.client.player, currentWaystone * 2 + 1)) {
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_ANVIL_BREAK, 1.0F));
                this.scrollOffset = Math.max(0, this.scrollOffset - 1);

                ClientPlayNetworking.send(new WaystoneGUISlotClickPacket(handler.syncId, currentWaystone * 2 + 1));

                return true;
            }
            if (handler instanceof WaystoneBlockScreenHandler waystoneBlockScreenHandler && waystoneBlockScreenHandler.getWaystone().equals(getDiscoveredWaystones().get(currentWaystone))) {
                continue;
            }
            if (waystoneButtonStartX >= 0.0D && waystoneButtonStartY >= 0.0D && waystoneButtonStartX < 158.0D && waystoneButtonStartY < 18.0D && (this.handler).onButtonClick(this.client.player, currentWaystone * 2)) {
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));

                ClientPlayNetworking.send(new WaystoneGUISlotClickPacket(handler.syncId, currentWaystone * 2));
                return true;
            }
        }

        int i3 = this.x + 141;
        int j3 = this.y + 40;
        if (mouseX >= (double) i3 && mouseX < (double) (i3 + 11) && mouseY >= (double) j3 && mouseY < (double) (j3 + 90)) {
            this.mouseClicked = true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.shouldScroll()) {
            int i = this.getMaxScroll();
            this.scrollAmount = (float) ((double) this.scrollAmount - verticalAmount / (double) i);
            this.scrollAmount = MathHelper.clamp(this.scrollAmount, 0.0F, 1.0F);
            this.scrollOffset = (int) ((double) (this.scrollAmount * (float) i) + 0.5D);
        }

        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.mouseClicked && this.shouldScroll()) {
            int i = this.y + 40;
            int j = i + 90;
            this.scrollAmount = ((float) mouseY - (float) i - 7.5F) / ((float) (j - i) - 15.0F);
            this.scrollAmount = MathHelper.clamp(this.scrollAmount, 0.0F, 1.0F);
            this.scrollOffset = (int) ((double) (this.scrollAmount * (float) this.getMaxScroll()) + 0.5D);
            return true;
        } else {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
    }

    protected boolean hasWaystones() {
        return getDiscoveredCount() > 0;
    }

    protected boolean shouldScroll() {
        return getDiscoveredCount() > maxLineNumber;
    }

    protected int getMaxScroll() {
        return getDiscoveredCount() - maxLineNumber;
    }

    protected int getDiscoveredCount() {
        return ((UniversalWaystoneScreenHandler) handler).getWaystonesCount();
    }

    protected ArrayList<String> getDiscoveredWaystones() {
        return ((UniversalWaystoneScreenHandler) handler).getSearchedWaystones();
    }

    protected boolean superMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    protected boolean superMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    protected void superResize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
    }

    protected void superOnMouseClick(Slot slot, int invSlot, int clickData, SlotActionType actionType) {
        super.onMouseClick(slot, invSlot, clickData, actionType);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.mouseClicked = false;
        this.mousePressed = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    protected boolean superMouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

}
