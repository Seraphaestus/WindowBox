package amaryllis.window_box;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = WindowBox.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class Scheduling {

    // NB there's no serialization, it's only intended for quick "1 tick later"s where unloading is unlikely

    @FunctionalInterface
    public interface Action {
        void run();
    }

    private static class ScheduledAction {
        public Action action;
        public int ticks = 1;
        public ScheduledAction(Action action, int ticks) {
            this.action = action; this.ticks = ticks;
        }
        public void advance() {
            ticks--;
            if (ticks == 0) {
                action.run();
            }
        }
    }

    private static final List<ScheduledAction> EVENTS = new ArrayList<>();

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || EVENTS.isEmpty()) return;
        EVENTS.forEach(ScheduledAction::advance);
        EVENTS.removeIf(action -> action.ticks <= 0);
    }

    public static void onNextTick(Action action) {
        EVENTS.add(new ScheduledAction(action, 1));
    }
    public static void inNTicks(int ticks, Action action) {
        EVENTS.add(new ScheduledAction(action, ticks));
    }

}
