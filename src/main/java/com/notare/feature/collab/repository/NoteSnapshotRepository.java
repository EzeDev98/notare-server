package com.notare.feature.collab.repository;

import com.notare.feature.collab.model.NoteSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteSnapshotRepository extends JpaRepository<NoteSnapshot, Long> {
}
