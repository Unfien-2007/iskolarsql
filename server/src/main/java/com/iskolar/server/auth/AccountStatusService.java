package com.iskolar.server.auth;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches each account's live role and active flag for a short window so
 * JWT-protected endpoints can reject disabled or demoted accounts without a
 * database round-trip on every request. Admin user edits invalidate the entry
 * immediately.
 */
@Service
public class AccountStatusService {

    private static final long TTL_MS = 60_000;

    /** Account state resolved from the database. */
    public record AccountStatus(boolean active, String role) {
    }

    private record Entry(AccountStatus status, long expiresAt) {
    }

    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();
    private final UserRepository users;

    public AccountStatusService(UserRepository users) {
        this.users = users;
    }

    /**
     * Returns the database-backed status, falling back to the token role when
     * the account no longer exists (in which case it is treated as inactive).
     */
    public AccountStatus status(long userId, String tokenRole) {
        long now = System.currentTimeMillis();
        Entry entry = cache.get(userId);
        if (entry != null && entry.expiresAt() > now) {
            return entry.status();
        }
        AccountStatus status = users.findById(userId)
                .map(row -> new AccountStatus(row.isActive(), row.role()))
                .orElse(new AccountStatus(false, tokenRole));
        cache.put(userId, new Entry(status, now + TTL_MS));
        return status;
    }

    public void invalidate(long userId) {
        cache.remove(userId);
    }
}
