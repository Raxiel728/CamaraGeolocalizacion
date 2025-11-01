package com.example.camarageolocalizacion;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {

    private List<Photo> photoList;

    public PhotoAdapter(List<Photo> photoList) {
        this.photoList = photoList;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        Photo photo = photoList.get(position);

        // Verificar que el archivo existe
        File file = new File(photo.getPath());
        if (!file.exists() || !file.canRead()) {
            holder.tvFileName.setText(photo.getName());
            holder.tvError.setText("❌ Archivo no disponible");
            holder.tvError.setVisibility(View.VISIBLE);
            holder.imageView.setImageBitmap(null);
            return;
        }

        // Cargar imagen con escala reducida
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            options.inJustDecodeBounds = false;

            Bitmap bitmap = BitmapFactory.decodeFile(photo.getPath(), options);

            if (bitmap != null) {
                holder.imageView.setImageBitmap(bitmap);
            } else {
                holder.imageView.setImageBitmap(null);
                holder.tvError.setText("❌ Error al decodificar");
                holder.tvError.setVisibility(View.VISIBLE);
                return;
            }
        } catch (Exception e) {
            holder.imageView.setImageBitmap(null);
            holder.tvError.setText("❌ Error al cargar imagen");
            holder.tvError.setVisibility(View.VISIBLE);
            return;
        }

        // Establecer nombre del archivo
        holder.tvFileName.setText(photo.getName());

        // Leer metadatos EXIF
        try {
            ExifInterface exif = new ExifInterface(photo.getPath());

            // Leer geolocalización
            float[] latLong = new float[2];
            boolean hasGeo = exif.getLatLong(latLong);

            // Leer fecha/hora
            String dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME);

            if (hasGeo) {
                // Mostrar badge GPS
                holder.tvGpsBadge.setVisibility(View.VISIBLE);

                // Mostrar ubicación
                holder.layoutGps.setVisibility(View.VISIBLE);
                holder.tvLocation.setText(String.format("%.6f, %.6f", latLong[0], latLong[1]));

                photo.setLatitude(latLong[0]);
                photo.setLongitude(latLong[1]);

                holder.tvError.setVisibility(View.GONE);
            } else {
                holder.tvGpsBadge.setVisibility(View.GONE);
                holder.layoutGps.setVisibility(View.GONE);
                holder.tvError.setText("⚠ Sin geolocalización");
                holder.tvError.setVisibility(View.VISIBLE);
            }

            if (dateTime != null && !dateTime.isEmpty()) {
                holder.layoutDateTime.setVisibility(View.VISIBLE);
                holder.tvDateTime.setText(dateTime);
                photo.setDateTime(dateTime);
            } else {
                holder.layoutDateTime.setVisibility(View.GONE);
            }

        } catch (IOException e) {
            holder.tvError.setText("⚠ No se pudieron leer metadatos");
            holder.tvError.setVisibility(View.VISIBLE);
            holder.layoutGps.setVisibility(View.GONE);
            holder.layoutDateTime.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return photoList.size();
    }

    public void updatePhotos(List<Photo> newPhotos) {
        this.photoList = newPhotos;
        notifyDataSetChanged();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView tvFileName;
        TextView tvGpsBadge;
        LinearLayout layoutGps;
        TextView tvLocation;
        LinearLayout layoutDateTime;
        TextView tvDateTime;
        TextView tvError;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvGpsBadge = itemView.findViewById(R.id.tvGpsBadge);
            layoutGps = itemView.findViewById(R.id.layoutGps);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            layoutDateTime = itemView.findViewById(R.id.layoutDateTime);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            tvError = itemView.findViewById(R.id.tvError);
        }
    }
}