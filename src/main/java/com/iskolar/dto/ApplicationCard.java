package com.iskolar.dto;

import com.iskolar.model.Application;
import com.iskolar.model.Scholarship;

/** A Kanban board row — the tracker record joined with its scholarship. */
public record ApplicationCard(
        Application application,
        Scholarship scholarship) {
}
