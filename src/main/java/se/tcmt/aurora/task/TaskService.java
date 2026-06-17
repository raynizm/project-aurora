// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.task;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IntelliJ service for task management with checkpoint tracking.
 * Mirrors Roo-Code's MainThreadTaskShape interface and implementation.
 * 
 * Provides:
 * - Task creation and ID generation
 * - Task provider registration/unregistration
 * - Task list fetching with filtering
 * - Task execution tracking
 * - Checkpoint management for progress tracking
 */
@Service(Service.Level.PROJECT)
public final class TaskService {

    private static final Logger logger = Logger.getInstance(TaskService.class);

    @NotNull private final Project project;
    @NotNull private final Map<String, Task> tasks;
    @NotNull private final Map<Integer, String> taskProviders;
    @NotNull private final Map<String, Map<String, Object>> taskExecutions;

    public TaskService(@NotNull Project project) {
        this.project = project;
        this.tasks = new ConcurrentHashMap<>();
        this.taskProviders = new ConcurrentHashMap<>();
        this.taskExecutions = new ConcurrentHashMap<>();
        
        logger.debug("TaskService created for: " + project.getName());
    }

    /**
     * Create a new task and return its ID.
     */
    @NotNull public String createTask(@Nullable Map<String, Object> taskData) {
        try {
            Task task = new Task();
            
            // Populate from provided data if available
            if (taskData != null) {
                String name = (String) taskData.get("name");
                String description = (String) taskData.get("description");
                
                if (name != null && !name.isEmpty()) {
                    task.setName(name);
                }
                if (description != null) {
                    task.setDescription(description);
                }

                // Add checkpoints if provided
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpointData = 
                        (List<Map<String, Object>>) taskData.get("checkpoints");
                
                if (checkpointData != null) {
                    for (Map<String, Object> cp : checkpointData) {
                        String title = (String) cp.get("title");
                        String desc = (String) cp.get("description");
                        if (title != null) {
                            task.addCheckpoint(title, desc);
                        }
                    }
                }

                // Set initial status if provided
                String statusStr = (String) taskData.get("status");
                if (statusStr != null) {
                    try {
                        Task.TaskStatus status = Task.TaskStatus.valueOf(statusStr.toUpperCase());
                        task.setStatus(status);
                    } catch (IllegalArgumentException e) {
                        logger.debug("Invalid task status: " + statusStr);
                    }
                }
            }

            // Store the task
            tasks.put(task.getId(), task);
            
            logger.info("[Task] Created task: " + task.getName() + " (ID: " + task.getId() + ")");
            return task.getId();
        } catch (Exception e) {
            logger.error("[Task] Failed to create task", e);
            throw e;
        }
    }

    /**
     * Register a task provider.
     */
    public void registerTaskProvider(int handle, @NotNull String type) {
        try {
            taskProviders.put(handle, type);
            logger.debug("[Task] Registered provider: handle=" + handle + ", type=" + type);
        } catch (Exception e) {
            logger.error("[Task] Failed to register task provider", e);
        }
    }

    /**
     * Unregister a task provider.
     */
    public void unregisterTaskProvider(int handle) {
        try {
            taskProviders.remove(handle);
            logger.debug("[Task] Unregistered provider: handle=" + handle);
        } catch (Exception e) {
            logger.error("[Task] Failed to unregister task provider", e);
        }
    }

    /**
     * Fetch tasks with optional filtering.
     */
    @NotNull public List<Map<String, Object>> fetchTasks(@Nullable Map<String, Object> filter) {
        try {
            logger.debug("[Task] Fetching tasks with filter: " + filter);
            
            List<Map<String, Object>> result = new ArrayList<>();
            
            for (Task task : tasks.values()) {
                // Apply filters if provided
                if (filter != null && !matchesFilter(task, filter)) {
                    continue;
                }

                Map<String, Object> taskMap = taskToMap(task);
                result.add(taskMap);
            }

            logger.debug("[Task] Fetched " + result.size() + " tasks");
            return result;
        } catch (Exception e) {
            logger.error("[Task] Failed to fetch tasks", e);
            throw e;
        }
    }

