package com.iskolar.service;

import com.iskolar.dao.ApplicationDao;
import com.iskolar.dto.ApplicationCard;
import com.iskolar.model.Application;
import com.iskolar.model.ApplicationStatus;

import java.util.List;

/**
 * Kanban tracker operations with transition validation
 * ({@code SAVED -> PREPARING_DOCS -> APPLIED -> INTERVIEW -> AWARDED | REJECTED}).
 */
public class ApplicationService {

    private final ApplicationDao applicationDao;

    public ApplicationService(ApplicationDao applicationDao) {
        this.applicationDao = applicationDao;
    }

    public List<ApplicationCard> cardsForStudent(int studentId) {
        return applicationDao.findCardsForStudent(studentId);
    }

    /** Bookmarks a scholarship (no-op when a tracker card already exists). */
    public Application bookmark(int studentId, long scholarshipId) {
        return applicationDao.findByStudentAndScholarship(studentId, scholarshipId)
                .orElseGet(() -> applicationDao.save(studentId, scholarshipId, ApplicationStatus.SAVED, null));
    }

    /** Advances a card, enforcing the canonical Kanban flow. */
    public void move(long applicationId, ApplicationStatus target) {
        Application current = applicationDao.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("No application with id " + applicationId));
        if (!current.status().nextTransitions().contains(target)) {
            throw new IllegalArgumentException(
                    "Cannot move an application from " + current.status() + " to " + target);
        }
        applicationDao.updateStatus(applicationId, target);
    }

    public void remove(long applicationId) {
        applicationDao.delete(applicationId);
    }
}
