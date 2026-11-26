package com.example.poofacescan1;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Pruebas instrumentadas del almacenamiento biométrico cifrado.
 * Usan Android Keystore; ejecutar en un dispositivo/emulador Android.
 */
@RunWith(AndroidJUnit4.class)
public class BiometricStorageInstrumentedTest {
    private Context context;
    private final String userA = "test_user_a_unit";
    private final String userB = "test_user_b_unit";

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BiometricStorage.clearSavedEmbeddings(context, userA);
        BiometricStorage.clearSavedEmbeddings(context, userB);
    }

    private float[] validEmbedding(float seed) {
        float[] vector = new float[192];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = seed + (i + 1) * 0.001f;
        }
        return vector;
    }

    @Test
    public void savesAndReadsOneEmbedding() {
        List<float[]> input = new ArrayList<>();
        input.add(validEmbedding(0.1f));

        assertTrue(BiometricStorage.saveEmbeddings(context, userA, input));

        List<float[]> output = BiometricStorage.getSavedEmbeddings(context, userA);
        assertNotNull(output);
        assertEquals(1, output.size());
        assertArrayEquals(input.get(0), output.get(0), 0.00001f);
    }

    @Test
    public void savesAndReadsThreeEmbeddings() {
        List<float[]> input = Arrays.asList(
                validEmbedding(0.1f), validEmbedding(0.2f), validEmbedding(0.3f));

        assertTrue(BiometricStorage.saveEmbeddings(context, userA, input));

        List<float[]> output = BiometricStorage.getSavedEmbeddings(context, userA);
        assertNotNull(output);
        assertEquals(3, output.size());
        for (int i = 0; i < 3; i++) {
            assertArrayEquals(input.get(i), output.get(i), 0.00001f);
        }
    }

    @Test
    public void rejectsInvalidEmbeddingDimensionsAndZeroVector() {
        List<float[]> wrongSize = new ArrayList<>();
        wrongSize.add(new float[191]);
        assertFalse(BiometricStorage.saveEmbeddings(context, userA, wrongSize));

        List<float[]> zeroVector = new ArrayList<>();
        zeroVector.add(new float[192]);
        assertFalse(BiometricStorage.saveEmbeddings(context, userA, zeroVector));
    }

    @Test
    public void rejectsMoreThanThreeEmbeddings() {
        List<float[]> four = Arrays.asList(
                validEmbedding(0.1f), validEmbedding(0.2f),
                validEmbedding(0.3f), validEmbedding(0.4f));
        assertFalse(BiometricStorage.saveEmbeddings(context, userA, four));
    }

    @Test
    public void biometricDataAndPoseAreSavedTogether() {
        List<float[]> embeddings = Arrays.asList(
                validEmbedding(0.1f), validEmbedding(0.2f), validEmbedding(0.3f));
        float[] pose = new float[]{0.12f, 0.34f, 1.5f};

        assertTrue(BiometricStorage.saveBiometricData(context, userA, embeddings, pose));

        assertNotNull(BiometricStorage.getSavedEmbeddings(context, userA));
        assertArrayEquals(pose, BiometricStorage.getRegisteredPose(context, userA), 0.00001f);
    }

    @Test
    public void rejectsInvalidPose() {
        List<float[]> embeddings = Arrays.asList(validEmbedding(0.1f));
        assertFalse(BiometricStorage.saveBiometricData(
                context, userA, embeddings, new float[]{0.1f, Float.NaN, 0.3f}));
        assertFalse(BiometricStorage.saveRegisteredPose(
                context, userA, new float[]{0.1f, 0.2f}));
    }

    @Test
    public void biometricDataIsIsolatedByAccount() {
        List<float[]> embeddings = Arrays.asList(validEmbedding(0.1f));
        assertTrue(BiometricStorage.saveEmbeddings(context, userA, embeddings));

        assertNotNull(BiometricStorage.getSavedEmbeddings(context, userA));
        assertNull(BiometricStorage.getSavedEmbeddings(context, userB));
    }

    @Test
    public void consentIsStoredAndCleared() {
        assertFalse(BiometricStorage.hasBiometricConsent(context, userA));
        assertTrue(BiometricStorage.recordBiometricConsent(context, userA));
        assertTrue(BiometricStorage.hasBiometricConsent(context, userA));

        assertTrue(BiometricStorage.clearSavedEmbeddings(context, userA));
        assertFalse(BiometricStorage.hasBiometricConsent(context, userA));
    }

    @Test
    public void clearRemovesEmbeddingsAndPose() {
        List<float[]> embeddings = Arrays.asList(validEmbedding(0.1f));
        float[] pose = new float[]{0.1f, 0.2f, 0.3f};
        assertTrue(BiometricStorage.saveBiometricData(context, userA, embeddings, pose));

        assertTrue(BiometricStorage.clearSavedEmbeddings(context, userA));

        assertNull(BiometricStorage.getSavedEmbeddings(context, userA));
        assertNull(BiometricStorage.getRegisteredPose(context, userA));
    }
}
