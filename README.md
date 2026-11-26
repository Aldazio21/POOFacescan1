# POOFacescan1

## 1. Descripción del proyecto

POOFacescan1 es una aplicación móvil desarrollada para Android que implementa un sistema de autenticación mediante reconocimiento facial.

El proyecto utiliza técnicas de procesamiento de imágenes y extracción de características biométricas para registrar y verificar la identidad de los usuarios. Además, incorpora autenticación local, almacenamiento protegido de información y un mecanismo básico de prueba de vida.

Su propósito es explorar la aplicación de tecnologías de reconocimiento facial en un sistema de autenticación móvil, considerando aspectos de seguridad, privacidad y experiencia de usuario.

## 2. Objetivos

### Objetivo general

Desarrollar una aplicación Android que permita autenticar usuarios mediante reconocimiento facial, utilizando extracción y comparación de características biométricas.

### Objetivos específicos

1. Implementar un sistema de registro e inicio de sesión local.
2. Permitir el registro facial mediante diferentes poses.
3. Extraer características faciales mediante un modelo de reconocimiento.
4. Comparar las características obtenidas con las referencias almacenadas.
5. Incorporar un mecanismo básico de prueba de vida.
6. Proteger la información de autenticación y los datos biométricos.
7. Evaluar el funcionamiento de la aplicación mediante pruebas.

## 3. Funcionalidades principales

### 3.1. Autenticación local

- Registro de cuentas independientes.
- Inicio y cierre de sesión.
- Protección de contraseñas mediante derivación iterativa de claves con HMAC-SHA256 y salt aleatorio.
- Bloqueo temporal después de cinco intentos fallidos.
- Aislamiento de la información correspondiente a cada cuenta.

### 3.2. Registro facial

- Solicitud de consentimiento informado.
- Captura guiada de tres poses faciales:
  - Rostro frontal.
  - Rostro girado hacia la izquierda.
  - Rostro girado hacia la derecha.
- Validación de la presencia de un único rostro.
- Generación y almacenamiento de tres referencias biométricas por usuario.

### 3.3. Verificación facial

1. Captura y procesamiento de la imagen facial.
2. Extracción de características mediante MobileFaceNet.
3. Generación de embeddings faciales de 192 dimensiones.
4. Comparación mediante similitud coseno.
5. Uso de un umbral de similitud configurado en 0.80.
6. Aplicación de una regla de cuatro coincidencias de cinco comparaciones.

### 3.4. Prueba de vida

La aplicación incorpora un mecanismo básico de detección de vida que solicita al usuario realizar acciones en orden aleatorio, entre ellas:

- Girar la cabeza y regresar a la posición inicial.
- Realizar un parpadeo.

El sistema verifica la secuencia de acciones antes de continuar con el proceso de autenticación.

### 3.5. Protección de información

- Almacenamiento independiente por cuenta.
- Protección de credenciales mediante derivación de claves.
- Almacenamiento cifrado de las referencias biométricas.
- Registro del consentimiento informado.
- Posibilidad de eliminar las referencias del registro facial.

## 4. Tecnologías utilizadas

| Tecnología | Función |
|---|---|
| Java | Lenguaje de programación |
| Android Studio | Entorno de desarrollo |
| Android SDK | Desarrollo de la aplicación Android |
| CameraX | Captura de imágenes mediante la cámara |
| MediaPipe | Detección y análisis facial |
| TensorFlow Lite | Ejecución del modelo de reconocimiento |
| MobileFaceNet | Extracción de características faciales |
| OpenCV | Procesamiento de imágenes |
| AndroidX Security Crypto | Protección del almacenamiento local |
| Git | Control de versiones |
| GitHub | Alojamiento del repositorio |

## 5. Arquitectura general

El funcionamiento de la aplicación se organiza en las siguientes etapas:

1. **Autenticación:** el usuario inicia sesión con sus credenciales.
2. **Acceso a cámara:** la aplicación solicita los permisos necesarios.
3. **Registro facial:** el usuario acepta el consentimiento y realiza las capturas guiadas.
4. **Procesamiento:** las imágenes se procesan para obtener características biométricas.
5. **Almacenamiento:** las referencias faciales se guardan de forma protegida y asociadas a la cuenta correspondiente.
6. **Prueba de vida:** se solicitan acciones para comprobar la respuesta del usuario.
7. **Verificación:** se comparan las características faciales con las referencias registradas.
8. **Resultado:** la aplicación determina si la verificación cumple los criterios configurados.

## 6. Requisitos para ejecutar el proyecto

- Android Studio.
- Git.
- Android SDK compatible con la configuración del proyecto.
- Dispositivo Android o emulador compatible.
- Cámara disponible para realizar las pruebas de reconocimiento facial.
- Archivos de modelos necesarios para el funcionamiento del reconocimiento facial.
- Conexión a internet durante la descarga inicial de dependencias de Gradle.

## 7. Instalación y ejecución desde el código fuente

### Clonar el repositorio

```bash
git clone https://github.com/Aldazio21/POOFacescan1.git
```

### Abrir el proyecto

1. Abrir Android Studio.
2. Seleccionar la opción **Open**.
3. Elegir la carpeta descargada de POOFacescan1.
4. Esperar la sincronización de Gradle.
5. Conectar un dispositivo Android compatible o iniciar un emulador.
6. Ejecutar la aplicación desde Android Studio.

## 8. Descargar e instalar el APK desde GitHub Actions

El repositorio genera un APK de depuración mediante GitHub Actions. El archivo se publica como un artefacto del flujo de compilación con el nombre **POOFacescan1-debug-apk**.

