package com.example.poofacescan1;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.TextView;
import android.widget.Button;
import android.graphics.Color;
import androidx.appcompat.app.AlertDialog;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.imgproc.Imgproc;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.Log;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FaceAuth";
    private static final int REQUEST_CODE_PERMISSIONS = 10;

    private final String[] REQUIRED_PERMISSIONS =
            new String[]{Manifest.permission.CAMERA};

    private static final int FACENET_INPUT_SIZE = 112;
    private static final int EMBEDDING_SIZE = 192;

    // Se conserva el umbral original. No se reduce para evitar falsos positivos.
    private static final float SIMILARITY_THRESHOLD = 0.80f;
    // Umbral mínimo configurado en MediaPipe para aceptar una detección facial.
    private static final float FACE_DETECTION_THRESHOLD = 0.50f;

    // Se requieren 4 coincidencias de las últimas 5 comparaciones.
    private static final int VOTE_WINDOW_SIZE = 5;
    private static final int REQUIRED_MATCHES = 4;

    // Cinco capturas por cada condición seleccionada.
    private static final int REGISTRATION_SAMPLES = 5;
    private static final int REQUIRED_REFERENCE_POSES = 3;
    private static final float FRONT_POSE_MAX_DEVIATION = 0.045f;
    private static final float TURN_POSE_MIN_DEVIATION = 0.075f;

    // Tolerancias iniciales para comparar la postura con la referencia del registro.
    // Deben ajustarse con pruebas en el dispositivo real.
    private static final float POSE_YAW_TOLERANCE = 0.055f;
    private static final float POSE_PITCH_TOLERANCE = 0.10f;
    private static final float POSE_ROLL_TOLERANCE_DEGREES = 10.0f;

    private volatile boolean isProcessing = false;

    private PreviewView viewFinder;
    private TextView instructionText;
    private TextView statusText;
    private TextView detectionThresholdText;
    private TextView similarityText;
    private TextView detectionTimeText;
    private Button registerButton, verifyButton, deleteButton, logoutButton;

    private enum AppMode { IDLE, REGISTER, VERIFY }
    private volatile AppMode appMode = AppMode.IDLE;

    private ExecutorService cameraExecutor;
    private FaceLandmarker faceLandmarker;
    private Interpreter faceNetInterpreter;

    private boolean modelsReady = false;
    // Medición desde que comienza Registro/Verificar hasta la primera detección.
    private volatile long faceSearchStartNanos = 0L;
    private volatile boolean firstFaceDetectionRecorded = false;
    private volatile long firstFaceDetectionMillis = -1L;

    // Estas colecciones se usan exclusivamente desde cameraExecutor.
    private final Deque<Boolean> matchVotes = new ArrayDeque<>();
    private final List<float[]> registrationSamples = new ArrayList<>();
    // Tres embeddings de referencia: frontal, giro a la izquierda y giro a la derecha.
    private final List<float[]> registrationEmbeddings = new ArrayList<>();
    // Posturas de referencia: guardamos la postura frontal como referencia de control.
    private final List<float[]> registrationPoseSamples = new ArrayList<>();
    private float[] pendingRegisteredPose = null;
    private int registrationPoseStep = 0;

    // Liveness activo básico: reto aleatorio de giro de cabeza y parpadeo.
    // No sustituye un modelo anti-spoofing entrenado para detectar vídeos,
    // pantallas, máscaras u otros ataques de presentación.
    private static final int LIVENESS_TURN = 0;
    private static final int LIVENESS_BLINK = 1;
    private final Random livenessRandom = new Random();
    private volatile boolean livenessPassed = false;
    private boolean livenessInitialized = false;
    private int[] livenessActions = new int[2];
    private int livenessActionIndex = 0;
    private int yawCalibrationFrames = 0;
    private float yawCalibrationSum = 0f;
    private float neutralYaw = 0.5f;
    private boolean turnAwayDetected = false;
    private int turnAwayFrames = 0;
    private int turnReturnFrames = 0;
    private boolean eyesWereOpen = false;
    private boolean blinkClosedDetected = false;
    private int blinkClosedFrames = 0;
    private int blinkOpenFrames = 0;
    private float blinkOpenRatio = 0f;

    // Se exigen varios fotogramas consecutivos para reducir detecciones por ruido.
    private static final int REQUIRED_TURN_FRAMES = 3;
    private static final int REQUIRED_BLINK_FRAMES = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthStorage.isAuthenticated()) {
            startActivity(new android.content.Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);
        instructionText = findViewById(R.id.instructionText);
        statusText = findViewById(R.id.statusText);
        detectionThresholdText = findViewById(R.id.detectionThresholdText);
        similarityText = findViewById(R.id.similarityText);
        detectionTimeText = findViewById(R.id.detectionTimeText);
        registerButton = findViewById(R.id.registerButton);
        verifyButton = findViewById(R.id.verifyButton);
        deleteButton = findViewById(R.id.deleteButton);
        logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setText("Cerrar sesión · " + AuthStorage.getCurrentUsername());
        logoutButton.setOnClickListener(v -> logout());

        registerButton.setOnClickListener(v -> startRegistration());
        verifyButton.setOnClickListener(v -> startVerification());
        deleteButton.setOnClickListener(v -> confirmDelete());
        setFaceStatus(false, "Rostro inválido");
        updateDetectionThresholdMetric();
        updateSimilarityMetric(null);
        updateDetectionTimeMetric("Sin búsqueda iniciada");
        updateInstruction("Selecciona Registro o Verificar para comenzar");
        cameraExecutor = Executors.newSingleThreadExecutor();

        if (allPermissionsGranted()) {
            inicializarModelos();
            if (modelsReady) {
                startCamera();
            } else {
                updateInstruction("No se pudieron cargar los modelos");
            }
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            );
        }
    }

    private void inicializarModelos() {
        try {
            if (!OpenCVLoader.initDebug()) {
                Log.w(TAG, "OpenCV no pudo inicializarse.");
            } else {
                Log.d(TAG, "OpenCV listo.");
            }

            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath("face_landmarker.task")
                    .build();

            FaceLandmarker.FaceLandmarkerOptions options =
                    FaceLandmarker.FaceLandmarkerOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setRunningMode(RunningMode.IMAGE)
                            .setNumFaces(2)
                            .setMinFaceDetectionConfidence(FACE_DETECTION_THRESHOLD)
                            .build();

            faceLandmarker = FaceLandmarker.createFromOptions(this, options);
            faceNetInterpreter = new Interpreter(
                    loadModelFile("mobile_face_net.tflite")
            );

            int[] inputShape = faceNetInterpreter.getInputTensor(0).shape();
            int[] outputShape = faceNetInterpreter.getOutputTensor(0).shape();

            Log.d(TAG, "ENTRADA: " + Arrays.toString(inputShape));
            Log.d(TAG, "TIPO ENTRADA: "
                    + faceNetInterpreter.getInputTensor(0).dataType());
            Log.d(TAG, "SALIDA: " + Arrays.toString(outputShape));
            Log.d(TAG, "TIPO SALIDA: "
                    + faceNetInterpreter.getOutputTensor(0).dataType());

            // Evita ejecutar el pipeline si el modelo no coincide con lo esperado.
            if (!Arrays.equals(inputShape, new int[]{1, 112, 112, 3})
                    || !Arrays.equals(outputShape, new int[]{1, EMBEDDING_SIZE})
                    || faceNetInterpreter.getInputTensor(0).dataType() != DataType.FLOAT32
                    || faceNetInterpreter.getOutputTensor(0).dataType() != DataType.FLOAT32) {
                throw new IllegalStateException(
                        "La forma o el tipo de datos de MobileFaceNet no coincide."
                );
            }

            modelsReady = true;

        } catch (Exception e) {
            modelsReady = false;
            Log.e(TAG, "Error al cargar IA", e);
            closeModels();
        }
    }

    private ByteBuffer loadModelFile(String modelName) throws Exception {
        AssetFileDescriptor fd = getAssets().openFd(modelName);
        try (FileInputStream inputStream =
                     new FileInputStream(fd.getFileDescriptor())) {
            return inputStream.getChannel().map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.getStartOffset(),
                    fd.getDeclaredLength()
            );
        } finally {
            fd.close();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                        )
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::processFrame);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalysis
                );

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error al iniciar cámara", e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                updateInstruction("Error al iniciar la cámara");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.annotation.OptIn(
            markerClass = androidx.camera.core.ExperimentalGetImage.class
    )
    private void processFrame(ImageProxy imageProxy) {
        if (isProcessing) {
            imageProxy.close();
            return;
        }

        isProcessing = true;
        Bitmap bitmap = null;

        try {
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            bitmap = imageProxy.toBitmap();

            // Normaliza la orientación de la imagen antes de MediaPipe/OpenCV.
            if (bitmap != null && rotationDegrees != 0) {
                Bitmap rotated = rotateBitmap(bitmap, rotationDegrees);
                if (rotated != bitmap) {
                    bitmap.recycle();
                    bitmap = rotated;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error al convertir el fotograma a Bitmap", e);
        } finally {
            imageProxy.close();
        }

        if (bitmap != null) {
            ejecutarPipeline(bitmap);
        } else {
            isProcessing = false;
        }
    }

    private Bitmap rotateBitmap(Bitmap source, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(
                source, 0, 0,
                source.getWidth(), source.getHeight(),
                matrix, true
        );
    }

    private void ejecutarPipeline(Bitmap frame) {
        Bitmap croppedFace = null;

        try {
            if (!modelsReady || faceLandmarker == null
                    || faceNetInterpreter == null) {
                updateInstruction("Modelos no disponibles");
                return;
            }

            updateDetectionSearchElapsed();

            if (!isImageQualityAcceptable(frame)) {
                if (appMode != AppMode.IDLE && !livenessPassed) {
                    resetLivenessChallenge();
                }
                resetTemporalState();
                setFaceStatus(false, "Rostro inválido");
        updateDetectionThresholdMetric();
        updateSimilarityMetric(null);
        updateDetectionTimeMetric("Sin búsqueda iniciada");
                updateInstruction("Imagen borrosa: mejora la iluminación");
                return;
            }

            MPImage mpImage = new BitmapImageBuilder(frame).build();
            FaceLandmarkerResult result = faceLandmarker.detect(mpImage);

            if (result.faceLandmarks().size() != 1) {
                updateDetectionSearchElapsed();
                if (appMode != AppMode.IDLE && !livenessPassed) {
                    resetLivenessChallenge();
                }
                resetTemporalState();
                setFaceStatus(false, "Rostro inválido");
        updateDetectionThresholdMetric();
        updateSimilarityMetric(null);
        updateDetectionTimeMetric("Sin búsqueda iniciada");
                updateInstruction(result.faceLandmarks().isEmpty()
                        ? "No se detecta rostro"
                        : "Debe aparecer una sola persona frente a la cámara");
                return;
            }

            recordFirstFaceDetection();
            List<NormalizedLandmark> landmarks =
                    result.faceLandmarks().get(0);

            // Antes de registrar o comparar identidad, exige completar el reto
            // de presencia. La detección facial por sí sola no concede acceso.
            if (appMode != AppMode.IDLE && !livenessPassed) {
                setFaceStatus(true, "Rostro detectado · comprobando vida");
                if (!processLivenessChallenge(landmarks)) {
                    return;
                }
                livenessPassed = true;
                updateInstruction("Presencia comprobada. Procesando identidad...");
            }

            // Recorte cuadrado y corrección de inclinación usando las esquinas
            // externas de los ojos (índices 33 y 263 de Face Mesh).
            croppedFace = getAlignedSquareFace(frame, landmarks);

            if (croppedFace == null) {
                resetTemporalState();
                setFaceStatus(false, "Rostro inválido");
        updateDetectionThresholdMetric();
        updateSimilarityMetric(null);
        updateDetectionTimeMetric("Sin búsqueda iniciada");
                updateInstruction("Centra el rostro dentro de la cámara");
                return;
            }

            float[] liveEmbedding = getFaceEmbedding(croppedFace);

            if (!isValidEmbedding(liveEmbedding)) {
                resetTemporalState();
                setFaceStatus(false, "Rostro inválido");
        updateDetectionThresholdMetric();
        updateSimilarityMetric(null);
        updateDetectionTimeMetric("Sin búsqueda iniciada");
                updateInstruction("No se pudo procesar el rostro");
                return;
            }

            setFaceStatus(true, "Rostro Detectado");
            List<float[]> savedEmbeddings = BiometricStorage.getSavedEmbeddings(this, AuthStorage.getCurrentUserId());

            if (appMode == AppMode.IDLE) {
                return;
            }

            if (appMode == AppMode.REGISTER) {
                float[] currentPose = calculateFacePose(landmarks);
                if (currentPose == null) {
                    updateInstruction("No se pudo medir la pose. Ajusta tu rostro");
                    return;
                }

                String poseName = getRegistrationPoseName(registrationPoseStep);
                float yawDelta = currentPose[0] - neutralYaw;
                boolean poseMatches = isRequestedRegistrationPose(registrationPoseStep, yawDelta);
                if (!poseMatches) {
                    updateInstruction(getPoseGuidance(registrationPoseStep));
                    return;
                }

                if (registrationPoseStep == 0) {
                    registrationPoseSamples.add(currentPose);
                }
                registrationSamples.add(liveEmbedding);
                updateInstruction("Capturando pose " + poseName + ": "
                        + registrationSamples.size() + "/" + REGISTRATION_SAMPLES
                        + ". " + getPoseGuidance(registrationPoseStep));

                if (registrationSamples.size() >= REGISTRATION_SAMPLES) {
                    float[] averageEmbedding = averageAndNormalizeEmbeddings(registrationSamples);
                    if (!isValidEmbedding(averageEmbedding)) {
                        registrationSamples.clear();
                        updateInstruction("No se pudo calcular la referencia. Repite esta pose");
                        return;
                    }
                    registrationEmbeddings.add(averageEmbedding);
                    registrationSamples.clear();

                    if (registrationPoseStep == 0) {
                        pendingRegisteredPose = averagePoseSamples(registrationPoseSamples);
                        registrationPoseSamples.clear();
                    }
                    registrationPoseStep++;

                    if (registrationPoseStep < REQUIRED_REFERENCE_POSES) {
                        updateInstruction("Pose " + poseName + " completada. Siguiente: "
                                + getPoseGuidance(registrationPoseStep));
                        return;
                    }

                    boolean validPose = isValidPose(pendingRegisteredPose);
                    boolean saved = validPose && registrationEmbeddings.size() == REQUIRED_REFERENCE_POSES
                            && BiometricStorage.saveBiometricData(this,
                            AuthStorage.getCurrentUserId(), registrationEmbeddings, pendingRegisteredPose);
                    List<float[]> verification = saved
                            ? BiometricStorage.getSavedEmbeddings(this, AuthStorage.getCurrentUserId()) : null;
                    float[] savedPose = saved
                            ? BiometricStorage.getRegisteredPose(this, AuthStorage.getCurrentUserId()) : null;
                    boolean validSavedData = saved && verification != null
                            && verification.size() == REQUIRED_REFERENCE_POSES
                            && isValidPose(savedPose);
                    if (validSavedData) {
                        for (float[] vector : verification) {
                            if (!isValidEmbedding(vector)) { validSavedData = false; break; }
                        }
                    }

                    if (validSavedData) {
                        appMode = AppMode.VERIFY;
                        updateInstruction("REGISTRO COMPLETADO: 3 poses guardadas");
                        Log.d(TAG, "Tres embeddings de referencia guardados y verificados.");
                    } else {
                        BiometricStorage.clearSavedEmbeddings(this, AuthStorage.getCurrentUserId());
                        registrationEmbeddings.clear();
                        registrationPoseStep = 0;
                        pendingRegisteredPose = null;
                        updateInstruction("No se pudo guardar el registro completo. Inicia nuevamente");
                    }
                }
                return;
            }

            if (savedEmbeddings == null || savedEmbeddings.isEmpty()) {
                updateInstruction("No hay rostros registrados. Pulsa Registro.");
                return;
            }

            // Comprueba contra las tres referencias de pose de la cuenta activa.
            float bestSimilarity = -1f;

            for (float[] savedEmbedding : savedEmbeddings) {
                if (!isValidEmbedding(savedEmbedding)) {
                    Log.e(TAG, "Se encontró un embedding almacenado no válido.");
                    continue;
                }

                float similarity = calculateCosineSimilarity(
                        liveEmbedding, savedEmbedding
                );
                bestSimilarity = Math.max(bestSimilarity, similarity);
            }

            if (bestSimilarity < 0f) {
                resetTemporalState();
                updateInstruction("El registro guardado no es válido");
                return;
            }

            Log.d(TAG, "Mejor similitud actual: " + bestSimilarity);
            updateSimilarityMetric(bestSimilarity);

            addMatchVote(bestSimilarity >= SIMILARITY_THRESHOLD);
            updateRecognitionStatus();

        } catch (Exception e) {
            Log.e(TAG, "Error en el pipeline facial", e);
            updateInstruction("Error procesando el rostro");
        } finally {
            if (croppedFace != null && !croppedFace.isRecycled()) {
                croppedFace.recycle();
            }

            if (frame != null && !frame.isRecycled()) {
                frame.recycle();
            }

            isProcessing = false;
        }
    }

    private void startRegistration() {
        new AlertDialog.Builder(this)
                .setTitle("Consentimiento informado")
                .setMessage("La aplicación capturará imágenes de tu rostro para extraer tres representaciones biométricas (frontal, giro a la izquierda y giro a la derecha). Se almacenarán cifradas y asociadas únicamente a tu cuenta en este dispositivo. Se utilizarán para verificar tu identidad dentro de la aplicación. Puedes borrar estos datos desde la opción de eliminar registro.\n\n¿Aceptas voluntariamente este tratamiento de datos biométricos?")
                .setPositiveButton("Acepto y continuar", (dialog, which) -> {
                    boolean consentSaved = BiometricStorage.recordBiometricConsent(
                            this, AuthStorage.getCurrentUserId());
                    if (consentSaved) beginRegistration();
                    else Toast.makeText(this, "No se pudo registrar el consentimiento", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("No acepto", (dialog, which) -> {
                    updateInstruction("Registro cancelado: no se otorgó consentimiento");
                })
                .show();
    }

    private void beginRegistration() {
        cameraExecutor.execute(() -> {
            appMode = AppMode.REGISTER;
            startFaceDetectionTimer();
            updateSimilarityMetric(null);
            registrationSamples.clear();
            registrationEmbeddings.clear();
            registrationPoseSamples.clear();
            pendingRegisteredPose = null;
            registrationPoseStep = 0;
            matchVotes.clear();
            resetLivenessChallenge();
            updateInstruction("Registro iniciado. Primero completa la comprobación de presencia; después sigue las tres poses");
        });
    }

    private String getRegistrationPoseName(int step) {
        if (step == 0) return "FRONTAL";
        if (step == 1) return "IZQUIERDA";
        return "DERECHA";
    }

    private String getPoseGuidance(int step) {
        if (step == 0) return "Mira de frente a la cámara";
        if (step == 1) return "Gira ligeramente la cabeza hacia tu izquierda";
        return "Gira ligeramente la cabeza hacia tu derecha";
    }

    private boolean isRequestedRegistrationPose(int step, float yawDelta) {
        if (!Float.isFinite(yawDelta)) return false;
        if (step == 0) return Math.abs(yawDelta) <= FRONT_POSE_MAX_DEVIATION;
        // La vista frontal de la cámara invierte visualmente los lados:
        // el giro físico a la izquierda produce un yawDelta positivo en este flujo.
        if (step == 1) return yawDelta >= TURN_POSE_MIN_DEVIATION;
        return yawDelta <= -TURN_POSE_MIN_DEVIATION;
    }

    private void startVerification() {
        cameraExecutor.execute(() -> {
            appMode = AppMode.VERIFY;
            startFaceDetectionTimer();
            updateSimilarityMetric(null);
            registrationSamples.clear();
            registrationEmbeddings.clear();
            registrationPoseSamples.clear();
            pendingRegisteredPose = null;
            registrationPoseStep = 0;
            matchVotes.clear();
            resetLivenessChallenge();
            List<float[]> saved = BiometricStorage.getSavedEmbeddings(this, AuthStorage.getCurrentUserId());
            if (saved == null || saved.isEmpty()) {
                updateInstruction("Biblioteca vacía: no hay rostros registrados");
            } else {
                updateInstruction("Hay " + saved.size()
                        + " referencias biométricas registradas. Mira a la cámara");
            }
        });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Borrar rostros registrados")
                .setMessage("¿Seguro que deseas borrar todos los rostros guardados? Esta acción no se puede deshacer.")
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Borrar", (dialog, which) -> cameraExecutor.execute(() -> {
                    boolean deleted = BiometricStorage.clearSavedEmbeddings(this, AuthStorage.getCurrentUserId());
                    pendingRegisteredPose = null;
                    registrationPoseSamples.clear();
                    appMode = AppMode.IDLE;
                    registrationSamples.clear();
                    registrationEmbeddings.clear();
                    matchVotes.clear();
                    resetLivenessChallenge();
                    updateInstruction(deleted ? "Todos los rostros fueron borrados" : "No se pudieron borrar los datos");
                }))
                .show();
    }


    // Postura facial aproximada: yaw (giro horizontal), pitch (arriba/abajo)
    // y roll (inclinación lateral). No es una medición clínica ni una prueba de identidad.
    private float[] calculateFacePose(List<NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.size() <= 263) {
            return null;
        }

        float yaw = calculateYawRatio(landmarks);
        float eyeMidY = (landmarks.get(33).y() + landmarks.get(263).y()) / 2f;
        float chinY = landmarks.get(152).y();
        float noseY = landmarks.get(1).y();
        float verticalReference = chinY - eyeMidY;

        if (!Float.isFinite(yaw) || !Float.isFinite(eyeMidY)
                || !Float.isFinite(chinY) || !Float.isFinite(noseY)
                || verticalReference <= 1.0e-5f) {
            return null;
        }

        float pitch = (noseY - eyeMidY) / verticalReference;
        float dx = landmarks.get(263).x() - landmarks.get(33).x();
        float dy = landmarks.get(263).y() - landmarks.get(33).y();
        float roll = (float) Math.toDegrees(Math.atan2(dy, dx));

        if (!Float.isFinite(pitch) || !Float.isFinite(roll)) {
            return null;
        }

        return new float[]{yaw, pitch, roll};
    }

    private float[] averagePoseSamples(List<float[]> samples) {
        if (samples == null || samples.isEmpty()) return null;
        float[] average = new float[3];
        int validCount = 0;
        for (float[] sample : samples) {
            if (!isValidPose(sample)) continue;
            for (int i = 0; i < 3; i++) average[i] += sample[i];
            validCount++;
        }
        if (validCount == 0) return null;
        for (int i = 0; i < 3; i++) average[i] /= validCount;
        return isValidPose(average) ? average : null;
    }

    private boolean isValidPose(float[] pose) {
        return pose != null && pose.length == 3
                && Float.isFinite(pose[0])
                && Float.isFinite(pose[1])
                && Float.isFinite(pose[2]);
    }

    private float[] loadRegisteredPose() {
        return BiometricStorage.getRegisteredPose(this, AuthStorage.getCurrentUserId());
    }

    private boolean isPoseWithinRegisteredPosition(List<NormalizedLandmark> landmarks) {
        float[] reference = loadRegisteredPose();
        if (!isValidPose(reference)) {
            updateInstruction("No hay una posición válida guardada. Registra nuevamente el rostro");
            return false;
        }

        float[] current = calculateFacePose(landmarks);
        if (!isValidPose(current)) {
            updateInstruction("No se pudo medir la posición. Centra tu rostro");
            return false;
        }

        float yawDifference = Math.abs(current[0] - reference[0]);
        float pitchDifference = Math.abs(current[1] - reference[1]);
        float rollDifference = Math.abs(current[2] - reference[2]);

        // Evita que una diferencia de ángulo equivalente a ±180° se interprete como enorme.
        rollDifference = Math.min(rollDifference, 360f - rollDifference);

        Log.d(TAG, "POSE delta yaw=" + yawDifference
                + " pitch=" + pitchDifference + " roll=" + rollDifference);

        if (yawDifference > POSE_YAW_TOLERANCE
                || pitchDifference > POSE_PITCH_TOLERANCE
                || rollDifference > POSE_ROLL_TOLERANCE_DEGREES) {
            updateInstruction("Coloca tu rostro en la misma posición del registro");
            return false;
        }

        return true;
    }

    private void resetLivenessChallenge() {
        livenessPassed = false;
        livenessInitialized = false;
        livenessActionIndex = 0;
        yawCalibrationFrames = 0;
        yawCalibrationSum = 0f;
        neutralYaw = 0.5f;
        turnAwayDetected = false;
        turnAwayFrames = 0;
        turnReturnFrames = 0;
        eyesWereOpen = false;
        blinkClosedDetected = false;
        blinkClosedFrames = 0;
        blinkOpenFrames = 0;
        blinkOpenRatio = 0f;

        // El orden cambia entre sesiones para evitar un patrón fijo.
        if (livenessRandom.nextBoolean()) {
            livenessActions = new int[]{LIVENESS_TURN, LIVENESS_BLINK};
        } else {
            livenessActions = new int[]{LIVENESS_BLINK, LIVENESS_TURN};
        }
    }

    private boolean processLivenessChallenge(List<NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.size() <= 386) {
            updateInstruction("No se pudieron analizar los rasgos. Centra tu rostro.");
            return false;
        }

        if (livenessActionIndex >= livenessActions.length) {
            livenessPassed = true;
            return true;
        }

        float yaw = calculateYawRatio(landmarks);
        if (!Float.isFinite(yaw)) {
            updateInstruction("Mantén el rostro visible frente a la cámara");
            return false;
        }

        // Calibración neutral con varias imágenes consecutivas.
        if (!livenessInitialized) {
            yawCalibrationSum += yaw;
            yawCalibrationFrames++;

            if (yawCalibrationFrames < 8) {
                updateInstruction("Comprobación de presencia: mira al frente y permanece quieto");
                return false;
            }

            neutralYaw = yawCalibrationSum / yawCalibrationFrames;
            livenessInitialized = true;
            updateLivenessInstruction();
            return false;
        }

        int action = livenessActions[livenessActionIndex];

        if (action == LIVENESS_TURN) {
            float deviation = Math.abs(yaw - neutralYaw);

            if (!turnAwayDetected) {
                if (deviation >= 0.085f) {
                    turnAwayFrames++;
                } else {
                    turnAwayFrames = 0;
                }

                if (turnAwayFrames >= REQUIRED_TURN_FRAMES) {
                    turnAwayDetected = true;
                    turnReturnFrames = 0;
                    updateInstruction("Bien. Ahora vuelve a mirar al frente");
                } else {
                    updateInstruction("Prueba de presencia: gira ligeramente la cabeza hacia un lado");
                }
            } else {
                if (deviation <= 0.045f) {
                    turnReturnFrames++;
                } else {
                    turnReturnFrames = 0;
                }

                if (turnReturnFrames >= REQUIRED_TURN_FRAMES) {
                    completeLivenessAction();
                } else {
                    updateInstruction("Ahora vuelve a mirar al frente");
                }
            }
        } else {
            float eyeRatio = calculateEyeAspectRatio(landmarks);
            if (!Float.isFinite(eyeRatio)) {
                updateInstruction("Mantén los ojos visibles frente a la cámara");
                return false;
            }

            // Primero exige ojos abiertos para establecer una referencia personal.
            if (!eyesWereOpen) {
                if (eyeRatio >= 0.16f) {
                    eyesWereOpen = true;
                    blinkOpenRatio = eyeRatio;
                    blinkClosedFrames = 0;
                    blinkOpenFrames = 0;
                    updateInstruction("Prueba de presencia: parpadea una vez");
                } else {
                    updateInstruction("Abre los ojos y mira a la cámara");
                }
            } else if (!blinkClosedDetected) {
                if (eyeRatio <= blinkOpenRatio * 0.85f) {
                    blinkClosedFrames++;
                } else {
                    blinkClosedFrames = 0;
                }

                if (blinkClosedFrames >= REQUIRED_BLINK_FRAMES) {
                    blinkClosedDetected = true;
                    blinkOpenFrames = 0;
                }
                updateInstruction("Prueba de presencia: parpadea una vez");
            } else {
                if (eyeRatio >= blinkOpenRatio * 0.90f) {
                    blinkOpenFrames++;
                } else {
                    blinkOpenFrames = 0;
                }

                if (blinkOpenFrames >= REQUIRED_BLINK_FRAMES) {
                    completeLivenessAction();
                } else {
                    updateInstruction("Abre los ojos nuevamente");
                }
            }
        }

        return livenessPassed;
    }

    private void completeLivenessAction() {
        livenessActionIndex++;
        turnAwayDetected = false;
        turnAwayFrames = 0;
        turnReturnFrames = 0;
        eyesWereOpen = false;
        blinkClosedDetected = false;
        blinkClosedFrames = 0;
        blinkOpenFrames = 0;
        blinkOpenRatio = 0f;

        if (livenessActionIndex >= livenessActions.length) {
            livenessPassed = true;
            updateInstruction("Presencia comprobada. Continuando...");
        } else {
            updateLivenessInstruction();
        }
    }

    private void updateLivenessInstruction() {
        if (livenessActionIndex >= livenessActions.length) {
            return;
        }

        if (livenessActions[livenessActionIndex] == LIVENESS_TURN) {
            updateInstruction("Prueba de vida: gira ligeramente la cabeza hacia un lado y vuelve al frente");
        } else {
            updateInstruction("Prueba de vida: parpadea una vez");
        }
    }

    // Relación entre la posición horizontal de la nariz y las esquinas externas
    // de los ojos. Se usa solo para detectar un cambio relativo de orientación.
    private float calculateYawRatio(List<NormalizedLandmark> landmarks) {
        float leftX = landmarks.get(33).x();
        float rightX = landmarks.get(263).x();
        float noseX = landmarks.get(1).x();
        float eyeDistance = rightX - leftX;

        if (Math.abs(eyeDistance) < 0.05f) {
            return Float.NaN;
        }

        return (noseX - leftX) / eyeDistance;
    }

    // EAR aproximado con seis landmarks por ojo. No es un clasificador biométrico.
    private float calculateEyeAspectRatio(List<NormalizedLandmark> landmarks) {
        float left = eyeAspectRatio(
                landmarks, 33, 160, 158, 133, 153, 144
        );
        float right = eyeAspectRatio(
                landmarks, 362, 385, 387, 263, 373, 380
        );

        if (!Float.isFinite(left) || !Float.isFinite(right)) {
            return Float.NaN;
        }

        return (left + right) / 2f;
    }

    private float eyeAspectRatio(
            List<NormalizedLandmark> landmarks,
            int corner1, int upper1, int upper2,
            int corner2, int lower1, int lower2
    ) {
        float horizontal = landmarkDistance(
                landmarks.get(corner1), landmarks.get(corner2)
        );
        if (horizontal <= 1.0e-6f) {
            return Float.NaN;
        }

        float vertical1 = landmarkDistance(
                landmarks.get(upper1), landmarks.get(lower1)
        );
        float vertical2 = landmarkDistance(
                landmarks.get(upper2), landmarks.get(lower2)
        );

        return (vertical1 + vertical2) / (2f * horizontal);
    }

    private float landmarkDistance(
            NormalizedLandmark first,
            NormalizedLandmark second
    ) {
        float dx = first.x() - second.x();
        float dy = first.y() - second.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void startFaceDetectionTimer() {
        faceSearchStartNanos = android.os.SystemClock.elapsedRealtimeNanos();
        firstFaceDetectionRecorded = false;
        firstFaceDetectionMillis = -1L;
        updateDetectionTimeMetric("Buscando rostro...");
    }

    private void recordFirstFaceDetection() {
        if (faceSearchStartNanos <= 0L || firstFaceDetectionRecorded) {
            return;
        }
        long elapsedNanos = android.os.SystemClock.elapsedRealtimeNanos()
                - faceSearchStartNanos;
        firstFaceDetectionMillis = Math.max(0L, elapsedNanos / 1_000_000L);
        firstFaceDetectionRecorded = true;
        updateDetectionTimeMetric(formatElapsed(firstFaceDetectionMillis));
    }

    private void updateDetectionSearchElapsed() {
        if (faceSearchStartNanos <= 0L || firstFaceDetectionRecorded) {
            return;
        }
        long elapsedMillis = (android.os.SystemClock.elapsedRealtimeNanos()
                - faceSearchStartNanos) / 1_000_000L;
        updateDetectionTimeMetric("Buscando... " + formatElapsed(elapsedMillis));
    }

    private String formatElapsed(long millis) {
        if (millis < 1000L) {
            return millis + " ms";
        }
        return String.format(java.util.Locale.getDefault(), "%.2f s",
                millis / 1000.0);
    }

    private void updateDetectionThresholdMetric() {
        runOnUiThread(() -> {
            if (detectionThresholdText != null) {
                detectionThresholdText.setText(String.format(
                        java.util.Locale.getDefault(),
                        "Umbral de detección (MediaPipe): %.0f%%",
                        FACE_DETECTION_THRESHOLD * 100f));
            }
        });
    }

    private void updateSimilarityMetric(Float similarity) {
        runOnUiThread(() -> {
            if (similarityText != null) {
                if (similarity == null || similarity < 0f) {
                    similarityText.setText(String.format(
                            java.util.Locale.getDefault(),
                            "Umbral de similitud: %.0f%% | Similitud actual: --",
                            SIMILARITY_THRESHOLD * 100f));
                } else {
                    similarityText.setText(String.format(
                            java.util.Locale.getDefault(),
                            "Umbral de similitud: %.0f%% | Similitud actual: %.1f%%",
                            SIMILARITY_THRESHOLD * 100f,
                            similarity * 100f));
                }
            }
        });
    }

    private void updateDetectionTimeMetric(String value) {
        runOnUiThread(() -> {
            if (detectionTimeText != null) {
                detectionTimeText.setText("Tiempo hasta detectar: " + value);
            }
        });
    }

    private void setFaceStatus(boolean detected, String message) {
        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText(message);
                statusText.setTextColor(Color.WHITE);
                statusText.setBackgroundColor(detected ? Color.rgb(46, 125, 50) : Color.rgb(198, 40, 40));
            }
        });
    }

    private void addMatchVote(boolean matched) {
        matchVotes.addLast(matched);

        while (matchVotes.size() > VOTE_WINDOW_SIZE) {
            matchVotes.removeFirst();
        }
    }

    private void updateRecognitionStatus() {
        if (matchVotes.size() < VOTE_WINDOW_SIZE) {
            updateInstruction("Verificando rostro...");
            return;
        }

        int matches = 0;
        for (Boolean vote : matchVotes) {
            if (vote) {
                matches++;
            }
        }

        if (matches >= REQUIRED_MATCHES) {
            updateInstruction("ACCESO CONCEDIDO");
        } else if (matchVotes.size() - matches >= REQUIRED_MATCHES) {
            updateInstruction("ROSTRO NO COINCIDE");
        } else {
            updateInstruction("Verificando rostro...");
        }
    }

    private void resetTemporalState() {
        matchVotes.clear();
        registrationSamples.clear();
        registrationPoseSamples.clear();
        // No se borra registrationEmbeddings: si una captura falla durante
        // la segunda fase, se conserva la primera condición ya completada.
    }

    private boolean isImageQualityAcceptable(Bitmap bitmap) {
        Mat rgba = new Mat();
        Mat gray = new Mat();
        Mat laplacian = new Mat();
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble std = new MatOfDouble();

        try {
            Utils.bitmapToMat(bitmap, rgba);
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F);
            Core.meanStdDev(laplacian, mean, std);

            double variance = Math.pow(std.get(0, 0)[0], 2);
            return variance >= 100.0;

        } finally {
            rgba.release();
            gray.release();
            laplacian.release();
            mean.release();
            std.release();
        }
    }

    private Bitmap getAlignedSquareFace(
            Bitmap original,
            List<NormalizedLandmark> landmarks
    ) {
        if (landmarks == null || landmarks.size() <= 263) {
            return null;
        }

        float minX = 1f;
        float minY = 1f;
        float maxX = 0f;
        float maxY = 0f;

        for (NormalizedLandmark landmark : landmarks) {
            float x = landmark.x();
            float y = landmark.y();

            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                continue;
            }

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        float faceWidth = (maxX - minX) * original.getWidth();
        float faceHeight = (maxY - minY) * original.getHeight();

        if (faceWidth <= 1f || faceHeight <= 1f) {
            return null;
        }

        // Cuadrado con margen para evitar deformar el rostro al redimensionar.
        float sideFloat = Math.max(faceWidth, faceHeight) * 1.30f;
        int side = Math.round(sideFloat);

        if (side <= 0 || side > original.getWidth()
                || side > original.getHeight()) {
            return null;
        }

        float centerX = ((minX + maxX) / 2f) * original.getWidth();
        float centerY = ((minY + maxY) / 2f) * original.getHeight();

        int left = Math.round(centerX - side / 2f);
        int top = Math.round(centerY - side / 2f);

        left = Math.max(0, Math.min(left, original.getWidth() - side));
        top = Math.max(0, Math.min(top, original.getHeight() - side));

        Bitmap squareCrop = Bitmap.createBitmap(
                original, left, top, side, side
        );

        // Calcula la inclinación de la línea que une ambos ojos.
        NormalizedLandmark leftEye = landmarks.get(33);
        NormalizedLandmark rightEye = landmarks.get(263);

        float eye1X = leftEye.x() * original.getWidth() - left;
        float eye1Y = leftEye.y() * original.getHeight() - top;
        float eye2X = rightEye.x() * original.getWidth() - left;
        float eye2Y = rightEye.y() * original.getHeight() - top;

        float dx = eye2X - eye1X;
        float dy = eye2Y - eye1Y;

        if (Math.abs(dx) < 1f) {
            return squareCrop;
        }

        double angleDegrees = Math.toDegrees(Math.atan2(dy, dx));

        // Evita rotaciones extremas si la detección de ojos es inestable.
        if (!Double.isFinite(angleDegrees) || Math.abs(angleDegrees) > 30.0) {
            return squareCrop;
        }

        Matrix rotation = new Matrix();
        rotation.setRotate(
                (float) -angleDegrees,
                squareCrop.getWidth() / 2f,
                squareCrop.getHeight() / 2f
        );

        Bitmap rotated = Bitmap.createBitmap(
                squareCrop,
                0,
                0,
                squareCrop.getWidth(),
                squareCrop.getHeight(),
                rotation,
                true
        );

        if (rotated == squareCrop) {
            return squareCrop;
        }

        // La imagen rotada puede crecer; se toma nuevamente el cuadrado central.
        int cropSide = Math.min(
                squareCrop.getWidth(),
                Math.min(rotated.getWidth(), rotated.getHeight())
        );

        int cropLeft = (rotated.getWidth() - cropSide) / 2;
        int cropTop = (rotated.getHeight() - cropSide) / 2;

        Bitmap aligned = Bitmap.createBitmap(
                rotated, cropLeft, cropTop, cropSide, cropSide
        );

        if (rotated != squareCrop && !rotated.isRecycled()) {
            rotated.recycle();
        }

        if (!squareCrop.isRecycled()) {
            squareCrop.recycle();
        }

        return aligned;
    }

    private float[] getFaceEmbedding(Bitmap face) {
        Bitmap resized = Bitmap.createScaledBitmap(
                face,
                FACENET_INPUT_SIZE,
                FACENET_INPUT_SIZE,
                true
        );

        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(
                FACENET_INPUT_SIZE * FACENET_INPUT_SIZE * 3 * 4
        );
        inputBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[FACENET_INPUT_SIZE * FACENET_INPUT_SIZE];
        resized.getPixels(
                pixels, 0, FACENET_INPUT_SIZE,
                0, 0, FACENET_INPUT_SIZE, FACENET_INPUT_SIZE
        );

        // Se conserva la normalización que ya utilizaba el proyecto.
        // Debe confirmarse con la documentación/entrenamiento del modelo.
        for (int pixel : pixels) {
            float red = ((pixel >> 16) & 0xFF);
            float green = ((pixel >> 8) & 0xFF);
            float blue = (pixel & 0xFF);

            inputBuffer.putFloat((red - 127.5f) / 128.0f);
            inputBuffer.putFloat((green - 127.5f) / 128.0f);
            inputBuffer.putFloat((blue - 127.5f) / 128.0f);
        }

        inputBuffer.rewind();

        float[][] output = new float[1][EMBEDDING_SIZE];
        faceNetInterpreter.run(inputBuffer, output);

        if (resized != face && !resized.isRecycled()) {
            resized.recycle();
        }

        return output[0];
    }

    private boolean isValidEmbedding(float[] embedding) {
        if (embedding == null || embedding.length != EMBEDDING_SIZE) {
            return false;
        }

        double norm = 0.0;
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                return false;
            }
            norm += value * value;
        }

        return norm > 1.0e-12;
    }

    private float[] averageAndNormalizeEmbeddings(List<float[]> embeddings) {
        float[] average = new float[EMBEDDING_SIZE];

        for (float[] embedding : embeddings) {
            for (int i = 0; i < EMBEDDING_SIZE; i++) {
                average[i] += embedding[i];
            }
        }

        for (int i = 0; i < EMBEDDING_SIZE; i++) {
            average[i] /= embeddings.size();
        }

        double norm = 0.0;
        for (float value : average) {
            norm += value * value;
        }

        norm = Math.sqrt(norm);
        if (norm > 1.0e-12) {
            for (int i = 0; i < average.length; i++) {
                average[i] /= (float) norm;
            }
        }

        return average;
    }

    private float calculateCosineSimilarity(float[] vector1, float[] vector2) {
        if (vector1 == null || vector2 == null
                || vector1.length != vector2.length
                || vector1.length != EMBEDDING_SIZE) {
            return -1f;
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vector1.length; i++) {
            dot += vector1[i] * vector2[i];
            normA += vector1[i] * vector1[i];
            normB += vector2[i] * vector2[i];
        }

        if (normA <= 1.0e-12 || normB <= 1.0e-12) {
            return -1f;
        }

        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private void updateInstruction(String message) {
        runOnUiThread(() -> {
            if (instructionText != null) {
                instructionText.setText(message);

                if ("ACCESO CONCEDIDO".equalsIgnoreCase(message)) {
                    instructionText.setTextColor(Color.WHITE);
                    instructionText.setBackgroundColor(Color.rgb(46, 125, 50));
                } else if ("ROSTRO NO COINCIDE".equalsIgnoreCase(message)) {
                    instructionText.setTextColor(Color.WHITE);
                    instructionText.setBackgroundColor(Color.rgb(198, 40, 40));
                } else {
                    instructionText.setTextColor(Color.WHITE);
                    instructionText.setBackgroundColor(Color.TRANSPARENT);
                }
            }
        });
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults
        );

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                inicializarModelos();
                if (modelsReady) {
                    startCamera();
                } else {
                    updateInstruction("No se pudieron cargar los modelos");
                }
            } else {
                Toast.makeText(
                        this, "Permiso denegado", Toast.LENGTH_SHORT
                ).show();
                finish();
            }
        }
    }

    private void logout() {
        new AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Deseas cerrar la sesión actual?")
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Cerrar sesión", (dialog, which) -> {
                    appMode = AppMode.IDLE;
                    try {
                        com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> future =
                                ProcessCameraProvider.getInstance(this);
                        future.addListener(() -> {
                            try { future.get().unbindAll(); }
                            catch (Exception e) { Log.w(TAG, "No se pudo detener la cámara", e); }
                        }, ContextCompat.getMainExecutor(this));
                    } catch (Exception e) {
                        Log.w(TAG, "Error al detener cámara", e);
                    }
                    AuthStorage.logout();
                    startActivity(new android.content.Intent(this, LoginActivity.class));
                    finish();
                })
                .show();
    }

    private void closeModels() {
        if (faceLandmarker != null) {
            faceLandmarker.close();
            faceLandmarker = null;
        }

        if (faceNetInterpreter != null) {
            faceNetInterpreter.close();
            faceNetInterpreter = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        closeModels();
    }
}
