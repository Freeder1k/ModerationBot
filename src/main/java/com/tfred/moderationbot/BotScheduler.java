package com.tfred.moderationbot;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link ScheduledThreadPoolExecutor} that can be paused and resumed.
 */
public class BotScheduler extends ScheduledThreadPoolExecutor {
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean ranWhilePaused = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<Runnable> pausedActions = new ConcurrentLinkedQueue<>();

    /**
     * Create a new scheduler that can be paused and resumed.
     */
    public BotScheduler() {
        super(0);
    }

    @NotNull
    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return super.schedule(() -> {
                    if (paused.get()) {
                        ranWhilePaused.set(true);
                        pausedActions.add(command);
                    } else
                        command.run();
                },
                delay,
                unit
        );
    }

    /**
     * @return
     *          a ScheduledFuture that can be used to extract result or cancel.
     *          If the scheduler was paused while this should've run the extracted result is null.
     */
    @NotNull
    @Override
    public <V> ScheduledFuture<@Nullable V> schedule(@NotNull Callable<V> callable, long delay, @NotNull TimeUnit unit) {
        return super.schedule(() -> {
                    if (paused.get()) {
                        ranWhilePaused.set(true);
                        pausedActions.add(() -> super.submit(callable));
                        return null;
                    } else
                        return callable.call();
                },
                delay,
                unit
        );
    }

    @NotNull
    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, java.util.concurrent.TimeUnit unit) {
        return super.scheduleAtFixedRate(() -> {
                    if (paused.get()) {
                        ranWhilePaused.set(true);
                        pausedActions.add(command);
                    }
                    command.run();
                },
                initialDelay,
                period,
                unit
        );
    }

    @NotNull
    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, java.util.concurrent.TimeUnit unit) {
        return super.scheduleWithFixedDelay(() -> {
                    if (paused.get()) {
                        ranWhilePaused.set(true);
                        pausedActions.add(command);
                    }
                    command.run();
                },
                initialDelay,
                delay,
                unit
        );
    }

    /**
     * @return true, if the scheduler is paused.
     */
    public boolean isPaused() {
        return paused.get();
    }

    /**
     * Pause the scheduler.
     */
    public void pause() {
        paused.set(true);
    }

    /**
     * Resume the scheduler.
     */
    public void resume() {
        paused.set(false);
        if (ranWhilePaused.compareAndSet(true, false)) {
            while (!pausedActions.isEmpty())
                if (paused.get())
                    break;
            Runnable r = pausedActions.poll();
            if (r != null)
                r.run();
        }
    }
}
