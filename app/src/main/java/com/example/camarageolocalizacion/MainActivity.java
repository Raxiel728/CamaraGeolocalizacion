package com.example.camarageolocalizacion;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int CAMERA_ACTIVITY_REQUEST = 200;

    private RecyclerView recyclerViewPhotos;
    private PhotoAdapter photoAdapter;
    private List<Photo> photoList;
    private FloatingActionButton btnTakePhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar vistas
        recyclerViewPhotos = findViewById(R.id.recyclerViewPhotos);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);

        // Inicializar lista y adaptador
        photoList = new ArrayList<>();
        photoAdapter = new PhotoAdapter(photoList);

        // Configurar RecyclerView con Grid de 2 columnas
        recyclerViewPhotos.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerViewPhotos.setAdapter(photoAdapter);

        // Configurar botón para abrir cámara
        btnTakePhoto.setOnClickListener(v -> {
            if (checkPermissions()) {
                openCameraActivity();
            } else {
                requestPermissions();
            }
        });

        // Verificar permisos y cargar fotos
        if (checkPermissions()) {
            loadPhotos();
        } else {
            requestPermissions();
        }
    }

    /**
     * Verifica si todos los permisos necesarios están concedidos
     */
    private boolean checkPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // Permiso de cámara
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        // Permisos de ubicación
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // Permisos de almacenamiento según la versión de Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            // Android 12 y anteriores
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
            }
        }

        return permissionsNeeded.isEmpty();
    }

    /**
     * Solicita los permisos necesarios
     */
    private void requestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        permissionsToRequest.add(Manifest.permission.CAMERA);
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        ActivityCompat.requestPermissions(this,
                permissionsToRequest.toArray(new String[0]),
                PERMISSION_REQUEST_CODE);
    }

    /**
     * Maneja la respuesta de la solicitud de permisos
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                loadPhotos();
                Toast.makeText(this, "✓ Permisos concedidos", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "⚠ Se necesitan todos los permisos para usar la aplicación",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Carga todas las fotos del dispositivo usando MediaStore
     */
    private void loadPhotos() {
        photoList.clear();

        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        String[] projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE
        };

        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getContentResolver().query(
                collection,
                projection,
                null,
                null,
                sortOrder
        )) {
            if (cursor != null) {
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);

                while (cursor.moveToNext()) {
                    String path = cursor.getString(dataColumn);
                    String name = cursor.getString(nameColumn);
                    long size = cursor.getLong(sizeColumn);

                    // Verificar que el archivo existe y tiene contenido
                    File file = new File(path);
                    if (file.exists() && file.canRead() && size > 0) {
                        photoList.add(new Photo(path, name));
                    }
                }

                Toast.makeText(this, "📷 " + photoList.size() + " fotos encontradas",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "❌ Error al cargar fotos: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }

        photoAdapter.updatePhotos(photoList);
    }

    /**
     * Abre la actividad de cámara
     */
    private void openCameraActivity() {
        Intent intent = new Intent(this, CameraActivity.class);
        startActivityForResult(intent, CAMERA_ACTIVITY_REQUEST);
    }

    /**
     * Recibe el resultado de la actividad de cámara
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_ACTIVITY_REQUEST && resultCode == RESULT_OK) {
            // Recargar fotos después de tomar una nueva
            loadPhotos();
            Toast.makeText(this, "✓ Foto guardada correctamente", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Recarga las fotos cuando la actividad vuelve al primer plano
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermissions()) {
            loadPhotos();
        }
    }
}