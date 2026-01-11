package com.rithik.collegego; // 🔁 replace with your actual package name

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class OfferRideActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    TextView tvFromAddress, tvToAddress, tvTime, tvAmount;
    Button btnOfferRide;

    double pickupLat, pickupLng, dropLat, dropLng;
    String pickupAddress, dropAddress, time;
    int amount;

    FirebaseAuth mAuth;
    FirebaseFirestore db;

    private static final String DIRECTIONS_API_KEY = "AIzaSyDYd2Y1n86BgTeSoYl58NYsyJT3NfQP0Zs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offer_ride);

        tvFromAddress = findViewById(R.id.tvFromAddress);
        tvToAddress = findViewById(R.id.tvToAddress);
        tvTime = findViewById(R.id.tvTime);
        tvAmount = findViewById(R.id.tvAmount);
        btnOfferRide = findViewById(R.id.btnOfferRide);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Get data from Intent
        pickupLat = getIntent().getDoubleExtra("pickupLat", 0.0);
        pickupLng = getIntent().getDoubleExtra("pickupLng", 0.0);
        pickupAddress = getIntent().getStringExtra("pickupAddress");

        dropLat = getIntent().getDoubleExtra("dropLat", 0.0);
        dropLng = getIntent().getDoubleExtra("dropLng", 0.0);
        dropAddress = getIntent().getStringExtra("dropAddress");

        time = getIntent().getStringExtra("timeText");
        amount = getIntent().getIntExtra("amount", 0);

        // Set info at bottom
        tvFromAddress.setText(pickupAddress);
        tvToAddress.setText(dropAddress);
        tvTime.setText("Time: " + time);
        tvAmount.setText("Amount: ₹" + amount);

        // Setup map
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        btnOfferRide.setOnClickListener(v -> offerRide());
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        if (pickupLat == 0.0 && pickupLng == 0.0) {
            Toast.makeText(this, "Pickup coordinates missing", Toast.LENGTH_SHORT).show();
            return;
        }

        LatLng pickup = new LatLng(pickupLat, pickupLng);
        addPngMarker(pickup, "Pickup", R.drawable.rider);


        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(pickup);

        LatLng drop = null;
        if (dropLat != 0.0 || dropLng != 0.0) {
            drop = new LatLng(dropLat, dropLng);
            mMap.addMarker(new MarkerOptions().position(drop).title("Drop"));
            builder.include(drop);
        }

        // Move camera to show pickup & drop
        LatLngBounds bounds = builder.build();
        int padding = 100;
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));

        // Draw route only if we have drop coordinates
        if (drop != null) {
            drawRoute(pickup, drop);
        }
    }
    private void addPngMarker(LatLng latLng, String title, int drawableRes) {
        mMap.addMarker(
                new MarkerOptions()
                        .position(latLng)
                        .title(title)
                        .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory
                                .fromResource(drawableRes))
                        .anchor(0.5f, 0.7f)

        );
    }


    private void offerRide() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String riderId = mAuth.getCurrentUser().getUid();

        Map<String, Object> ride = new HashMap<>();
        ride.put("riderId", riderId);
        ride.put("pickupAddress", pickupAddress);
        ride.put("pickupLat", pickupLat);
        ride.put("pickupLng", pickupLng);
        ride.put("dropAddress", dropAddress);
        ride.put("dropLat", dropLat);
        ride.put("dropLng", dropLng);
        ride.put("dateTime", time);
        ride.put("amount", amount);
        ride.put("status", "CREATED");

        // Request route polyline before saving
        LatLng pickup = new LatLng(pickupLat, pickupLng);
        LatLng drop = new LatLng(dropLat, dropLng);

        fetchRouteAndSaveRide(ride, pickup, drop);
    }
    private void fetchRouteAndSaveRide(Map<String, Object> rideMap, LatLng pickup, LatLng drop) {

        String urlString = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + pickup.latitude + "," + pickup.longitude +
                "&destination=" + drop.latitude + "," + drop.longitude +
                "&mode=driving" +
                "&key=" + DIRECTIONS_API_KEY;

        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                InputStream inputStream = conn.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream));

                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }

                JSONObject json = new JSONObject(result.toString());
                JSONArray routes = json.getJSONArray("routes");

                if (routes.length() > 0) {
                    JSONObject route = routes.getJSONObject(0);

                    // Polyline
                    JSONObject polyObj = route.getJSONObject("overview_polyline");
                    String overviewPolyline = polyObj.getString("points");
                    rideMap.put("overview_polyline", overviewPolyline);

                    // Distance + Duration
                    JSONArray legs = route.getJSONArray("legs");
                    JSONObject leg = legs.getJSONObject(0);

                    long distance = leg.getJSONObject("distance").getLong("value");
                    long duration = leg.getJSONObject("duration").getLong("value");

                    rideMap.put("route_distance_meters", distance);
                    rideMap.put("route_duration_seconds", duration);
                }

                runOnUiThread(() -> saveRideToFirestore(rideMap));

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> saveRideToFirestore(rideMap));
            } finally {
                if (conn != null) conn.disconnect();
                try { if (reader != null) reader.close(); } catch (Exception ignored) {}
            }
        }).start();
    }
    private void saveRideToFirestore(Map<String, Object> rideMap) {
        db.collection("rides")
                .add(rideMap)
                .addOnSuccessListener(docRef -> {

                    Toast.makeText(
                            OfferRideActivity.this,
                            "Ride offered successfully!",
                            Toast.LENGTH_SHORT
                    ).show();

                    // 🔴 Navigate to RiderRequestsActivity
                    Intent intent = new Intent(
                            OfferRideActivity.this,
                            RiderRequestsActivity.class
                    );
                    intent.putExtra("rideId", docRef.getId());
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(
                            OfferRideActivity.this,
                            "Failed to offer ride: " + e.getMessage(),
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }



    // 🔹 Fetch route from Google Directions API and draw it as a polyline
    private void drawRoute(LatLng pickup, LatLng drop) {
        if (DIRECTIONS_API_KEY.equals("YOUR_API_KEY_HERE")) {
            // Prevent silent failure if key not set
            Toast.makeText(this, "Set DIRECTIONS_API_KEY in OfferRideActivity", Toast.LENGTH_SHORT).show();
            return;
        }

        String urlString = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + pickup.latitude + "," + pickup.longitude +
                "&destination=" + drop.latitude + "," + drop.longitude +
                "&key=" + DIRECTIONS_API_KEY;

        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                InputStream inputStream = conn.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream));

                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }

                JSONObject json = new JSONObject(result.toString());
                JSONArray routes = json.getJSONArray("routes");
                if (routes.length() == 0) {
                    return;
                }

                JSONObject route = routes.getJSONObject(0);
                JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                String points = overviewPolyline.getString("points");

                List<LatLng> path = decodePolyline(points);

                runOnUiThread(() -> {
                    if (mMap != null && path.size() > 0) {
                        mMap.addPolyline(new PolylineOptions()
                                .addAll(path)
                                .width(10f)
                                .color(Color.BLUE)
                                .geodesic(true));
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (conn != null) conn.disconnect();
                if (reader != null) {
                    try { reader.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    // 🔹 Decode encoded polyline string into a list of LatLng points
    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lng += dlng;

            LatLng p = new LatLng(lat / 1E5, lng / 1E5);
            poly.add(p);
        }

        return poly;
    }
}
