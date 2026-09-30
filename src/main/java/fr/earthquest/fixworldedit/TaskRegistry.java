package fr.earthquest.fixworldedit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TaskRegistry {
    public interface Task {
        void cancel();
    }

    private final Map<UUID, Task> tasks = new HashMap<UUID, Task>();

    public boolean isBusy(UUID player) {
        return tasks.containsKey(player);
    }

    public void start(UUID player, Task task) {
        tasks.put(player, task);
    }

    public Task get(UUID player) {
        return tasks.get(player);
    }

    public void finish(UUID player) {
        tasks.remove(player);
    }

    public void cancelAll() {
        for (Task task : new ArrayList<Task>(tasks.values())) {
            task.cancel();
        }
        tasks.clear();
    }
}
