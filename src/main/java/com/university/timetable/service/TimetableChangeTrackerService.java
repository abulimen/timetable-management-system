package com.university.timetable.service;

import com.university.timetable.dto.TimetableChangeStatusDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TimetableChangeTrackerService {

    private final AtomicBoolean pendingChanges = new AtomicBoolean(false);
    private final AtomicBoolean editingEnabled = new AtomicBoolean(true);
    private volatile String reason;
    private volatile LocalDateTime changedAt;

    public void markDirty(String reason) {
        pendingChanges.set(true);
        this.reason = reason;
        this.changedAt = LocalDateTime.now();
    }

    public void clear(String reason) {
        pendingChanges.set(false);
        this.reason = reason;
        this.changedAt = null;
    }

    public void enableEditing(String reason) {
        editingEnabled.set(true);
        this.reason = reason;
    }

    public void lockEditing(String reason) {
        editingEnabled.set(false);
        this.reason = reason;
    }

    public boolean isEditingEnabled() {
        return editingEnabled.get();
    }

    public TimetableChangeStatusDTO getStatus() {
        return new TimetableChangeStatusDTO(
                pendingChanges.get(),
                reason,
                changedAt,
                editingEnabled.get());
    }
}
