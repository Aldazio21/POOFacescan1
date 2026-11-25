package com.example.poofacescan1;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.util.ArrayList;
import java.util.List;

public final class BiometricStorage {
    private static final String PREF_NAME = "secure_biometric_data";
    private static final int EMBEDDING_SIZE = 192;

    private BiometricStorage() { }

    private static SharedPreferences getSecurePreferences(Context context) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        SharedPreferences preferences = EncryptedSharedPreferences.create(
                context, PREF_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        // Los vectores antiguos eran globales y no pertenecían a ninguna cuenta.
        // Se eliminan para evitar conservar biometría sin propietario asignado.
        if (preferences.contains("user_face_vectors_v2")
                || preferences.contains("user_face_vector")) {
            preferences.edit().remove("user_face_vectors_v2")
                    .remove("user_face_vector").commit();
        }
        return preferences;
    }

    private static String accountKey(String userId, String suffix) {
        if (userId == null || userId.trim().isEmpty())
            throw new IllegalArgumentException("No hay una cuenta activa.");
        return "account_" + userId.replaceAll("[^A-Za-z0-9_-]", "") + "_" + suffix;
    }

    /**
     * Guarda embeddings y postura de forma conjunta en una sola transacción
     * de SharedPreferences: no se informa un registro parcial.
     */
    public static boolean saveBiometricData(Context context, String userId,
                                            List<float[]> embeddings, float[] pose) {
        if (embeddings == null || embeddings.isEmpty() || embeddings.size() > 3
                || !isValidPose(pose)) return false;
        try {
            StringBuilder data = new StringBuilder();
            for (int n = 0; n < embeddings.size(); n++) {
                float[] vector = embeddings.get(n);
                if (!isValidEmbedding(vector)) return false;
                if (n > 0) data.append(';');
                for (int i = 0; i < vector.length; i++) {
                    if (i > 0) data.append(',');
                    data.append(vector[i]);
                }
            }

            SharedPreferences.Editor edit = getSecurePreferences(context).edit();
            edit.putString(accountKey(userId, "face_vectors_v3"), data.toString());
            edit.putFloat(accountKey(userId, "pose_yaw"), pose[0]);
            edit.putFloat(accountKey(userId, "pose_pitch"), pose[1]);
            edit.putFloat(accountKey(userId, "pose_roll"), pose[2]);
            edit.putBoolean(accountKey(userId, "pose_available"), true);
            return edit.commit();
        } catch (Exception e) {
            Log.e("BiometricStorage", "Error al guardar datos biométricos completos", e);
            return false;
        }
    }


    /** Registra que la persona aceptó el aviso de tratamiento biométrico. */
    public static boolean recordBiometricConsent(Context context, String userId) {
        try {
            return getSecurePreferences(context).edit()
                    .putBoolean(accountKey(userId, "biometric_consent"), true)
                    .putLong(accountKey(userId, "biometric_consent_timestamp"),
                            System.currentTimeMillis())
                    .commit();
        } catch (Exception e) {
            Log.e("BiometricStorage", "Error al registrar consentimiento", e);
            return false;
        }
    }

    public static boolean hasBiometricConsent(Context context, String userId) {
        try {
            SharedPreferences prefs = getSecurePreferences(context);
            return prefs.getBoolean(accountKey(userId, "biometric_consent"), false)
                    && prefs.getLong(accountKey(userId, "biometric_consent_timestamp"), 0L) > 0L;
        } catch (Exception e) {
            Log.e("BiometricStorage", "Error al consultar consentimiento", e);
            return false;
        }
    }

    public static boolean saveEmbeddings(Context context, String userId, List<float[]> embeddings) {
        if (embeddings == null || embeddings.isEmpty() || embeddings.size() > 3) return false;
        try {
            StringBuilder data = new StringBuilder();
            for (int n = 0; n < embeddings.size(); n++) {
                float[] vector = embeddings.get(n);
                if (!isValidEmbedding(vector)) return false;
                if (n > 0) data.append(';');
                for (int i = 0; i < vector.length; i++) {
                    if (i > 0) data.append(',');
                    data.append(vector[i]);
                }
            }
            return getSecurePreferences(context).edit()
                    .putString(accountKey(userId, "face_vectors_v3"), data.toString()).commit();
        } catch (Exception e) {
            Log.e("BiometricStorage", "Error al guardar embeddings", e);
            return false;
        }
    }

