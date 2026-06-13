package org.degree.factions.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class SchedulerCompat {
    private static final boolean FOLIA = hasMethod(Bukkit.class, "getGlobalRegionScheduler");

    private SchedulerCompat() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static void runAsync(JavaPlugin plugin, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return;
        }

        invokeScheduler(
                staticInvoke(Bukkit.class, "getAsyncScheduler"),
                "runNow",
                plugin,
                consumer(task)
        );
    }

    public static void runGlobal(JavaPlugin plugin, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }

        invokeScheduler(
                staticInvoke(Bukkit.class, "getGlobalRegionScheduler"),
                "run",
                plugin,
                consumer(task)
        );
    }

    public static void runGlobalTimer(JavaPlugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
            return;
        }

        invokeScheduler(
                staticInvoke(Bukkit.class, "getGlobalRegionScheduler"),
                "runAtFixedRate",
                plugin,
                consumer(task),
                initialDelayTicks,
                periodTicks
        );
    }

    public static void runEntity(JavaPlugin plugin, Entity entity, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }

        Object scheduler = invoke(entity, "getScheduler");
        invokeScheduler(scheduler, "run", plugin, consumer(task), null);
    }

    private static Consumer<Object> consumer(Runnable task) {
        return ignored -> task.run();
    }

    private static boolean hasMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static Object staticInvoke(Class<?> type, String methodName) {
        try {
            return type.getMethod(methodName).invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not access Folia scheduler method " + methodName, e);
        }
    }

    private static Object invoke(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not access Folia entity scheduler", e);
        }
    }

    private static void invokeScheduler(Object scheduler, String methodName, Object... args) {
        for (Method method : scheduler.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                try {
                    method.invoke(scheduler, args);
                    return;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Could not invoke Folia scheduler method " + methodName, e);
                }
            }
        }
        throw new IllegalStateException("Folia scheduler method not found: " + methodName);
    }
}
