package com.iskolar.service;

import com.iskolar.dao.DocumentDao;
import com.iskolar.dao.StudentProfileDao;
import com.iskolar.dao.TagDao;
import com.iskolar.model.StudentProfile;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Profile wizard persistence: the 4-step wizard saves progressively — the
 * profile row after Steps 1-3, demographic tags after Step 3, and the
 * document readiness vault on Finish.
 */
public class ProfileService {

    private final StudentProfileDao profileDao;
    private final TagDao tagDao;
    private final DocumentDao documentDao;

    public ProfileService(StudentProfileDao profileDao, TagDao tagDao, DocumentDao documentDao) {
        this.profileDao = profileDao;
        this.tagDao = tagDao;
        this.documentDao = documentDao;
    }

    public Optional<StudentProfile> findByUserId(long userId) {
        return profileDao.findByUserId(userId);
    }

    /** Upserts the profile (Steps 1-3 fields). */
    public StudentProfile saveProfile(StudentProfile profile) {
        return profileDao.save(profile);
    }

    /** Replaces the student's demographic tag set (wizard Step 3). */
    public void saveTags(int studentId, Collection<Integer> tagIds) {
        tagDao.replaceForStudent(studentId, tagIds);
    }

    /** Replaces the student's document vault state (wizard Step 4 / Finish). */
    public void saveDocuments(int studentId, Map<Integer, Boolean> readyByDocTypeId) {
        documentDao.replaceForStudent(studentId, readyByDocTypeId);
    }
}
