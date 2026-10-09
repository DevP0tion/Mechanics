package devp0tion.mechanics.client;

import devp0tion.mechanics.pipe.PumpContainer;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.network.client.Client;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.ContainerComponent;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.presets.containerComponent.object.OEInventoryContainerForm;
import necesse.gfx.gameFont.FontOptions;

import java.awt.Rectangle;

/**
 * The window of the log-fueled pumps (N31-11): the vanilla object inventory window with the fuel
 * slot, and under it two lines (N36-44): the output direction and the form, then the state, the same
 * words as the hover tooltip (N36-57). Refreshed while the window is open, as the tank controller's
 * (5-4); a line too long for the window wraps, and the window grows with it.
 *
 * <p>TODO(game): not verified visually (the dedicated server cannot draw): the lines' place and look.
 */
public class PumpContainerForm extends OEInventoryContainerForm<PumpContainer> {

    // Layout (UI only).
    private static final int TEXT_X = 4;
    private static final int LINE_GAP = 4;
    private static final int BOTTOM_GAP = 8;
    private static final int FONT_SIZE = 16;

    private final int textWidth;
    private final FormLabel output;
    private final FormLabel state;
    private String shownOutput;
    private String shownState;

    public PumpContainerForm(Client client, PumpContainer container) {
        super(client, container);
        // After the vanilla layout: the window is placed when it opens, after this constructor.
        textWidth = inventoryForm.getWidth() - 2 * TEXT_X;
        int top = inventoryForm.getHeight();
        shownOutput = container.pump.getOutputText();
        shownState = container.pump.getStateText();
        output = inventoryForm.addComponent(new FormLabel(shownOutput, new FontOptions(FONT_SIZE), FormLabel.ALIGN_LEFT,
                TEXT_X, top, textWidth));
        state = inventoryForm.addComponent(new FormLabel(shownState, new FontOptions(FONT_SIZE), FormLabel.ALIGN_LEFT,
                TEXT_X, top, textWidth));
        layout();
    }

    /** Puts the state line under the output line and fits the window to them; whether its height changed. */
    private boolean layout() {
        state.setY(output.getY() + output.getHeight() + LINE_GAP);
        int height = state.getY() + state.getHeight() + BOTTOM_GAP;
        if (height == inventoryForm.getHeight()) {
            return false;
        }
        inventoryForm.setHeight(height);
        return true;
    }

    @Override
    public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
        String outputText = container.pump.getOutputText();
        String stateText = container.pump.getStateText();
        if (!outputText.equals(shownOutput) || !stateText.equals(shownState)) {
            shownOutput = outputText;
            shownState = stateText;
            output.setText(outputText, textWidth);
            state.setText(stateText, textWidth);
            if (layout()) {
                ContainerComponent.setPosFocus(inventoryForm);
            }
        }
        super.draw(tickManager, perspective, renderBox);
    }

}
