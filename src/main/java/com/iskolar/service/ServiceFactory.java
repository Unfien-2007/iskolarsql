package com.iskolar.service;

import com.iskolar.dao.DaoFactory;

/** Application wiring for the service layer (DAOs come from {@code DaoFactory}). */
public final class ServiceFactory {

    private ServiceFactory() {
    }

    private static final class AuthHolder {
        static final AuthService INSTANCE = new AuthService(DaoFactory.userDao());
    }

    private static final class MatchingHolder {
        static final MatchingService INSTANCE = new MatchingService(
                DaoFactory.scholarshipDao(),
                DaoFactory.studentProfileDao(),
                DaoFactory.tagDao(),
                DaoFactory.documentDao());
    }

    private static final class ProfileHolder {
        static final ProfileService INSTANCE = new ProfileService(
                DaoFactory.studentProfileDao(),
                DaoFactory.tagDao(),
                DaoFactory.documentDao());
    }

    private static final class ApplicationHolder {
        static final ApplicationService INSTANCE = new ApplicationService(DaoFactory.applicationDao());
    }

    public static AuthService authService() {
        return AuthHolder.INSTANCE;
    }

    public static MatchingService matchingService() {
        return MatchingHolder.INSTANCE;
    }

    public static ProfileService profileService() {
        return ProfileHolder.INSTANCE;
    }

    public static ApplicationService applicationService() {
        return ApplicationHolder.INSTANCE;
    }
}
