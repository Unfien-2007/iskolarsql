package com.iskolar.server;

import com.iskolar.server.admin.ScholarshipAdminRepository;
import com.iskolar.server.catalog.ScholarshipLifecycleRepository;
import com.iskolar.server.catalog.ScholarshipLifecycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScholarshipLifecycleServiceTest {

    @Mock
    private ScholarshipLifecycleRepository lifecycle;

    @Mock
    private ScholarshipAdminRepository admin;

    @InjectMocks
    private ScholarshipLifecycleService service;

    @Test
    void deactivateExpiredBumpsCatalogVersionOnlyWhenRowsChange() {
        when(lifecycle.deactivateExpired()).thenReturn(4);

        assertThat(service.deactivateExpired()).isEqualTo(4);

        verify(admin).bumpCatalogVersion();
    }

    @Test
    void deactivateExpiredDoesNotBumpWhenNothingChanges() {
        when(lifecycle.deactivateExpired()).thenReturn(0);

        assertThat(service.deactivateExpired()).isZero();

        verify(admin, never()).bumpCatalogVersion();
    }

    @Test
    void reactivateNonExpiredBumpsCatalogVersionOnlyWhenRowsChange() {
        when(lifecycle.reactivateNonExpired()).thenReturn(2);

        assertThat(service.reactivateNonExpired()).isEqualTo(2);

        verify(admin).bumpCatalogVersion();
    }

    @Test
    void reactivateNonExpiredDoesNotBumpWhenNothingChanges() {
        when(lifecycle.reactivateNonExpired()).thenReturn(0);

        assertThat(service.reactivateNonExpired()).isZero();

        verifyNoInteractions(admin);
    }
}
