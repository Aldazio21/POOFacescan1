# POOFacescan1 – Sistema de autenticación facial

1. Descripción del proyecto

POOFacescan1 es una aplicación móvil desarrollada para Android que implementa un sistema de autenticación mediante reconocimiento facial.

El proyecto utiliza técnicas de procesamiento de imágenes y extracción de características biométricas para registrar y verificar la identidad de los usuarios. Además, incorpora autenticación local, almacenamiento protegido de información y un mecanismo básico de prueba de vida.

Su propósito es explorar la aplicación de tecnologías de reconocimiento facial en un sistema de autenticación móvil, considerando aspectos de seguridad, privacidad y experiencia de usuario.

2. Objetivos

- - Objetivo general

Desarrollar una aplicación Android que permita autenticar usuarios mediante reconocimiento facial, utilizando extracción y comparación de características biométricas.

- - Objetivos específicos

- Implementar un sistema de registro e inicio de sesión local.
- Permitir el registro facial mediante diferentes poses.
- Extraer características faciales mediante un modelo de reconocimiento.
- Comparar las características obtenidas con las referencias almacenadas.
- Incorporar un mecanismo básico de prueba de vida.
- Proteger la información de autenticación y los datos biométricos.
- Evaluar el funcionamiento de la aplicación mediante pruebas.

3. Funcionalidades principales

- - 3.1. Autenticación local

- Registro de cuentas independientes.
- Inicio y cierre de sesión.
- Protección de contraseñas mediante PBKDF2.
- Bloqueo temporal después de cinco intentos fallidos.

- - 3.2. Registro facial

- Solicitud de consentimiento informado.
- Captura guiada de tres poses faciales:
  - Rostro frontal.
  - Rostro girado hacia la izquierda.
  - Rostro girado hacia la derecha.
- Validación de la presencia de un único rostro.
- Generación y almacenamiento de tres referencias biométricas por usuario.

- - 3.3. Verificación facial

- Captura y procesamiento de la imagen facial.
- Extracción de características mediante MobileFaceNet.
- Generación de embeddings faciales de 192 dimensiones.
- Comparación mediante similitud coseno.
- Uso de un umbral de similitud configurado en 0.80.
- Aplicación de una regla de cuatro coincidencias de cinco comparaciones.

- - 3.4. Prueba de vida

La aplicación incorpora un mecanismo básico de detección de vida que solicita al usuario realizar acciones aleatorias, entre ellas:

- Girar la cabeza y regresar a la posición inicial.
- Realizar un parpadeo.

El sistema verifica la secuencia de acciones antes de continuar con el proceso de autenticación.

- - 3.5. Protección de información

- Almacenamiento independiente por cuenta.
- Protección de credenciales mediante derivación de claves.
- Almacenamiento cifrado de las referencias biométricas.
- Posibilidad de eliminar el registro facial.

4. Tecnologías utilizadas

| Tecnología | Función |
|---|---|
| Java : Lenguaje de programación |
| Android Studio: Entorno de desarrollo |
| Android SDK: Desarrollo de la aplicación Android |
| CameraX: Captura de imágenes mediante la cámara |
| MediaPipe: Detección y análisis facial |
| TensorFlow Lite: Ejecución del modelo de reconocimiento |
| MobileFaceNe: Extracción de características faciales |
| Git: Control de versiones |
| GitHub: Alojamiento del repositorio |

5. Arquitectura general

El funcionamiento de la aplicación se organiza en las siguientes etapas:

1. **Autenticación:** el usuario inicia sesión con sus credenciales.
2. **Acceso a cámara:** la aplicación solicita los permisos necesarios.
3. **Registro facial:** el usuario acepta el consentimiento y realiza las capturas guiadas.
4. **Procesamiento:** las imágenes se procesan para obtener características biométricas.
5. **Almacenamiento:** las referencias faciales se guardan de forma protegida y asociadas a la cuenta correspondiente.
6. **Prueba de vida:** se solicitan acciones para comprobar la respuesta del usuario.
7. **Verificación:** se comparan las características faciales con las referencias registradas.
8. **Resultado:** la aplicación determina si la verificación cumple los criterios configurados.

6. Requisitos para ejecutar el proyecto

- Android Studio.
- Git.
- Android SDK compatible con la configuración del proyecto.
- Dispositivo Android o emulador compatible.
- Cámara disponible para realizar las pruebas de reconocimiento facial.
- Archivos de modelos necesarios para el funcionamiento del reconocimiento facial.

7. Instalación

Clonar el repositorio

git clone (https://github.com/Aldazio21/POOFacescan1.git)

Abrir el proyecto

1. Abrir Android Studio.
2. Seleccionar la opción **Open**.
3. Elegir la carpeta descargada de POOFacescan1.
4. Esperar la sincronización de Gradle.
5. Conectar un dispositivo Android compatible o iniciar un emulador.
6. Ejecutar la aplicación.

8. Parámetros de verificación

La implementación utiliza los siguientes parámetros configurados:

| Parámetro | Valor |
|---|---|
| Referencias faciales por usuario | 3 |
| Dimensiones del embedding | 192 |
| Umbral de similitud | 0.80 |
| Regla de coincidencia | 4 de 5 |

Estos valores corresponden a la configuración implementada y no representan por sí solos una medición experimental de precisión.

9. Pruebas del sistema

El proyecto cuenta con un protocolo de pruebas que contempla:

- Inicio de sesión correcto e incorrecto.
- Bloqueo temporal por intentos fallidos.
- Separación de información entre cuentas.
- Permisos de cámara.
- Aceptación y rechazo del consentimiento.
- Prueba de vida.
- Registro de las tres poses faciales.
- Verificación del usuario registrado.
- Rechazo de usuarios diferentes.
- Eliminación de referencias biométricas.
- Comportamiento ante imágenes de baja calidad.
- Persistencia de información después de reiniciar la aplicación.

Los resultados experimentales se documentan en el protocolo de pruebas del proyecto.

10. Seguridad y limitaciones

La aplicación incorpora mecanismos de protección de credenciales y almacenamiento cifrado de referencias biométricas.

El mecanismo de prueba de vida implementado es básico y no garantiza protección absoluta frente a todos los métodos de suplantación.

Asimismo, el umbral de similitud debe evaluarse experimentalmente para determinar el comportamiento del sistema frente a usuarios legítimos y usuarios diferentes.

Los datos biométricos deben tratarse con consentimiento informado y mantenerse protegidos.

11. Control de versiones

El proyecto utiliza Git para el control de versiones y GitHub como plataforma de alojamiento del código fuente.

Repositorio: [POOFacescan1](https://github.com/Aldazio21/POOFacescan1.git)

## 12. Autor

Desarrollador: Aldazio21

Proyecto: POOFacescan1

Plataforma: Android