### Pasos para descargarlo

1. Ingresar al repositorio: [POOFacescan1](https://github.com/Aldazio21/POOFacescan1).
2. Abrir la pestaña **Actions**.
3. En la lista de flujos de trabajo, seleccionar **Build Android APK**.
4. Abrir la ejecución más reciente que tenga el estado **Success** (marca verde).
5. Desplazarse hasta la sección **Artifacts** al final de la página.
6. Seleccionar **POOFacescan1-debug-apk** para descargar el archivo ZIP.
7. Una vez descargado, extraer el contenido del ZIP. Dentro se encuentra el archivo APK generado.
8. Transferir el APK al dispositivo Android si la descarga se realizó desde un computador.
9. Abrir el archivo APK desde el dispositivo y seguir las indicaciones de instalación de Android.

### Si Android bloquea la instalación

Al tratarse de un APK descargado fuera de Google Play, Android puede solicitar autorización para instalar aplicaciones desde esa fuente. Si aparece ese aviso, revisar los ajustes del dispositivo y permitir la instalación para la aplicación desde la que se está abriendo el APK (por ejemplo, el navegador o el administrador de archivos). Esta autorización puede desactivarse nuevamente después de la instalación.

### Consideraciones importantes

- El artefacto disponible corresponde a una compilación **debug**; no es una versión de lanzamiento firmada para distribución en Google Play.
- GitHub Actions conserva el artefacto durante un periodo limitado (14 días según la configuración actual del flujo). Si ya no aparece en la sección **Artifacts**, será necesario ejecutar nuevamente el flujo de compilación.
- Para descargar el artefacto desde un repositorio privado, es necesario iniciar sesión en GitHub y contar con permisos de acceso al repositorio.
- La descarga del APK no instala automáticamente la aplicación. Es necesario extraer el ZIP y abrir el APK en el dispositivo Android.
- Se recomienda descargar el artefacto únicamente desde una ejecución exitosa del flujo oficial del repositorio.

### Generar el APK localmente (opcional)

También es posible compilar la aplicación desde Android Studio o desde la terminal, en la carpeta raíz del proyecto, usando:

```powershell
.\\gradlew.bat assembleDebug
```

Si la compilación finaliza correctamente, el APK de depuración se genera normalmente en:

```text
app/build/outputs/apk/debug/
```

## 9. Parámetros de verificación

La implementación utiliza los siguientes parámetros configurados:

| Parámetro | Valor |
|---|---:|
| Referencias faciales por usuario | 3 |
| Dimensiones del embedding | 192 |
| Umbral de similitud | 0.80 |
| Regla de coincidencia | 4 de 5 |

Estos valores corresponden a la configuración implementada y no representan por sí solos una medición experimental de precisión.

## 10. Pruebas del sistema

El proyecto cuenta con un protocolo de pruebas que contempla:

- Inicio de sesión correcto e incorrecto.
- Bloqueo temporal por intentos fallidos.
- Separación de información entre cuentas.
- Permisos de cámara.
- Aceptación y rechazo del consentimiento.
- Prueba de vida y orden aleatorio de acciones.
- Registro de las tres poses faciales.
- Verificación del usuario registrado.
- Rechazo de usuarios diferentes.
- Eliminación de referencias biométricas.
- Comportamiento ante imágenes de baja calidad.
- Persistencia de información después de reiniciar la aplicación.

### Resultados de las pruebas automatizadas

Se ejecutaron pruebas instrumentadas en un dispositivo Android 16, obteniendo los siguientes resultados:

| Clase de prueba | Pruebas | Fallidas | Errores |
|---|---:|---:|---:|
| AuthStorageInstrumentedTest | 6 | 0 | 0 |
| BiometricStorageInstrumentedTest | 9 | 0 | 0 |
| ExampleInstrumentedTest | 1 | 0 | 0 |
| **Total** | **16** | **0** | **0** |

**Resultado:** las 16 pruebas instrumentadas finalizaron correctamente, sin fallos, errores ni pruebas omitidas.

Para ejecutar las pruebas instrumentadas desde la terminal de Android Studio:

```powershell
.\\gradlew.bat connectedDebugAndroidTest
```

El informe generado por Gradle puede consultarse en:

```text
app/build/reports/androidTests/connected/debug/index.html
```

Estos resultados validan los casos cubiertos por las pruebas automatizadas, pero no representan una evaluación experimental completa de la precisión del reconocimiento facial.

## 11. Seguridad y limitaciones

La aplicación incorpora mecanismos de protección de credenciales, aislamiento de información por cuenta y almacenamiento cifrado de referencias biométricas.

El mecanismo de prueba de vida implementado es básico y no garantiza protección absoluta frente a todos los métodos de suplantación.

Asimismo, el umbral de similitud debe evaluarse experimentalmente para determinar el comportamiento del sistema frente a usuarios legítimos y usuarios diferentes.

Las 16 pruebas instrumentadas aprobadas validan los comportamientos cubiertos por dichas pruebas, pero no certifican la seguridad integral del sistema ni su resistencia frente a todos los ataques.

Los datos biométricos deben tratarse con consentimiento informado y mantenerse protegidos.

## 12. Control de versiones

El proyecto utiliza Git para el control de versiones y GitHub como plataforma de alojamiento del código fuente.

**Repositorio:** [POOFacescan1](https://github.com/Aldazio21/POOFacescan1)

**Rama principal:** `main`

## 13. Autor

Desarrollador: Aldazio21

Proyecto: POOFacescan1

Plataforma: Android
