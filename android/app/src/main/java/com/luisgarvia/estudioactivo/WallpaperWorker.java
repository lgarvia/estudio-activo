package com.luisgarvia.estudioactivo;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WallpaperWorker extends Worker {
    private static final String TAG = "WallpaperWorker";

    public WallpaperWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Iniciando rotación de fondo en segundo plano...");
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("EstudioActivoPrefs", Context.MODE_PRIVATE);
        
        String uriString = prefs.getString("folder_uri", null);
        if (uriString == null) {
            Log.e(TAG, "No hay carpeta seleccionada en las preferencias.");
            return Result.failure();
        }

        try {
            Uri treeUri = Uri.parse(uriString);
            DocumentFile rootDir = DocumentFile.fromTreeUri(context, treeUri);
            if (rootDir == null || !rootDir.exists()) {
                Log.e(TAG, "La carpeta seleccionada no existe o no tiene permisos de acceso.");
                return Result.failure();
            }

            DocumentFile[] files = rootDir.listFiles();
            List<DocumentFile> imageFiles = new ArrayList<>();
            for (DocumentFile file : files) {
                if (file.isFile()) {
                    String name = file.getName();
                    if (name != null) {
                        String nameLower = name.toLowerCase();
                        if (nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg")) {
                            imageFiles.add(file);
                        }
                    }
                }
            }

            if (imageFiles.isEmpty()) {
                Log.w(TAG, "No se encontraron imágenes en la carpeta.");
                return Result.success();
            }

            // Ordenar alfabéticamente por nombre de archivo para tener orden secuencial determinista
            Collections.sort(imageFiles, (f1, f2) -> {
                String n1 = f1.getName() != null ? f1.getName() : "";
                String n2 = f2.getName() != null ? f2.getName() : "";
                return n1.compareToIgnoreCase(n2);
            });

            // Leer modo de rotación: secuencial o aleatorio
            boolean randomMode = prefs.getBoolean("rotation_random", false);
            int currentIndex = prefs.getInt("rotation_index", 0);
            int targetIndex = currentIndex;

            if (randomMode) {
                // Elegir índice aleatorio diferente al anterior si es posible
                if (imageFiles.size() > 1) {
                    int lastAppliedIndex = prefs.getInt("last_applied_index", -1);
                    do {
                        targetIndex = (int) (Math.random() * imageFiles.size());
                    } while (targetIndex == lastAppliedIndex);
                } else {
                    targetIndex = 0;
                }
            } else {
                // Modo secuencial
                if (targetIndex >= imageFiles.size()) {
                    targetIndex = 0;
                }
            }

            DocumentFile targetFile = imageFiles.get(targetIndex);
            Uri imageUri = targetFile.getUri();
            Log.d(TAG, "Aplicando imagen: " + targetFile.getName() + " (Índice: " + targetIndex + ")");

            // Cargar imagen como Bitmap
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (inputStream != null) {
                inputStream.close();
            }

            if (bitmap == null) {
                Log.e(TAG, "No se pudo decodificar la imagen: " + targetFile.getName());
                return Result.failure();
            }

            // Aplicar fondo de pantalla
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
            String rotationMode = prefs.getString("rotation_mode", "lock"); // "lock", "home", "both"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if ("lock".equalsIgnoreCase(rotationMode)) {
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK);
                } else if ("home".equalsIgnoreCase(rotationMode)) {
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM);
                } else {
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK | WallpaperManager.FLAG_SYSTEM);
                }
            } else {
                wallpaperManager.setBitmap(bitmap);
            }

            // Guardar estado de rotación
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("last_applied_index", targetIndex);
            if (!randomMode) {
                editor.putInt("rotation_index", (targetIndex + 1) % imageFiles.size());
            }
            editor.apply();

            Log.d(TAG, "Fondo de pantalla actualizado con éxito.");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Error en doWork al rotar fondo de pantalla", e);
            return Result.retry();
        }
    }
}
