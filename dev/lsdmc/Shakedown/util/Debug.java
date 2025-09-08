package dev.lsdmc.Shakedown.util;

import dev.lsdmc.Shakedown.PrisonShakedown;
import dev.lsdmc.Shakedown.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

/**
 * Lightweight debug logger with MiniMessage colored output.
 */
public final class Debug {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Debug() {}

    private static boolean enabled() {
        ConfigManager cfg = new ConfigManager(PrisonShakedown.getInstance());
        return cfg.isDebugEnabled();
    }

    private static boolean verbose() {
        ConfigManager cfg = new ConfigManager(PrisonShakedown.getInstance());
        return cfg.isDebugVerbose();
    }

    private static boolean console() {
        ConfigManager cfg = new ConfigManager(PrisonShakedown.getInstance());
        return cfg.isDebugConsole();
    }

    public static void info(String msg) {
        if (!enabled()) return;
        log("<#06FFA5>[INFO]</#06FFA5> <#ADB5BD>" + msg + "</#ADB5BD>");
    }

    public static void warn(String msg) {
        if (!enabled()) return;
        log("<#FF6B6B>[WARN]</#FF6B6B> <#ADB5BD>" + msg + "</#ADB5BD>");
    }

    public static void error(String msg, Throwable t) {
        if (!enabled()) return;
        String base = "<#FF6B6B>[ERROR]</#FF6B6B> <#ADB5BD>" + msg + "</#ADB5BD>";
        if (verbose() && t != null) {
            base += " <gray>(" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")</gray>";
        }
        log(base);
        if (verbose() && t != null) t.printStackTrace();
    }

    private static void log(String mm) {
        Component c = MINI.deserialize(mm);
        Bukkit.getConsoleSender().sendMessage(c);
        if (console()) {
            PrisonShakedown.getInstance().getLogger().info(MINI.stripTags(mm));
        }
    }
}