    /**
     * Check if a task matches the given filter criteria.
     */
    private boolean matchesFilter(@NotNull Task task, @NotNull Map<String, Object> filter) {
        // Filter by status
        String statusFilter = (String) filter.get("status");
        if (statusFilter != null && !statusFilter.isEmpty()) {
            try {
                Task.TaskStatus expectedStatus = Task.TaskStatus.valueOf(statusFilter.toUpperCase());
                if (task.getStatus() != expectedStatus) {
                    return false;
                }
            } catch (IllegalArgumentException e) {
                // Invalid status filter, skip this check
            }
        }

        // Filter by name (substring match)
        String nameFilter = (String) filter.get("name");
        if (nameFilter != null && !nameFilter.isEmpty()) {
            if (!task.getName().toLowerCase().contains(nameFilter.toLowerCase())) {
                return false;
            }
        }

        // Filter by provider handle
        Integer providerHandle = (Integer) filter.get("providerHandle");
        if (providerHandle != null) {
            if (!taskProviders.containsKey(providerHandle)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get task execution information.
     */
    @NotNull public Map<String, Object> getTaskExecution(@Nullable Map<String, Object> value) {
        try {
            String taskId = null;
            if (value != null) {
                taskId = (String) value.get("id");
                if (taskId == null) {
                    taskId = (String) value.get("taskId");
                }
            }

            logger.debug("[Task] Getting execution for task: " + taskId);

            // Create a simple task execution DTO
            Map<String, Object> execution = new ConcurrentHashMap<>();
            execution.put("id", taskId != null ? taskId : "unknown-task");
            execution.put("task", value != null ? value : Collections.emptyMap());
            execution.put("active", false);

            return execution;
        } catch (Exception e) {
            logger.error("[Task] Failed to get task execution", e);
            throw e;
        }
    }

    /**
     * Execute a task.
     */
    @NotNull public Map<String, Object> executeTask(@Nullable Map<String, Object> taskData) {
        try {
            String taskId = null;
            if (taskData != null) {
                taskId = (String) taskData.get("id");
                if (taskId == null) {
                    taskId = (String) taskData.get("taskId");
                }
            }

            logger.info("[Task] Executing task: " + taskId);

            // Find the task in our store
            Task task = tasks.get(taskId != null ? taskId : null);
            
            // Create an executing task execution DTO
            Map<String, Object> execution = new ConcurrentHashMap<>();
            execution.put("id", taskId != null ? taskId : "unknown-task");
            execution.put("task", taskData != null ? taskData : Collections.emptyMap());
            execution.put("active", true);

            // Store task execution information
            if (taskId != null) {
                taskExecutions.put(taskId, execution);
                
                // Update task status to IN_PROGRESS
                if (task != null) {
                    task.setStatus(Task.TaskStatus.IN_PROGRESS);
                }
            }

            return execution;
        } catch (Exception e) {
            logger.error("[Task] Failed to execute task", e);
            throw e;
        }
    }

    /**
     * Terminate a task.
     */
    public void terminateTask(@NotNull String id) {
        try {
            logger.info("[Task] Terminating task: " + id);
            
            // Remove from executions
            taskExecutions.remove(id);
            
            // Update task status if it exists
            Task task = tasks.get(id);
            if (task != null) {
                task.setStatus(Task.TaskStatus.CANCELLED);
            }

            logger.debug("[Task] Terminated: " + id);
        } catch (Exception e) {
            logger.error("[Task] Failed to terminate task", e);
        }
    }

    /**
     * Register a task system.
     */
    public void registerTaskSystem(@NotNull String scheme, @Nullable Map<String, Object> info) {
        try {
            logger.debug("[Task] Registering task system: scheme=" + scheme + ", info=" + info);
            // Task system registration - no-op for now
        } catch (Exception e) {
            logger.error("[Task] Failed to register task system", e);
        }
    }

    /**
     * Mark custom execution as complete.
     */
    public void customExecutionComplete(@NotNull String id, @Nullable Integer result) {
        try {
            logger.debug("[Task] Custom execution complete for: " + id + ", result=" + result);
            
            // Update task execution status
            Map<String, Object> execution = taskExecutions.get(id);
            if (execution != null) {
                execution.put("active", false);
                if (result != null) {
                    execution.put("result", result);
                }
            }

            // Update task status if it exists
            Task task = tasks.get(id);
            if (task != null && result != null && result == 0) {
                task.setStatus(Task.TaskStatus.COMPLETED);
            } else if (task != null && result != null && result != 0) {
                task.setStatus(Task.TaskStatus.FAILED);
            }

        } catch (Exception e) {
            logger.error("[Task] Failed to update custom execution completion status", e);
        }
    }

    /**
     * Register supported execution types.
     */
    public void registerSupportedExecutions(@Nullable Boolean custom, 
                                            @Nullable Boolean shell, 
                                            @Nullable Boolean process) {
        try {
            logger.debug("[Task] Registering supported executions: custom=" + custom + 
                    ", shell=" + shell + ", process=" + process);
            // Execution type registration - no-op for now
        } catch (Exception e) {
            logger.error("[Task] Failed to register supported execution types", e);
        }
    }

    /**
     * Get a task by ID.
     */
    @Nullable public Task getTask(@NotNull String id) {
        return tasks.get(id);
    }

    /**
     * Get all tasks.
     */
    @NotNull public List<Task> getAllTasks() {
        return Collections.unmodifiableList(new ArrayList<>(tasks.values()));
    }

    /**
     * Delete a task by ID.
     */
    public boolean deleteTask(@NotNull String id) {
        Task removed = tasks.remove(id);
        if (removed != null) {
            taskExecutions.remove(id);
            logger.info("[Task] Deleted: " + id);
            return true;
        }
        return false;
    }

    /**
     * Convert a Task object to a Map for JSON serialization.
     */
    @NotNull private Map<String, Object> taskToMap(@NotNull Task task) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("id", task.getId());
        map.put("name", task.getName());
        map.put("description", task.getDescription());
        map.put("status", task.getStatus().toString().toLowerCase());
        map.put("createdAt", task.getCreatedAt());
        map.put("updatedAt", task.getUpdatedAt());
        map.put("completionPercentage", task.getCompletionPercentage());

        // Convert checkpoints to list of maps
        List<Map<String, Object>> checkpointList = new ArrayList<>();
        for (Task.TaskCheckpoint cp : task.getCheckpoints()) {
            Map<String, Object> cpMap = new ConcurrentHashMap<>();
            cpMap.put("title", cp.getTitle());
            cpMap.put("description", cp.getDescription());
            cpMap.put("completed", cp.isCompleted());
            cpMap.put("failed", cp.isFailed());
            cpMap.put("createdAt", cp.getCreatedAt());
            if (cp.getCompletedAt() != null) {
                cpMap.put("completedAt", cp.getCompletedAt());
            }
            checkpointList.add(cpMap);
        }
        map.put("checkpoints", checkpointList);

        return map;
    }

    /**
     * Dispose of all resources.
     */
    public void dispose() {
        logger.debug("[Task] Disposing TaskService");
        tasks.clear();
        taskProviders.clear();
        taskExecutions.clear();
    }
}
