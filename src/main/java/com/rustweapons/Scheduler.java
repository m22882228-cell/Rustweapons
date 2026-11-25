package com.rustweapons;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Tiny server-tick scheduler used for the second reload sound. */
public final class Scheduler {
	private record Task(int[] ticks, Runnable run) {}

	private static final List<Task> TASKS = new ArrayList<>();

	private Scheduler() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (TASKS.isEmpty()) return;
			List<Task> snapshot = new ArrayList<>(TASKS);
			TASKS.clear();
			for (Task t : snapshot) {
				if (--t.ticks[0] <= 0) {
					t.run.run();
				} else {
					TASKS.add(t);
				}
			}
		});
	}

	public static void later(int ticks, Runnable run) {
		TASKS.add(new Task(new int[] {Math.max(1, ticks)}, run));
	}
}
