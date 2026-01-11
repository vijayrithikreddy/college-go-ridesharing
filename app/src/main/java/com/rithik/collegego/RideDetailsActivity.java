package com.rithik.collegego;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;


import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RideDetailsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "RideDetailsActivity";

    GoogleMap mMap;
    SupportMapFragment mapFragment;

    TextView tvRiderName, tvRoute,tvMatchPercent, tvStatus;
    TextView tvTime, tvAmount;

    Button btnRequestSeat;

    String rideId;
    String pillionEncoded; // passed in intent
    double pillionPickupLat, pillionPickupLng, pillionDropLat, pillionDropLng;
    double matchPercent = 0.0;

    FirebaseFirestore db;
    ListenerRegistration requestListener;
    String createdRequestId = null;

    // ride fields (fetched)
    String riderEncoded = null;
    String pickupAddress = "", dropAddress = "", dateTime = "";
    int amount = 0;
    String riderId = "", riderName = "", riderPhone = "";
    long riderTotalMeters = 0;
    long riderTotalSeconds = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ride_details);

        tvRiderName = findViewById(R.id.tvRiderName);
        tvRoute = findViewById(R.id.tvRoute);
        tvTime = findViewById(R.id.tvTime);
        tvAmount = findViewById(R.id.tvAmount);
        tvMatchPercent = findViewById(R.id.tvMatchPercent);
        tvStatus = findViewById(R.id.tvStatus);
        btnRequestSeat = findViewById(R.id.btnRequestSeat);

        db = FirebaseFirestore.getInstance();

        // get intent data
        rideId = getIntent().getStringExtra("rideId");
        pillionEncoded = getIntent().getStringExtra("pillionPolyline");
        pillionPickupLat = getIntent().getDoubleExtra("pillionPickupLat", 0);
        pillionPickupLng = getIntent().getDoubleExtra("pillionPickupLng", 0);
        pillionDropLat = getIntent().getDoubleExtra("pillionDropLat", 0);
        pillionDropLng = getIntent().getDoubleExtra("pillionDropLng", 0);
        matchPercent = getIntent().getDoubleExtra("matchPercent", 0.0);

        if (rideId == null || rideId.isEmpty()) {
            Toast.makeText(this, "Missing ride id", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // map fragment
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapDetails);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // fetch ride document
        fetchRideDoc();
        setupRoleBasedUI();

    }
    private void acceptRide() {

        // 1️⃣ Update ride status
        db.collection("rides").document(rideId)
                .update("status", "ACCEPTED")
                .addOnSuccessListener(aVoid -> {

                    // 2️⃣ Update request status
                    db.collection("rideRequests")
                            .whereEqualTo("rideId", rideId)
                            .whereEqualTo("status", "PENDING")
                            .get()
                            .addOnSuccessListener(snapshot -> {

                                if (snapshot.isEmpty()) {
                                    Toast.makeText(this, "No pending requests", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                DocumentSnapshot requestDoc = snapshot.getDocuments().get(0);
                                String requestId = requestDoc.getId();

                                requestDoc.getReference()
                                        .update("status", "ACCEPTED")
                                        .addOnSuccessListener(v -> {

                                            Toast.makeText(this, "Ride accepted", Toast.LENGTH_SHORT).show();

                                            // ✅ 3️⃣ NAVIGATE TO FINAL SCREEN
                                            Intent intent = new Intent(
                                                    RideDetailsActivity.this,
                                                    FinalRideActivity.class
                                            );

                                            intent.putExtra("rideId", rideId);
                                            intent.putExtra("requestId", requestId);

                                            startActivity(intent);
                                            finish(); // optional but recommended
                                        });
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to accept ride", Toast.LENGTH_SHORT).show()
                );
    }


    private void fetchRideDoc() {
        db.collection("rides").document(rideId)
                .get()
                .addOnSuccessListener(this::onRideDocFetched)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch ride doc: " + e.getMessage());
                    Toast.makeText(this, "Failed to load ride", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void onRideDocFetched(DocumentSnapshot doc) {
        if (!doc.exists()) {
            Toast.makeText(this, "Ride not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        riderId = doc.getString("riderId");
        riderName = doc.contains("riderName") ? doc.getString("riderName") : "Rider";
        riderPhone = doc.contains("riderPhone") ? doc.getString("riderPhone") : "";

        pickupAddress = doc.contains("pickupAddress") ? doc.getString("pickupAddress") : "";
        dropAddress = doc.contains("dropAddress") ? doc.getString("dropAddress") : "";
        dateTime = doc.contains("dateTime") ? doc.getString("dateTime") : "";
        try {
            amount = doc.contains("amount") ? (int) (doc.getLong("amount") == null ? 0L : doc.getLong("amount")) : 0;
        } catch (Exception e) {
            amount = 0;
        }

        riderEncoded = doc.contains("overview_polyline") ? doc.getString("overview_polyline") : null;
        try {
            riderTotalMeters = doc.contains("route_distance_meters")
                    ? doc.getLong("route_distance_meters")
                    : 0;

            riderTotalSeconds = doc.contains("route_duration_seconds")
                    ? doc.getLong("route_duration_seconds")
                    : 0;
        } catch (Exception e) {
            riderTotalMeters = 0;
            riderTotalSeconds = 0;
        }
        Log.d("ETA_DEBUG",
                "meters=" + riderTotalMeters +
                        " seconds=" + riderTotalSeconds);

        // update UI
        tvRiderName.setText(riderName);
        tvRoute.setText(pickupAddress + " → " + dropAddress);
        tvTime.setText(dateTime != null && !dateTime.isEmpty()
                ? dateTime
                : "Time not set");

        tvAmount.setText("₹" + amount);
        tvMatchPercent.setText(String.format("%.0f%%\nMatch", matchPercent));


        // if map is ready we will draw, otherwise onMapReady will draw after map ready
        if (mMap != null) drawPolylines();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        if (riderEncoded != null) {
            drawPolylines();
        }
    }

    private void drawPolylines() {
        try {
            mMap.clear();

            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();

            List<LatLng> riderPts = null;
            List<LatLng> pillionPts = null;

            // draw rider polyline
            if (riderEncoded != null && !riderEncoded.isEmpty()) {
                riderPts = PolylineMatchUtils.decodePolyline(riderEncoded);
                if (riderPts != null && riderPts.size() > 0) {
                    mMap.addPolyline(new PolylineOptions()
                            .addAll(riderPts)
                            .color(Color.GREEN)
                            .width(10f));
                    for (LatLng p : riderPts) boundsBuilder.include(p);
                }
                if (riderPts != null && !riderPts.isEmpty()) {

                    LatLng riderStart = riderPts.get(0);

                    mMap.addMarker(new MarkerOptions()
                            .position(riderStart)
                            .title("Rider")
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.rider))
                            // 🔑 anchor fix
                            .anchor(0.5f, 0.7f)
                    );
                }

            }

            // draw pillion polyline
            if (pillionEncoded != null && !pillionEncoded.isEmpty()) {
                pillionPts = PolylineMatchUtils.decodePolyline(pillionEncoded);
                if (pillionPts != null && pillionPts.size() > 0) {
                    mMap.addPolyline(new PolylineOptions()
                            .addAll(pillionPts)
                            .color(Color.BLUE)
                            .width(10f));
                    for (LatLng p : pillionPts) boundsBuilder.include(p);
                    LatLng p = new LatLng(pillionPickupLat, pillionPickupLng);

                    mMap.addMarker(new MarkerOptions()
                            .position(p)
                            .title("Pillion Pickup")
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.pillion))
                            .anchor(0.5f, 0.7f)
                    );
                }
            } else {
                // fallback markers
                if (pillionPickupLat != 0 && pillionPickupLng != 0) {
                    LatLng p = new LatLng(pillionPickupLat, pillionPickupLng);
                    mMap.addMarker(new MarkerOptions().position(p).title("Your pickup"));
                    boundsBuilder.include(p);
                }
                if (pillionDropLat != 0 && pillionDropLng != 0) {
                    LatLng d = new LatLng(pillionDropLat, pillionDropLng);
                    mMap.addMarker(new MarkerOptions().position(d).title("Your drop"));
                    boundsBuilder.include(d);
                }
            }

            // 🔴 ADD MEETING POINT MARKER
            if (riderPts != null && pillionPts != null) {
                LatLng meetingPoint = findMeetingPoint(
                        pillionPts,
                        riderPts,
                        5.0   // SAME threshold as matching
                );

                if (meetingPoint != null) {

                    String snippet = "Pillion joins rider here";

                    if (riderTotalMeters > 0 && riderTotalSeconds > 0) {

                        double metersToMeeting =
                                distanceAlongRouteUntilPoint(riderPts, meetingPoint);

                        double ratio = metersToMeeting / riderTotalMeters;
                        long etaSeconds = (long) (ratio * riderTotalSeconds);
                        long etaMinutes = Math.max(1, etaSeconds / 60);

                        // 🔴 ADD THIS
                        String meetingTime = computeMeetingTime(dateTime, etaMinutes);

                        snippet = "Meet at: " + meetingTime;
                    }

                    mMap.addMarker(new MarkerOptions()
                            .position(meetingPoint)
                            .title("Meeting Point")
                            .anchor(0.5f, 0.7f)
                            .snippet(snippet));

                    boundsBuilder.include(meetingPoint);
                }


            }

            // move camera
            try {
                LatLngBounds bounds = boundsBuilder.build();
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150));
            } catch (IllegalStateException ignored) {}


        } catch (Exception e) {
            Log.e(TAG, "drawPolylines error: " + e.getMessage(), e);
        }
    }
    private String computeMeetingTime(String scheduledTime, long etaMinutes) {
        try {
            // Example input: "09:30 AM"
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());

            java.util.Date date = sdf.parse(scheduledTime);
            if (date == null) return scheduledTime;

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(date);
            cal.add(java.util.Calendar.MINUTE, (int) etaMinutes);

            return sdf.format(cal.getTime());

        } catch (Exception e) {
            Log.e("TIME_CALC", "Time parse error: " + e.getMessage());
            return scheduledTime; // fallback
        }
    }

    private double distanceAlongRouteUntilPoint(
            List<LatLng> route,
            LatLng targetPoint) {

        double distance = 0.0;

        for (int i = 0; i < route.size() - 1; i++) {
            LatLng a = route.get(i);
            LatLng b = route.get(i + 1);

            // check if target is close to segment start
            float[] res = new float[1];
            android.location.Location.distanceBetween(
                    a.latitude, a.longitude,
                    targetPoint.latitude, targetPoint.longitude,
                    res
            );

            if (res[0] <= 5.0) { // meeting point reached
                break;
            }

            // add segment distance
            float[] seg = new float[1];
            android.location.Location.distanceBetween(
                    a.latitude, a.longitude,
                    b.latitude, b.longitude,
                    seg
            );
            distance += seg[0];
        }

        return distance;
    }


    private void createRideRequest() {
        String currentUser = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUser == null) {
            Toast.makeText(this, "Login required", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button to prevent duplicate requests
        btnRequestSeat.setEnabled(false);
        tvStatus.setText("Requesting...");

        Map<String, Object> req = new HashMap<>();
        req.put("rideId", rideId);
        req.put("pillionId", currentUser);
        req.put("status", "PENDING");
        req.put("requestedPickupLat", pillionPickupLat);
        req.put("requestedPickupLng", pillionPickupLng);
        req.put("requestedDropLat", pillionDropLat);
        req.put("requestedDropLng", pillionDropLng);
        req.put("time", dateTime);
        req.put("amount", amount);
        req.put("timestamp", FieldValue.serverTimestamp());
        req.put("matchPercent", matchPercent);      // ✅ STORE MATCH %
        req.put("pillionPolyline", pillionEncoded); // ✅ STORE PILLION ROUTE


        db.collection("rideRequests")
                .add(req)
                .addOnSuccessListener(documentReference -> {
                    createdRequestId = documentReference.getId();
                    tvStatus.setVisibility(View.VISIBLE);
                    tvStatus.setText("Request sent (PENDING)");
                    // Listen for updates on this request
                    listenToRequestUpdates(createdRequestId);
                })
                .addOnFailureListener(e -> {
                    btnRequestSeat.setEnabled(true);
                    tvStatus.setText("");
                    Toast.makeText(RideDetailsActivity.this, "Request failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void listenToRequestUpdates(String requestId) {
        if (requestId == null) return;

        DocumentReference ref = db.collection("rideRequests").document(requestId);
        requestListener = ref.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "request listener error: " + error.getMessage());
                return;
            }
            if (snapshot == null || !snapshot.exists()) return;

            String status = snapshot.getString("status");
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText("Request status: " + status);

            if ("ACCEPTED".equalsIgnoreCase(status)){

                btnRequestSeat.setEnabled(false);
                tvStatus.setText("Ride accepted");

                // 🔴 NAVIGATE PILLION TO FINAL SCREEN
                Intent intent = new Intent(
                        RideDetailsActivity.this,
                        FinalRideActivity.class
                );

                intent.putExtra("rideId", rideId);
                intent.putExtra("requestId", requestId);

                startActivity(intent);
                finish();
            }else if ("REJECTED".equalsIgnoreCase(status)) {
                btnRequestSeat.setEnabled(true);
                Toast.makeText(this, "Request rejected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void revealRiderContact() {
        // If phone exists in ride doc (riderPhone) use it, otherwise fetch from users collection
        if (riderPhone != null && !riderPhone.isEmpty()) {
            tvStatus.setText(tvStatus.getText() + "\nRider phone: " + riderPhone);
            return;
        }
        if (riderId == null || riderId.isEmpty()) return;

        db.collection("users").document(riderId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.contains("phone")) {
                        String phone = doc.getString("phone");
                        tvStatus.setText(tvStatus.getText() + "\nRider phone: " + phone);
                    } else {
                        tvStatus.setText(tvStatus.getText() + "\nRider contact not available");
                    }
                })
                .addOnFailureListener(e -> {
                    tvStatus.setText(tvStatus.getText() + "\nFailed to fetch contact");
                });
    }
    private LatLng findMeetingPoint(List<LatLng> pillionPts,
                                    List<LatLng> riderPts,
                                    double thresholdMeters) {

        for (LatLng p : pillionPts) {
            for (LatLng r : riderPts) {
                float[] result = new float[1];
                android.location.Location.distanceBetween(
                        p.latitude, p.longitude,
                        r.latitude, r.longitude,
                        result
                );
                if (result[0] <= thresholdMeters) {
                    return p; // first overlap point
                }
            }
        }
        return null;
    }
    private void setupRoleBasedUI() {

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) return;

                    // ✅ READ CORRECT FIELD (MATCHES FIRESTORE)
                    String role = doc.getString("role");
                    if (role == null) role = "PILLION";

                    if ("PILLION".equalsIgnoreCase(role)) {

                        btnRequestSeat.setText("Request Seat");
                        btnRequestSeat.setEnabled(true);
                        btnRequestSeat.setOnClickListener(v -> createRideRequest());

                    } else if ("RIDER".equalsIgnoreCase(role)) {

                        btnRequestSeat.setText("Accept Ride");
                        btnRequestSeat.setEnabled(true);
                        btnRequestSeat.setOnClickListener(v -> acceptRide());
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load user role", Toast.LENGTH_SHORT).show()
                );
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (requestListener != null) requestListener.remove();
    }
}
