package com.rithik.collegego;   // <-- replace with your package

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import android.location.Location;
import android.view.MotionEvent;
import android.view.View;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

public class PillionEntryActivity extends AppCompatActivity implements OnMapReadyCallback {

    FusedLocationProviderClient locationClient;

    EditText etPickup, etDrop;
    View tvPickupMap, tvDropMap;

    Button btnSearchRides;
    GoogleMap mMap;

    LatLng pickupLatLng = null;
    LatLng dropLatLng = null;

    Polyline previewPolyline;
    String previewEncodedPolyline = "";

    private static final int MAP_PICKER_REQUEST = 100;
    private static final String API_KEY = "google_maps_api"; // Replace this

    private final ActivityResultLauncher<Intent> mapPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {

                if (result.getResultCode() == RESULT_OK && result.getData() != null) {

                    Intent data = result.getData();

                    double lat = data.getDoubleExtra("lat", 0);
                    double lng = data.getDoubleExtra("lng", 0);
                    int type = data.getIntExtra("type", 0);

                    LatLng selected = new LatLng(lat, lng);

                    // Reverse geocode
                    String address = getAddressFromLatLng(selected);

                    if (type == 1) {
                        pickupLatLng = selected;
                        etPickup.setText(address);
                    } else {
                        dropLatLng = selected;
                        etDrop.setText(address);
                    }

                    updateMapPreview();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pillion_entry);
        locationClient = LocationServices.getFusedLocationProviderClient(this);


        etPickup = findViewById(R.id.etPickup);
        etDrop = findViewById(R.id.etDrop);
        tvPickupMap = findViewById(R.id.tvPickupMap);
        tvDropMap = findViewById(R.id.tvDropMap);
        btnSearchRides = findViewById(R.id.btnSearchRides);

        // Map initialization
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapPillion);
        mapFragment.getMapAsync(this);
        etPickup.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {

                if (etPickup.getCompoundDrawables()[2] != null) {

                    int drawableWidth =
                            etPickup.getCompoundDrawables()[2].getBounds().width();

                    int[] location = new int[2];
                    etPickup.getLocationOnScreen(location);
                    int rightEdge = location[0] + etPickup.getWidth();

                    if (event.getRawX()
                            >= rightEdge - etPickup.getPaddingEnd() - drawableWidth) {

                        getCurrentLocation(true);
                        return true;
                    }
                }
            }
            return false;
        });


        etDrop.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {

                if (etDrop.getCompoundDrawables()[2] != null) {

                    int drawableWidth =
                            etDrop.getCompoundDrawables()[2].getBounds().width();

                    int[] location = new int[2];
                    etDrop.getLocationOnScreen(location);
                    int rightEdge = location[0] + etDrop.getWidth();

                    if (event.getRawX()
                            >= rightEdge - etDrop.getPaddingEnd() - drawableWidth) {

                        getCurrentLocation(false);
                        return true;
                    }
                }
            }
            return false;
        });



        // Manual typing listeners
        etPickup.addTextChangedListener(locationWatcher);
        etDrop.addTextChangedListener(locationWatcher);

        // Map picker buttons
        tvPickupMap.setOnClickListener(v -> openMapPicker(1));
        tvDropMap.setOnClickListener(v -> openMapPicker(2));


        // Search rides button
        btnSearchRides.setOnClickListener(v -> {
            if (pickupLatLng == null || dropLatLng == null) {
                Toast.makeText(this, "Enter Pickup & Drop", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(PillionEntryActivity.this, MatchedRidesActivity.class);
            i.putExtra("pickupLat", pickupLatLng.latitude);
            i.putExtra("pickupLng", pickupLatLng.longitude);
            i.putExtra("dropLat", dropLatLng.latitude);
            i.putExtra("dropLng", dropLatLng.longitude);
            i.putExtra("pillionPolyline", previewEncodedPolyline);
            startActivity(i);
        });
    }

    // When map is ready
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
    }

    // Text watcher for manual typing
    private final TextWatcher locationWatcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override
        public void afterTextChanged(Editable s) {
            handleLocationEntered();
        }
    };

    private void handleLocationEntered() {
        String pick = etPickup.getText().toString().trim();
        String drop = etDrop.getText().toString().trim();

        if (pick.isEmpty() || drop.isEmpty()) return;

        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());

            List<Address> pickList = geocoder.getFromLocationName(pick, 1);
            List<Address> dropList = geocoder.getFromLocationName(drop, 1);

            if (pickList == null || pickList.isEmpty()) return;
            if (dropList == null || dropList.isEmpty()) return;

            Address p = pickList.get(0);
            Address d = dropList.get(0);

            pickupLatLng = new LatLng(p.getLatitude(), p.getLongitude());
            dropLatLng = new LatLng(d.getLatitude(), d.getLongitude());

            updateMapPreview();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void getCurrentLocation(boolean isPickup) {

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    101
            );
            return;
        }

        locationClient.getLastLocation()
                .addOnSuccessListener(location -> {

                    if (location == null) {
                        Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    String address = getAddressFromLatLng(latLng);

                    if (isPickup) {
                        pickupLatLng = latLng;
                        etPickup.setText(address);
                    } else {
                        dropLatLng = latLng;
                        etDrop.setText(address);
                    }

                    updateMapPreview();
                    updateSearchButtonState();
                });
    }
    private void updateSearchButtonState() {
        boolean enabled = pickupLatLng != null && dropLatLng != null;

        btnSearchRides.setEnabled(enabled);
        btnSearchRides.setAlpha(enabled ? 1f : 0.5f);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 101) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void updateMapPreview() {
        if (mMap == null || pickupLatLng == null || dropLatLng == null) return;

        mMap.clear();

        // Add markers
        mMap.addMarker(new MarkerOptions().position(pickupLatLng).title("Pickup"));
        mMap.addMarker(new MarkerOptions().position(dropLatLng).title("Drop"));

        // Adjust camera
        LatLngBounds bounds = new LatLngBounds.Builder()
                .include(pickupLatLng)
                .include(dropLatLng)
                .build();

        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));

        // Draw route preview
        drawPreviewRoute();
    }

    private void drawPreviewRoute() {

        String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + pickupLatLng.latitude + "," + pickupLatLng.longitude +
                "&destination=" + dropLatLng.latitude + "," + dropLatLng.longitude +
                "&key=" + API_KEY;

        new Thread(() -> {
            try {
                URL urlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
                conn.connect();

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }

                JSONObject json = new JSONObject(result.toString());
                JSONArray routes = json.getJSONArray("routes");

                if (routes.length() == 0) return;

                JSONObject route = routes.getJSONObject(0);
                JSONObject polyObj = route.getJSONObject("overview_polyline");
                previewEncodedPolyline = polyObj.getString("points");

                List<LatLng> path = PolylineMatchUtils.decodePolyline(previewEncodedPolyline);

                runOnUiThread(() -> {
                    if (previewPolyline != null) previewPolyline.remove();

                    previewPolyline = mMap.addPolyline(
                            new PolylineOptions()
                                    .addAll(path)
                                    .width(12)
                                    .color(Color.BLUE)
                                    .geodesic(true)
                    );
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // MapPicker result
    private void openMapPicker(int type) {
        Intent i = new Intent(this, MapPickerActivity.class);
        i.putExtra("type", type);
        mapPickerLauncher.launch(i);
    }



    private String getAddressFromLatLng(LatLng latLng) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> list = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (list != null && !list.isEmpty()) {
                return list.get(0).getAddressLine(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return latLng.latitude + ", " + latLng.longitude; // fallback
    }

}
