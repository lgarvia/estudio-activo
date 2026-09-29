# Estudio Activo — Soberanía Cognitiva 📱🧠

Aplicación móvil desarrollada con **Capacitor 8** y **Android Nativo** para el aprendizaje pasivo y la soberanía cognitiva, diseñada para convertir notas y fichas de estudio (provenientes de bóvedas de Obsidian o carpetas de imágenes) en fondos de pantalla dinámicos para el móvil con rotación automática periódica.

---

## 🌟 Características Principales

- **Microaprendizaje Ambiental (Passive Recall)**: Convierte cada desbloqueo del móvil en una oportunidad de fijación de conceptos mediante repetición espaciada inconsciente.
- **Integración con Carpetas Locales / Obsidian**: Selección directa del directorio de fichas o imágenes mediante el selector nativo de Android (`ACTION_OPEN_DOCUMENT_TREE`) con permisos persistentes de lectura.
- **Motor Nativo de Fondos de Pantalla**: Soporte nativo para aplicar fondos a:
  - Pantalla de inicio (*Home screen*)
  - Pantalla de bloqueo (*Lock screen*)
  - Ambas pantallas a la vez
- **Worker Autónomo en Segundo Plano**: Implementado con **Android Jetpack `WorkManager`** (`PeriodicWorkRequest` y `OneTimeWorkRequest`), garantizando que la rotación se ejecute periódicamente (intervalos configurables: 15 min, 30 min, 1 h, etc.) incluso cuando la aplicación está cerrada, optimizando el consumo de batería.
- **Interfaz Moderna y Fluida**:
  - Tema oscuro de inspiración Obsidian / GitHub Dark.
  - Tipografías modernas (*Outfit* y *JetBrains Mono*).
  - Carrusel interactivo con vista previa a resolución completa.
  - Forzado manual inmediato de siguiente ficha.
  - Sistema de notificaciones *Toast* para confirmaciones de estado.

---

## 🏗️ Arquitectura del Proyecto

```
aplicación móvil/
├── www/                                # Capa Web (Frontend)
│   ├── index.html                      # UI completa (CSS3 + Vanilla JS SPA)
│   ├── icon.png                        # Icono de la aplicación
│   ├── manifest.json                   # Manifiesto PWA
│   └── sw.js                           # Service Worker
├── android/                            # Capa Nativa Android
│   ├── app/src/main/
│   │   ├── java/com/luisgarvia/estudioactivo/
│   │   │   ├── MainActivity.java       # Bridge Activity de Capacitor
│   │   │   ├── WallpaperPlugin.java    # Plugin Capacitor: permisos SAF, selección de carpeta y API Wallpaper
│   │   │   └── WallpaperWorker.java    # Worker en segundo plano (Android Jetpack WorkManager)
│   │   ├── AndroidManifest.xml         # Permisos SET_WALLPAPER e INTERNET
│   │   └── res/                        # Drawables, splash screens, mipmaps y layouts
│   ├── build.gradle                    # Configuración de compilación Gradle
│   └── variables.gradle                # Versiones de dependencias y SDK
├── capacitor.config.json               # Configuración de runtime de Capacitor
└── package.json                        # Metadatos del proyecto y dependencias
```

---

## 🚀 Requisitos Previos

- **Node.js** (v18 o superior recomendado)
- **Java Development Kit (JDK)**: JDK 17 o 21
- **Android SDK**: Plataforma Android 34 / 36

---

## 🛠️ Instalación y Configuración

1. **Instalar dependencias del proyecto**:
   ```bash
   npm install
   ```

2. **Sincronizar el proyecto web con la plataforma Android**:
   ```bash
   npx cap sync android
   ```

3. **Compilar el APK de desarrollo (vía terminal)**:
   ```bash
   cd android
   ./gradlew assembleDebug
   ```
   El archivo generado se ubicará en:
   `android/app/build/outputs/apk/debug/app-debug.apk`

4. **Instalar directamente en el móvil (por USB con ADB)**:
   ```bash
   adb install -r android/app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 📱 Uso de la Aplicación

1. **Seleccionar Carpeta**: Pulsa sobre "Vincular Bóveda / Carpeta" y selecciona la carpeta en tu almacenamiento interno donde exportes o tengas tus fichas de estudio (imágenes en formato PNG/JPG).
2. **Configurar Rotación**:
   - Activa el interruptor **Rotación Automática**.
   - Escoge el intervalo deseado (ej. cada 15 o 30 minutos).
   - Elige el destino del fondo (Pantalla de bloqueo, inicio o ambas).
   - Opcionalmente activa el modo aleatorio.
3. **Guardar Configuración**: La aplicación programará el `WorkManager` de Android para mantener el flujo de aprendizaje activo de forma autónoma.

---

## 👤 Autor

- **Luis Garvía Vega** ([@lgarvia](https://github.com/lgarvia))
- ICADE / Universidad Pontificia Comillas
