package com.iskolar.dao;

import com.iskolar.config.DatabaseConfig;

/**
 * Application wiring: lazily-built DAO singletons sharing the HikariCP pool.
 *
 * <p>Unit/integration tests (Phase 3) construct DAOs directly with their own
 * {@code DataSource} instead of using this factory.
 */
public final class DaoFactory {

    private DaoFactory() {
    }

    private static final class UserDaoHolder {
        static final UserDao INSTANCE = new UserDao(DatabaseConfig.dataSource());
    }

    private static final class DegreeProgramDaoHolder {
        static final DegreeProgramDao INSTANCE = new DegreeProgramDao(DatabaseConfig.dataSource());
    }

    private static final class StudentProfileDaoHolder {
        static final StudentProfileDao INSTANCE = new StudentProfileDao(DatabaseConfig.dataSource());
    }

    private static final class ScholarshipDaoHolder {
        static final ScholarshipDao INSTANCE = new ScholarshipDao(DatabaseConfig.dataSource());
    }

    private static final class TagDaoHolder {
        static final TagDao INSTANCE = new TagDao(DatabaseConfig.dataSource());
    }

    private static final class DocumentDaoHolder {
        static final DocumentDao INSTANCE = new DocumentDao(DatabaseConfig.dataSource());
    }

    private static final class ApplicationDaoHolder {
        static final ApplicationDao INSTANCE = new ApplicationDao(DatabaseConfig.dataSource());
    }

    public static UserDao userDao() {
        return UserDaoHolder.INSTANCE;
    }

    public static DegreeProgramDao degreeProgramDao() {
        return DegreeProgramDaoHolder.INSTANCE;
    }

    public static StudentProfileDao studentProfileDao() {
        return StudentProfileDaoHolder.INSTANCE;
    }

    public static ScholarshipDao scholarshipDao() {
        return ScholarshipDaoHolder.INSTANCE;
    }

    public static TagDao tagDao() {
        return TagDaoHolder.INSTANCE;
    }

    public static DocumentDao documentDao() {
        return DocumentDaoHolder.INSTANCE;
    }

    public static ApplicationDao applicationDao() {
        return ApplicationDaoHolder.INSTANCE;
    }
}
