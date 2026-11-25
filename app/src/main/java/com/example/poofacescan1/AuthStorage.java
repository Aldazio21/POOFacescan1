package com.example.poofacescan1;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class AuthStorage {
    private static final String PREFS = "local_auth_accounts_v1";
    private static final String ACCOUNT_SET = "account_keys";
    private static final int ITERATIONS = 150000;
    private static final int HASH_BITS = 256;
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MS = 30000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile String currentUserId;
    private static volatile String currentUsername;

    public static final int REGISTER_OK = 0;
    public static final int REGISTER_EXISTS = 1;
    public static final int REGISTER_INVALID = 2;
    public static final int REGISTER_WEAK_PASSWORD = 3;

    private AuthStorage() { }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private static String normalized(String name) { return name == null ? "" : name.trim().toLowerCase(Locale.ROOT); }
    private static String accountKey(String normalizedName) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalizedName.getBytes("UTF-8"));
        StringBuilder out = new StringBuilder();
        for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return "account_" + out;
    }
    // PBKDF2-HMAC-SHA256 implementado con javax.crypto.Mac para mantener
    // compatibilidad con minSdk 24 sin depender del proveedor PBKDF2 del sistema.
    private static byte[] derive(char[] password, byte[] salt) throws Exception {
        byte[] passwordBytes = new String(password).getBytes("UTF-8");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(passwordBytes, "HmacSHA256"));
        byte[] block = new byte[salt.length + 4];
        System.arraycopy(salt, 0, block, 0, salt.length);
        block[block.length - 1] = 1; // INT_32_BE(1)
        byte[] u = mac.doFinal(block);
        byte[] result = u.clone();
        for (int i = 1; i < ITERATIONS; i++) {
            u = mac.doFinal(u);
            for (int j = 0; j < result.length; j++) result[j] ^= u[j];
        }
        java.util.Arrays.fill(passwordBytes, (byte) 0);
        return result;
    }
    private static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder();
        for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return s.toString();
    }
    private static byte[] unhex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(value.substring(i*2, i*2+2), 16);
        return out;
    }

    public static synchronized int register(Context context, String username, String password) {
        String display = username == null ? "" : username.trim();
        String normalized = normalized(username);
        if (display.length() < 3 || display.length() > 32 || password == null) return REGISTER_INVALID;
        if (password.length() < 8) return REGISTER_WEAK_PASSWORD;
        try {
            SharedPreferences p = prefs(context);
            String key = accountKey(normalized);
            if (p.contains(key + "_salt")) return REGISTER_EXISTS;
            byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
            String userId = UUID.randomUUID().toString().replace("-", "");
            char[] chars = password.toCharArray();
            byte[] hash;
            try { hash = derive(chars, salt); } finally { java.util.Arrays.fill(chars, '\0'); }
            Set<String> accounts = new HashSet<>(p.getStringSet(ACCOUNT_SET, new HashSet<>()));
            accounts.add(key);
            boolean saved = p.edit().putString(key + "_name", display)
                    .putString(key + "_user_id", userId).putString(key + "_salt", hex(salt))
                    .putString(key + "_hash", hex(hash)).putInt(key + "_failures", 0)
                    .putLong(key + "_lock_until", 0L).putStringSet(ACCOUNT_SET, accounts).commit();
            if (saved) { currentUserId = userId; currentUsername = display; }
            return saved ? REGISTER_OK : REGISTER_INVALID;
        } catch (Exception e) { return REGISTER_INVALID; }
    }

    public static synchronized boolean login(Context context, String username, String password) {
        String normalized = normalized(username);
        if (normalized.isEmpty() || password == null) return false;
        try {
            SharedPreferences p = prefs(context);
            String key = accountKey(normalized);
            if (!p.contains(key + "_salt")) return false;
            if (p.getLong(key + "_lock_until", 0L) > System.currentTimeMillis()) return false;
            byte[] salt = unhex(p.getString(key + "_salt", ""));
            char[] chars = password.toCharArray();
            byte[] candidate;
            try { candidate = derive(chars, salt); } finally { java.util.Arrays.fill(chars, '\0'); }
            byte[] expected = unhex(p.getString(key + "_hash", ""));
            if (MessageDigest.isEqual(candidate, expected)) {
                p.edit().putInt(key + "_failures", 0).putLong(key + "_lock_until", 0L).apply();
                currentUserId = p.getString(key + "_user_id", null);
                currentUsername = p.getString(key + "_name", username.trim());
                return currentUserId != null;
            }
            int failures = p.getInt(key + "_failures", 0) + 1;
            SharedPreferences.Editor edit = p.edit().putInt(key + "_failures", failures);
            if (failures >= MAX_ATTEMPTS) edit.putLong(key + "_lock_until", System.currentTimeMillis() + LOCK_MS).putInt(key + "_failures", 0);
            edit.apply();
            return false;
        } catch (Exception e) { return false; }
    }

    public static long getRemainingLockTime(Context context, String username) {
        try {
            String key = accountKey(normalized(username));
            return Math.max(0L, prefs(context).getLong(key + "_lock_until", 0L) - System.currentTimeMillis());
        } catch (Exception e) { return 0L; }
    }
    public static boolean isAuthenticated() { return currentUserId != null; }
    public static String getCurrentUserId() { return currentUserId; }
    public static String getCurrentUsername() { return currentUsername == null ? "" : currentUsername; }
    public static void logout() { currentUserId = null; currentUsername = null; }
}
