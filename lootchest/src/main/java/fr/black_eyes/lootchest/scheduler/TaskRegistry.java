package fr.black_eyes.lootchest.scheduler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Owns every task scheduled by LootChest so reload and shutdown can cancel them
 * as one deterministic unit.
 */
public final class TaskRegistry {

	private final JavaPlugin plugin;
	private final Map<String, BukkitTask> keyedTasks = new HashMap<>();
	private final Set<BukkitTask> tasks = new HashSet<>();

	public TaskRegistry(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public BukkitTask runLater(String key, Runnable action, long delayTicks) {
		requirePrimaryThread();
		cancel(key);

		AtomicReference<BukkitTask> taskReference = new AtomicReference<>();
		BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
			BukkitTask currentTask = taskReference.get();
			try {
				action.run();
			} finally {
				unregister(key, currentTask);
			}
		}, Math.max(0L, delayTicks));
		taskReference.set(task);
		register(key, task);
		return task;
	}

	public BukkitTask runLater(Runnable action, long delayTicks) {
		return runLater(null, action, delayTicks);
	}

	public BukkitTask runRepeating(String key, Runnable action, long delayTicks, long periodTicks) {
		requirePrimaryThread();
		cancel(key);

		AtomicReference<BukkitTask> taskReference = new AtomicReference<>();
		BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			try {
				action.run();
			} catch (RuntimeException | LinkageError exception) {
				plugin.getLogger().log(Level.SEVERE, "Scheduled task " + key + " failed and was cancelled.", exception);
				cancelTask(key, taskReference.get());
			}
		}, Math.max(0L, delayTicks), Math.max(1L, periodTicks));
		taskReference.set(task);
		register(key, task);
		return task;
	}

	public <T> void runBatched(
			String key,
			Collection<T> items,
			int itemsPerTick,
			Consumer<T> operation,
			Runnable completion
	) {
		requirePrimaryThread();
		Queue<T> pending = new ArrayDeque<>(items);
		if (pending.isEmpty()) {
			completion.run();
			return;
		}

		int batchSize = Math.max(1, itemsPerTick);
		runRepeating(key, () -> {
			for (int processed = 0; processed < batchSize && !pending.isEmpty(); processed++) {
				T item = pending.remove();
				try {
					operation.accept(item);
				} catch (RuntimeException | LinkageError exception) {
					plugin.getLogger().log(Level.SEVERE,
							"Scheduled chest operation " + key + " failed for " + item + "; continuing with the next chest.",
							exception);
				}
			}
			if (pending.isEmpty()) {
				cancel(key);
				completion.run();
			}
		}, 1L, 1L);
	}

	public boolean hasTask(String key) {
		requirePrimaryThread();
		BukkitTask task = keyedTasks.get(key);
		return task != null && !task.isCancelled();
	}

	public void cancel(String key) {
		requirePrimaryThread();
		if (key == null) {
			return;
		}
		cancelTask(key, keyedTasks.get(key));
	}

	public void cancelAll() {
		requirePrimaryThread();
		for (BukkitTask task : new ArrayList<>(tasks)) {
			task.cancel();
		}
		tasks.clear();
		keyedTasks.clear();
	}

	private void register(String key, BukkitTask task) {
		tasks.add(task);
		if (key != null) {
			keyedTasks.put(key, task);
		}
	}

	private void cancelTask(String key, BukkitTask task) {
		if (task == null) {
			return;
		}
		task.cancel();
		unregister(key, task);
	}

	private void unregister(String key, BukkitTask task) {
		if (task == null) {
			return;
		}
		tasks.remove(task);
		if (key != null) {
			keyedTasks.remove(key, task);
		}
	}

	private void requirePrimaryThread() {
		if (!Bukkit.isPrimaryThread()) {
			throw new IllegalStateException("LootChest scheduler tasks must be managed from the server thread.");
		}
	}
}
