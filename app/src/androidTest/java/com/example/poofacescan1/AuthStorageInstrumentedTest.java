package com.example.poofacescan1;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Pruebas instrumentadas de AuthStorage.
 * Se ejecutan en un dispositivo/emulador Android, no en la JVM local.
 */
@RunWith(AndroidJUnit4.class)
public class AuthStorageInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AuthStorage.logout();
        context.getSharedPreferences("local_auth_accounts_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @After
    public void tearDown() {
        AuthStorage.logout();
        context.getSharedPreferences("local_auth_accounts_v1", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    public void registerValidAccountAuthenticatesUser() {
        int result = AuthStorage.register(context, "UsuarioUno", "ClaveSegura123");

        assertEquals(AuthStorage.REGISTER_OK, result);
        assertTrue(AuthStorage.isAuthenticated());
        assertEquals("UsuarioUno", AuthStorage.getCurrentUsername());
        assertNotNull(AuthStorage.getCurrentUserId());
    }

    @Test
    public void registerRejectsInvalidUsernameAndWeakPassword() {
        assertEquals(AuthStorage.REGISTER_INVALID,
                AuthStorage.register(context, "ab", "ClaveSegura123"));
        assertEquals(AuthStorage.REGISTER_WEAK_PASSWORD,
                AuthStorage.register(context, "UsuarioDos", "123"));
    }

    @Test
    public void registerDoesNotAllowDuplicateUsernameIgnoringCase() {
        assertEquals(AuthStorage.REGISTER_OK,
                AuthStorage.register(context, "CuentaDemo", "ClaveSegura123"));
        AuthStorage.logout();

        assertEquals(AuthStorage.REGISTER_EXISTS,
                AuthStorage.register(context, "cuentademo", "OtraClave123"));
    }

    @Test
    public void loginAcceptsCorrectPasswordAndRejectsIncorrectPassword() {
        assertEquals(AuthStorage.REGISTER_OK,
                AuthStorage.register(context, "LoginDemo", "ClaveSegura123"));
        AuthStorage.logout();

        assertFalse(AuthStorage.login(context, "LoginDemo", "ClaveIncorrecta"));
        assertTrue(AuthStorage.login(context, " logindemo ", "ClaveSegura123"));
        assertTrue(AuthStorage.isAuthenticated());
    }

    @Test
    public void fiveFailedAttemptsTemporarilyLockAccount() {
        assertEquals(AuthStorage.REGISTER_OK,
                AuthStorage.register(context, "BloqueoDemo", "ClaveSegura123"));
        AuthStorage.logout();

        for (int i = 0; i < 5; i++) {
            assertFalse(AuthStorage.login(context, "BloqueoDemo", "Incorrecta123"));
        }

        assertTrue(AuthStorage.getRemainingLockTime(context, "BloqueoDemo") > 0L);
        assertFalse(AuthStorage.login(context, "BloqueoDemo", "ClaveSegura123"));
    }

    @Test
    public void logoutClearsAuthenticatedSession() {
        assertEquals(AuthStorage.REGISTER_OK,
                AuthStorage.register(context, "LogoutDemo", "ClaveSegura123"));

        AuthStorage.logout();

        assertFalse(AuthStorage.isAuthenticated());
        assertNull(AuthStorage.getCurrentUserId());
        assertEquals("", AuthStorage.getCurrentUsername());
    }
}
