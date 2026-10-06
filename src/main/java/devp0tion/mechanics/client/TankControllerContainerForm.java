package devp0tion.mechanics.client;

import devp0tion.mechanics.tank.TankControllerContainer;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.network.client.Client;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.localComponents.FormLocalLabel;
import necesse.gfx.forms.presets.containerComponent.ContainerForm;
import necesse.gfx.gameFont.FontOptions;

import java.awt.Rectangle;

/**
 * The tank controller window (5-4): the controller's name and the tank state as
 * {@code <fluid name> <current>/<max>} (6-2, 6-9), refreshed while the window is open.
 */
public class TankControllerContainerForm extends ContainerForm<TankControllerContainer> {

    // Window layout (UI only).
    private static final int WIDTH = 300;
    private static final int HEIGHT = 70;

    private final FormLabel status;
    private String shownText;

    public TankControllerContainerForm(Client client, TankControllerContainer container) {
        super(client, WIDTH, HEIGHT, container);
        addComponent(new FormLocalLabel(container.controller.getObject().getLocalization(),
                new FontOptions(20), FormLabel.ALIGN_LEFT, 4, 4));
        shownText = container.controller.getStatusText();
        status = addComponent(new FormLabel(shownText, new FontOptions(16), FormLabel.ALIGN_LEFT, 10, 38));
    }

    @Override
    public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
        String text = container.controller.getStatusText();
        if (!text.equals(shownText)) {
            shownText = text;
            status.setText(text);
        }
        super.draw(tickManager, perspective, renderBox);
    }

}