    public static List<float[]> getSavedEmbeddings(Context context, String userId) {
        try {
            String data = getSecurePreferences(context).getString(accountKey(userId, "face_vectors_v3"), null);
            if (data == null || data.trim().isEmpty()) return null;
            String[] vectors = data.split(";", -1);
            if (vectors.length < 1 || vectors.length > 3) return null;
            List<float[]> result = new ArrayList<>();
            for (String vectorText : vectors) {
                String[] values = vectorText.split(",", -1);
                if (values.length != EMBEDDING_SIZE) return null;
                float[] vector = new float[EMBEDDING_SIZE];
                for (int i = 0; i < EMBEDDING_SIZE; i++) vector[i] = Float.parseFloat(values[i]);
                if (!isValidEmbedding(vector)) return null;
                result.add(vector);
            }
            return result;
        } catch (Exception e) {
            Log.e("BiometricStorage", "Error al leer embeddings", e);
            return null;
        }
    }

    public static boolean saveRegisteredPose(Context context, String userId, float[] pose) {
        if (!isValidPose(pose)) return false;
        try {
            return getSecurePreferences(context).edit()
                    .putFloat(accountKey(userId, "pose_yaw"), pose[0])
                    .putFloat(accountKey(userId, "pose_pitch"), pose[1])
                    .putFloat(accountKey(userId, "pose_roll"), pose[2])
                    .putBoolean(accountKey(userId, "pose_available"), true).commit();
        } catch (Exception e) {
            Log.e("BiometricStorage", "Error al guardar postura", e);
            return false;
        }
    }

    public static float[] getRegisteredPose(Context context, String userId) {
        try {
            SharedPreferences prefs = getSecurePreferences(context);
            if (!prefs.getBoolean(accountKey(userId, "pose_available"), false)) return null;
            float[] pose = new float[]{
                    prefs.getFloat(accountKey(userId, "pose_yaw"), Float.NaN),
                    prefs.getFloat(accountKey(userId, "pose_pitch"), Float.NaN),
                    prefs.getFloat(accountKey(userId, "pose_roll"), Float.NaN)};
            return isValidPose(pose) ? pose : null;
        } catch (Exception e) {
            Log.e("BiometricStorage", "Error al leer postura", e);
            return null;
        }
    }

    public static boolean clearSavedEmbeddings(Context context, String userId) {
        try {
            SharedPreferences.Editor edit = getSecurePreferences(context).edit();
            edit.remove(accountKey(userId, "face_vectors_v3"));
            edit.remove(accountKey(userId, "pose_yaw"));
            edit.remove(accountKey(userId, "pose_pitch"));
            edit.remove(accountKey(userId, "pose_roll"));
            edit.remove(accountKey(userId, "pose_available"));
            edit.remove(accountKey(userId, "biometric_consent"));
            edit.remove(accountKey(userId, "biometric_consent_timestamp"));
            return edit.commit();
        } catch (Exception e) {
            Log.e("BiometricStorage", "Error al borrar datos de la cuenta", e);
            return false;
        }
    }

    private static boolean isValidPose(float[] pose) {
        return pose != null && pose.length == 3
                && Float.isFinite(pose[0])
                && Float.isFinite(pose[1])
                && Float.isFinite(pose[2]);
    }

    private static boolean isValidEmbedding(float[] vector) {
        if (vector == null || vector.length != EMBEDDING_SIZE) return false;
        double norm = 0;
        for (float value : vector) {
            if (Float.isNaN(value) || Float.isInfinite(value)) return false;
            norm += value * value;
        }
        return norm > 1.0e-12;
    }
}
