package com.luisgarvia.estudioactivo;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@CapacitorPlugin(name = "WallpaperPlugin")
public class WallpaperPlugin extends Plugin {
    private static final String TAG = "WallpaperPlugin";

    @PluginMethod
    public void selectFolder(PluginCall call) {
        saveCall(call);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(call, intent, "selectFolderResult");
    }

    @ActivityCallback
    private void selectFolderResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Uri treeUri = result.getData().getData();
            try {
                getContext().getContentResolver().takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                );

                SharedPreferences prefs = getContext().getSharedPreferences("EstudioActivoPrefs", Context.MODE_PRIVATE);
                prefs.edit().putString("folder_uri", treeUri.toString()).apply();

                DocumentFile rootDir = DocumentFile.fromTreeUri(getContext(), treeUri);
                String folderName = rootDir != null ? rootDir.getName() : "Carpeta seleccionada";
                
                int imgCount = 0;
                if (rootDir != null) {
                    for (DocumentFile file : rootDir.listFiles()) {
                        if (file.isFile()) {
                            String name = file.getName();
                            if (name != null) {
                                String nameLower = name.toLowerCase();
                                if (nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg")) {
                                    imgCount++;
                                }
                            }
                        }
                    }
                }

                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("folderUri", treeUri.toString());
                ret.put("folderName", folderName);
                ret.put("totalImages", imgCount);
                call.resolve(ret);

            } catch (Exception e) {
                Log.e(TAG, "Error al tomar permisos persistentes de la carpeta", e);
                call.reject("Error al acceder a la carpeta: " + e.getMessage());
            }
        } else {
            call.reject("Selección cancelada");
        }
    }

    @PluginMethod
    public void getFolderImages(PluginCall call) {
        SharedPreferences prefs = getContext().getSharedPreferences("EstudioActivoPrefs", Context.MODE_PRIVATE);
        String uriString = prefs.getString("folder_uri", null);
        if (uriString == null) {
            call.reject("No se ha seleccionado ninguna carpeta.");
            return;
        }

        try {
            Uri treeUri = Uri.parse(uriString);
            DocumentFile rootDir = DocumentFile.fromTreeUri(getContext(), treeUri);
            if (rootDir == null || !rootDir.exists()) {
                call.reject("La carpeta no existe o no tiene permisos de lectura.");
                return;
            }

            JSArray images = new JSArray();
            DocumentFile[] files = rootDir.listFiles();
            List<DocumentFile> list = new ArrayList<>();
            for (DocumentFile file : files) {
                if (file.isFile()) {
                    String name = file.getName();
                    if (name != null) {
                        String nameLower = name.toLowerCase();
                        if (nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg")) {
                            list.add(file);
                        }
                    }
                }
            }

            // Ordenar alfabéticamente
            Collections.sort(list, (f1, f2) -> {
                String n1 = f1.getName() != null ? f1.getName() : "";
                String n2 = f2.getName() != null ? f2.getName() : "";
                return n1.compareToIgnoreCase(n2);
            });

            for (DocumentFile file : list) {
                JSObject img = new JSObject();
                img.put("name", file.getName());
                img.put("uri", file.getUri().toString());
                img.put("size", file.length());
                images.put(img);
            }

            JSObject ret = new JSObject();
            ret.put("images", images);
            call.resolve(ret);

        } catch (Exception e) {
            call.reject("Error al listar imágenes: " + e.getMessage());
        }
    }

    @PluginMethod
    public void loadImage(PluginCall call) {
        String uriString = call.getString("uri");
        if (uriString == null) {
            call.reject("Falta la URI de la imagen.");
            return;
        }

        try {
            Uri uri = Uri.parse(uriString);
            InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                call.reject("No se pudo abrir el stream de la imagen.");
                return;
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            byte[] bytes = outputStream.toByteArray();
            inputStream.close();

            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            JSObject ret = new JSObject();
            ret.put("base64", base64);
            call.resolve(ret);

        } catch (Exception e) {
            call.reject("Error al cargar la imagen: " + e.getMessage());
        }
    }

    @PluginMethod
    public void setWallpaperFromUri(PluginCall call) {
        String uriString = call.getString("uri");
        String destination = call.getString("destination"); // "home", "lock", or "both"
        if (uriString == null) {
            call.reject("Falta la URI de la imagen.");
            return;
        }

        try {
            Uri uri = Uri.parse(uriString);
            InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (inputStream != null) {
                inputStream.close();
            }

            if (bitmap == null) {
                call.reject("No se pudo decodificar la imagen.");
                return;
            }

            WallpaperManager wallpaperManager = WallpaperManager.getInstance(getContext());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if ("lock".equalsIgnoreCase(destination)) {
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK);
                } else if ("home".equalsIgnoreCase(destination)) {
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM);
                } else {
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK | WallpaperManager.FLAG_SYSTEM);
                }
            } else {
                wallpaperManager.setBitmap(bitmap);
            }

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);

        } catch (Exception e) {
            call.reject("Error al aplicar fondo de pantalla: " + e.getMessage());
        }
    }

    @PluginMethod
    public void setAutorotate(PluginCall call) {
        Boolean enable = call.getBoolean("enable", false);
        Integer intervalMinutes = call.getInt("interval", 60);
        String mode = call.getString("mode", "lock");
        Boolean random = call.getBoolean("random", false);

        SharedPreferences prefs = getContext().getSharedPreferences("EstudioActivoPrefs", Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean("rotation_enabled", enable)
            .putInt("rotation_interval", intervalMinutes)
            .putString("rotation_mode", mode)
            .putBoolean("rotation_random", random)
            .apply();

        WorkManager workManager = WorkManager.getInstance(getContext());
        workManager.cancelUniqueWork("WallpaperRotationJob");

        if (enable) {
            // PeriodicWorkRequest exige un mínimo de 15 minutos en Android
            int finalInterval = Math.max(intervalMinutes, 15);
            Log.d(TAG, "Programando rotación en segundo plano cada " + finalInterval + " minutos (Modo: " + mode + ")");

            PeriodicWorkRequest rotationRequest =
                new PeriodicWorkRequest.Builder(WallpaperWorker.class, finalInterval, TimeUnit.MINUTES)
                    .build();

            workManager.enqueueUniquePeriodicWork(
                "WallpaperRotationJob",
                ExistingPeriodicWorkPolicy.UPDATE,
                rotationRequest
            );
        } else {
            Log.d(TAG, "Rotación automática desactivada.");
        }

        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void forceRotate(PluginCall call) {
        try {
            WorkManager workManager = WorkManager.getInstance(getContext());
            OneTimeWorkRequest oneTimeWorkRequest = new OneTimeWorkRequest.Builder(WallpaperWorker.class).build();
            workManager.enqueue(oneTimeWorkRequest);

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Error al forzar rotación: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getRotationStatus(PluginCall call) {
        SharedPreferences prefs = getContext().getSharedPreferences("EstudioActivoPrefs", Context.MODE_PRIVATE);
        String uriString = prefs.getString("folder_uri", null);
        
        JSObject ret = new JSObject();
        ret.put("folderUri", uriString);
        
        String folderName = null;
        int imgCount = 0;
        if (uriString != null) {
            try {
                Uri treeUri = Uri.parse(uriString);
                DocumentFile rootDir = DocumentFile.fromTreeUri(getContext(), treeUri);
                if (rootDir != null && rootDir.exists()) {
                    folderName = rootDir.getName();
                    for (DocumentFile file : rootDir.listFiles()) {
                        if (file.isFile()) {
                            String name = file.getName();
                            if (name != null) {
                                String nameLower = name.toLowerCase();
                                if (nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg")) {
                                    imgCount++;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error al obtener info de carpeta en getRotationStatus", e);
            }
        }
        
        ret.put("folderName", folderName);
        ret.put("totalImages", imgCount);
        ret.put("rotationEnabled", prefs.getBoolean("rotation_enabled", false));
        ret.put("rotationInterval", prefs.getInt("rotation_interval", 60));
        ret.put("rotationMode", prefs.getString("rotation_mode", "lock"));
        ret.put("rotationRandom", prefs.getBoolean("rotation_random", false));
        ret.put("currentIndex", prefs.getInt("rotation_index", 0));
        
        call.resolve(ret);
    }

    @PluginMethod
    public void setWallpaper(PluginCall call) {
        // Método heredado para compatibilidad base64
        String base64Image = call.getString("base64");
        String destination = call.getString("destination");

        if (base64Image == null) {
            call.reject("Falta la imagen en base64");
            return;
        }

        try {
            byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

            if (bitmap == null) {
                call.reject("No se pudo decodificar la imagen");
                return;
            }

            WallpaperManager wallpaperManager = WallpaperManager.getInstance(getContext());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if ("lock".equalsIgnoreCase(destination)) {
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK);
                } else if ("home".equalsIgnoreCase(destination)) {
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM);
                } else {
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK | WallpaperManager.FLAG_SYSTEM);
                }
            } else {
                wallpaperManager.setBitmap(bitmap);
            }

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);

        } catch (Exception e) {
            call.reject("Error: " + e.getMessage());
        }
    }
}
