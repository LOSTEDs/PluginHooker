package io.github.dionatestserver.pluginhooker.hook;

import com.comphenix.protocol.injector.PacketFilterBuilder;
import io.github.dionatestserver.pluginhooker.DionaPluginHooker;
import io.github.dionatestserver.pluginhooker.config.DionaConfig;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.util.proxy.DefineClassHelper;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.util.function.BiPredicate;

public class HookerManager {

    @Getter
    private final CallbackHandler callbackHandler;

    private final ClassPool classPool;

    public HookerManager() {
        this.callbackHandler = new CallbackHandler();
        this.classPool = ClassPool.getDefault();
        classPool.appendClassPath(new LoaderClassPath(DionaPluginHooker.class.getClassLoader()));
    }

    public void injectEventHandler() {
        if (!DionaConfig.hookBukkitEvent) return;

        final String targetClassName = "org.bukkit.plugin.RegisteredListener";

        try {
            Class<?> bukkitEventHooker =
                    DefineClassHelper.toClass(
                            BukkitEventHooker.class.getName(),
                            HookerManager.class,
                            Bukkit.class.getClassLoader(),
                            null,
                            classPool.get(BukkitEventHooker.class.getName()).toBytecode()
                    );

            BiPredicate<Plugin, Event> callback = this.callbackHandler::handleBukkitEvent;
            bukkitEventHooker.getConstructor(BiPredicate.class).newInstance(callback);

            CtClass registeredListener = classPool.get(targetClassName);
            CtMethod callEvent = this.getMethodByName(registeredListener.getMethods(), "callEvent");
            callEvent.insertBefore(
                    "if(" + BukkitEventHooker.class.getName() + ".getInstance().onCallEvent(this.plugin,$1))return;"
            );

            DefineClassHelper.toClass(targetClassName, Plugin.class, Plugin.class.getClassLoader(), null, registeredListener.toBytecode());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void injectPacketHandler() {
        if (!DionaConfig.hookProtocolLibPacket) return;

        classPool.appendClassPath(new LoaderClassPath(PacketFilterBuilder.class.getClassLoader()));
        final String targetClassName = "com.comphenix.protocol.injector.PacketFilterManager";

        try {
            CtClass packetFilterManager = classPool.get(targetClassName);

            CtMethod postPacketToListeners = this.getMethodByName(packetFilterManager.getDeclaredMethods(), "handlePacket");
            postPacketToListeners.insertBefore(
                    "$1=" + DionaPluginHooker.class.getName() + ".getHookerManager().getCallbackHandler().handleProtocolLibPacket($1,$2,$3);"
            );

            DefineClassHelper.toClass(targetClassName, PacketFilterBuilder.class, PacketFilterBuilder.class.getClassLoader(), null, packetFilterManager.toBytecode());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private CtMethod getMethodByName(CtMethod[] methods, String targetName) {
        for (CtMethod method : methods) {
            if (method.getName().equals(targetName))
                return method;
        }
        return null;
    }

    public static class BukkitEventHooker {

        @Getter
        private static BukkitEventHooker instance;

        private final BiPredicate<Plugin, Event> callback;

        public BukkitEventHooker(BiPredicate<Plugin, Event> callback) {
            instance = this;
            this.callback = callback;
        }

        public boolean onCallEvent(Plugin plugin, Event event) {
            return callback.test(plugin, event);
        }
    }

}
