package com.example.camarageolocalizacion;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CameraActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private Button btnCapture;
    private TextView tvGpsStatus;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location currentLocation;
    private File photoFile;
    private Uri photoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        btnCapture = findViewById(R.id.btnCapture);
        tvGpsStatus = findViewById(R.id.tvGpsStatus);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        setupLocationCallback();
        checkGpsEnabled();
        requestLocationUpdates();

        btnCapture.setOnClickListener(v -> dispatchTakePictureIntent());
    }

    private void checkGpsEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!gpsEnabled) {
            tvGpsStatus.setText("GPS: ❌ Desactivado");

            new AlertDialog.Builder(this)
                    .setTitle("GPS Desactivado")
                    .setMessage("Por favor, activa el GPS para agregar geolocalización a tus fotos.")
                    .setPositiveButton("Activar", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        }
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        currentLocation = location;
                        tvGpsStatus.setText(String.format(Locale.getDefault(),
                                "GPS: ✓ %.6f, %.6f\nPrecisión: %.0fm",
                                location.getLatitude(),
                                location.getLongitude(),
                                location.getAccuracy()));
                    }
                }
            }
        };
    }

    private void requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            tvGpsStatus.setText("GPS: ❌ Sin permiso");
            return;
        }

        tvGpsStatus.setText("GPS: ⏳ Buscando señal...");

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .setMaxUpdateDelayMillis(10000)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                getMainLooper());

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLocation = location;
                        tvGpsStatus.setText(String.format(Locale.getDefault(),
                                "GPS: ✓ %.6f, %.6f",
                                location.getLatitude(),
                                location.getLongitude()));
                    }
                });
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                photoFile = createImageFile();

                if (photoFile != null) {
                    photoUri = FileProvider.getUriForFile(this,
                            getApplicationContext().getPackageName() + ".fileprovider",
                            photoFile);

                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                }
            } catch (IOException ex) {
                Toast.makeText(this, "❌ Error al crear archivo: " + ex.getMessage(),
                        Toast.LENGTH_LONG).show();
                ex.printStackTrace();
            }
        } else {
            Toast.makeText(this, "❌ No hay aplicación de cámara disponible",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";

        // Usar directorio temporal de la app
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs();
        }

        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );

        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            if (photoFile != null && photoFile.exists()) {
                Toast.makeText(this, "⏳ Procesando foto...", Toast.LENGTH_SHORT).show();

                // Primero agregar metadatos EXIF al archivo temporal
                addExifData(photoFile.getAbsolutePath());

                // Luego copiar a la galería
                copyToGallery();
            } else {
                Toast.makeText(this, "❌ Error: archivo no encontrado", Toast.LENGTH_SHORT).show();
            }
        } else if (resultCode == RESULT_CANCELED) {
            Toast.makeText(this, "❌ Captura cancelada", Toast.LENGTH_SHORT).show();
            if (photoFile != null && photoFile.exists()) {
                photoFile.delete();
            }
        }
    }

    private void addExifData(String filePath) {
        try {
            ExifInterface exif = new ExifInterface(filePath);

            // Agregar fecha y hora
            String dateTime = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());
            exif.setAttribute(ExifInterface.TAG_DATETIME, dateTime);
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTime);
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateTime);

            // Agregar geolocalización si está disponible
            if (currentLocation != null) {
                double latitude = currentLocation.getLatitude();
                double longitude = currentLocation.getLongitude();
                double altitude = currentLocation.getAltitude();

                exif.setLatLong(latitude, longitude);

                // Convertir altitud a formato racional
                String altitudeRef = altitude >= 0 ? "0" : "1";
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE,
                        String.valueOf(Math.abs(altitude)));
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, altitudeRef);

                exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP,
                        new SimpleDateFormat("yyyy:MM:dd", Locale.getDefault()).format(new Date()));
                exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP,
                        new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));

                exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS");

                Toast.makeText(this, "✓ Geolocalización agregada", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "⚠ Sin datos de GPS", Toast.LENGTH_SHORT).show();
            }

            // Metadata adicional
            exif.setAttribute(ExifInterface.TAG_MAKE, "Android");
            exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL);
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "CamaraGeolocalizacion");

            // IMPORTANTE: Guardar los cambios
            exif.saveAttributes();

        } catch (IOException e) {
            Toast.makeText(this, "⚠ Error al agregar metadatos: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void copyToGallery() {
        try {
            String fileName = photoFile.getName();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: Usar MediaStore
                copyToGalleryMediaStore(fileName);
            } else {
                // Android 9 y anteriores: Copiar a DCIM directamente
                copyToGalleryDirect(fileName);
            }

            // Eliminar archivo temporal
            if (photoFile.exists()) {
                photoFile.delete();
            }

            Toast.makeText(this, "✓ Foto guardada en galería", Toast.LENGTH_LONG).show();

            setResult(RESULT_OK);
            finish();

        } catch (Exception e) {
            Toast.makeText(this, "❌ Error al guardar en galería: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void copyToGalleryMediaStore(String fileName) throws IOException {
        ContentResolver resolver = getContentResolver();
        ContentValues contentValues = new ContentValues();

        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DCIM + "/Camera");
        contentValues.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

        if (imageUri != null) {
            try (OutputStream outputStream = resolver.openOutputStream(imageUri);
                 FileInputStream inputStream = new FileInputStream(photoFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                contentValues.clear();
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(imageUri, contentValues, null, null);
            }
        }
    }

    private void copyToGalleryDirect(String fileName) throws IOException {
        File galleryDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), "Camera");

        if (!galleryDir.exists()) {
            galleryDir.mkdirs();
        }

        File destFile = new File(galleryDir, fileName);

        try (FileInputStream inputStream = new FileInputStream(photoFile);
             java.io.FileOutputStream outputStream = new java.io.FileOutputStream(destFile)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        // Notificar al sistema
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(destFile);
        mediaScanIntent.setData(contentUri);
        sendBroadcast(mediaScanIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkGpsEnabled();
        requestLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}