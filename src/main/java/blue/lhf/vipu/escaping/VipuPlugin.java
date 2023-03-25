package blue.lhf.vipu.escaping;

/**
 * A Vipu plugin.
 * */
public interface VipuPlugin {
    /**
     * Called when the plugin is enabled.
     * */
    void onEnable();

    /**
     * Called when the plugin is disabled.
     * */
    void onDisable();

    /**
     * @return The name of the plugin.
     * */
    default String getName() {
        return getClass().getSimpleName();
    }
}
