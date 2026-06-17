// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.task;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents a task with checkpoints for tracking progress.
 * Mirrors Roo-Code's task management architecture.
 */
public final class Task {

    @NotNull private final String id;
    @NotNull private String name;
    @Nullable private String description;
    @NotNull private TaskStatus status;
    @NotNull private List<TaskCheckpoint> checkpoints;
    private long createdAt;
    private long updatedAt;

    public enum TaskStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    }

    public Task() {
        this.id = UUID.randomUUID().toString();
        this.name = "Untitled Task";
        this.description = null;
        this.status = TaskStatus.PENDING;
        this.checkpoints = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    @NotNull public String getId() { return id; }
    @NotNull public String getName() { return name; }
    public void setName(@NotNull String name) { 
        this.name = name; 
        this.updatedAt = System.currentTimeMillis();
    }
    
    @Nullable public String getDescription() { return description; }
    public void setDescription(@Nullable String description) { 
        this.description = description; 
        this.updatedAt = System.currentTimeMillis();
    }
    
    @NotNull public TaskStatus getStatus() { return status; }
    public void setStatus(@NotNull TaskStatus status) { 
        this.status = status; 
        this.updatedAt = System.currentTimeMillis();
    }
    
    @NotNull public List<TaskCheckpoint> getCheckpoints() { 
        return Collections.unmodifiableList(checkpoints); 
    }

    /**
     * Add a checkpoint to track progress.
     */
    public void addCheckpoint(@NotNull String title, @Nullable String description) {
        TaskCheckpoint checkpoint = new TaskCheckpoint(title, description);
        checkpoints.add(checkpoint);
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Mark a checkpoint as completed by index.
     */
    public boolean completeCheckpoint(int index) {
        if (index >= 0 && index < checkpoints.size()) {
            TaskCheckpoint checkpoint = checkpoints.get(index);
            checkpoint.setCompleted(true);
            checkpoint.setCompletedAt(System.currentTimeMillis());
            this.updatedAt = System.currentTimeMillis();

            // Auto-update task status based on checkpoint progress
            updateTaskStatusFromCheckpoints();
            return true;
        }
        return false;
    }

    /**
     * Mark a checkpoint as failed by index.
     */
    public boolean failCheckpoint(int index) {
        if (index >= 0 && index < checkpoints.size()) {
            TaskCheckpoint checkpoint = checkpoints.get(index);
            checkpoint.setFailed(true);
            checkpoint.setCompletedAt(System.currentTimeMillis());
            this.updatedAt = System.currentTimeMillis();

            // Auto-update task status based on checkpoint progress
            updateTaskStatusFromCheckpoints();
            return true;
        }
        return false;
    }

    /**
     * Update task status based on checkpoint completion ratio.
     */
    private void updateTaskStatusFromCheckpoints() {
        if (checkpoints.isEmpty()) {
            return;
        }

        long completedCount = checkpoints.stream().filter(TaskCheckpoint::isCompleted).count();
        long failedCount = checkpoints.stream().filter(TaskCheckpoint::isFailed).count();

        if (failedCount > 0) {
            setStatus(TaskStatus.FAILED);
        } else if (completedCount == checkpoints.size()) {
            setStatus(TaskStatus.COMPLETED);
        } else if (completedCount > 0) {
            setStatus(TaskStatus.IN_PROGRESS);
        }
    }

    /**
     * Get completion percentage.
     */
    public double getCompletionPercentage() {
        if (checkpoints.isEmpty()) {
            return status == TaskStatus.COMPLETED ? 100.0 : 0.0;
        }
        long completedCount = checkpoints.stream().filter(TaskCheckpoint::isCompleted).count();
        return (completedCount * 100.0) / checkpoints.size();
    }

    /**
     * Get the current progress description from incomplete checkpoints.
     */
    @Nullable public String getProgressDescription() {
        for (TaskCheckpoint checkpoint : checkpoints) {
            if (!checkpoint.isCompleted()) {
                return checkpoint.getTitle();
            }
        }
        return null;
    }

    /**
     * Get task creation timestamp.
     */
    public long getCreatedAt() { return createdAt; }
    
    /**
     * Get last update timestamp.
     */
    public long getUpdatedAt() { return updatedAt; }

    /**
     * Create a summary string for display.
     */
    @NotNull public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (!checkpoints.isEmpty()) {
            long completedCount = checkpoints.stream().filter(TaskCheckpoint::isCompleted).count();
            sb.append(" (").append(completedCount).append("/").append(checkpoints.size()).append(")");
        }
        return sb.toString();
    }

    /**
     * Represents a checkpoint within a task.
     */
    public static final class TaskCheckpoint {
        @NotNull private String title;
        @Nullable private String description;
        private boolean completed;
        private boolean failed;
        private long createdAt;
        private Long completedAt;

        public TaskCheckpoint(@NotNull String title, @Nullable String description) {
            this.title = title;
            this.description = description;
            this.completed = false;
            this.failed = false;
            this.createdAt = System.currentTimeMillis();
            this.completedAt = null;
        }

        @NotNull public String getTitle() { return title; }
        public void setTitle(@NotNull String title) { this.title = title; }
        
        @Nullable public String getDescription() { return description; }
        public void setDescription(@Nullable String description) { this.description = description; }
        
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
        
        public boolean isFailed() { return failed; }
        public void setFailed(boolean failed) { this.failed = failed; }
        
        public long getCreatedAt() { return createdAt; }
        @Nullable public Long getCompletedAt() { return completedAt; }
        public void setCompletedAt(@Nullable Long completedAt) { this.completedAt = completedAt; }

        /**
         * Create a display string for the checkpoint.
         */
        @NotNull public String toDisplayString() {
            String prefix = completed ? "✅" : (failed ? "❌" : "⬜");
            return prefix + " " + title;
        }
    }
}
