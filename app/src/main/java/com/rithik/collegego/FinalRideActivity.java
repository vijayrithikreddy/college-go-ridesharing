package com.rithik.collegego;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FinalRideActivity extends AppCompatActivity
        implements OnMapReadyCallback {

    private static final String TAG = "FinalRideActivity";

    GoogleMap mMap;

    TextView tvPersonName, tvPhone;
    Button btnCall, btnFinishRide;

    String role;                 // RIDER / PILLION
    String rideId;
    String requestId;
    ListenerRegistration paymentListener;

    String otherUserId;
    String otherUserName;
    String otherUserPhone;
    String riderEncoded;
    String pillionEncoded;
    // 🔴 LIVE TRACKING
    FusedLocationProviderClient locationClient;
    LocationCallback locationCallback;
    Marker liveRiderMarker;
    ListenerRegistration liveLocationListener;

    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_final_ride);
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        tvPersonName = findViewById(R.id.tvPersonName);
        tvPhone = findViewById(R.id.tvPhone);
        btnCall = findViewById(R.id.btnCall);
        btnFinishRide = findViewById(R.id.btnFinishRide);

        db = FirebaseFirestore.getInstance();

        rideId = getIntent().getStringExtra("rideId");
        requestId = getIntent().getStringExtra("requestId");

        // default UI state
        tvPersonName.setText("Loading...");
        tvPhone.setText("Loading...");
        btnCall.setEnabled(false);
        btnFinishRide.setVisibility(View.GONE);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.mapFinalRide);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        resolveUserRoleAndSetupUI();
        fetchRide();
        fetchRideRequest();
        btnFinishRide.setOnClickListener(v -> finishRide());
    }

    /* ---------------- ROLE (SOURCE OF TRUTH) ---------------- */

    private void resolveUserRoleAndSetupUI() {

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) return;

                    role = doc.getString("role"); // RIDER / PILLION
                    Log.d(TAG, "ROLE = " + role);

                    btnFinishRide.setVisibility(View.VISIBLE);

                    if ("RIDER".equalsIgnoreCase(role)) {
                        // 🚴 Rider
                        btnFinishRide.setEnabled(true);

                        // 🔴 START SENDING LIVE LOCATION
                        startLiveLocationUpdates();

                    } else if ("PILLION".equalsIgnoreCase(role)) {
                        // 🔵 Pillion
                        btnFinishRide.setEnabled(false);

                        // 🔵 LISTEN TO RIDER LIVE LOCATION
                        listenForPaymentStatus();
                        listenToLiveRideLocation();
                    }

                    fetchOtherUserDetails();
                });
    }

    private void listenToLiveRideLocation() {

        liveLocationListener = db.collection("rideLive")
                .document(rideId)
                .addSnapshotListener((doc, e) -> {

                    if (doc == null || !doc.exists() || mMap == null) return;

                    Double lat = doc.getDouble("lat");
                    Double lng = doc.getDouble("lng");
                    Double bearing = doc.getDouble("bearing");

                    if (lat == null || lng == null) return;

                    LatLng pos = new LatLng(lat, lng);

                    if (liveRiderMarker == null) {
                        liveRiderMarker = mMap.addMarker(
                                new MarkerOptions()
                                        .position(pos)
                                        .title("Rider (Live)")
                                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.rider))
                                        .anchor(0.5f, 0.7f)
                        );
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16));
                    } else {
                        liveRiderMarker.setPosition(pos);
                        if (bearing != null) {
                            liveRiderMarker.setRotation(bearing.floatValue());
                        }
                    }
                });
    }
    private void startLiveLocationUpdates() {

        LocationRequest request = LocationRequest.create();
        request.setInterval(3000);        // every 3 seconds
        request.setFastestInterval(2000);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;

                Location loc = result.getLastLocation();
                if (loc == null) return;

                Map<String, Object> live = new HashMap<>();
                live.put("lat", loc.getLatitude());
                live.put("lng", loc.getLongitude());
                live.put("bearing", loc.getBearing());
                live.put("updatedAt", FieldValue.serverTimestamp());

                db.collection("rideLive")
                        .document(rideId)
                        .set(live);
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locationClient.requestLocationUpdates(request,
                locationCallback,
                getMainLooper()
        );
    }
    private void listenForPaymentStatus() {

        if (requestId == null) return;

        paymentListener = db.collection("rideRequests")
                .document(requestId)
                .addSnapshotListener((doc, e) -> {

                    if (doc == null || !doc.exists()) return;

                    String paymentStatus = doc.getString("paymentStatus");

                    // 🔑 ENABLE ONLY WHEN PAYMENT IS PENDING
                    if ("PENDING".equalsIgnoreCase(paymentStatus)) {
                        btnFinishRide.setEnabled(true);
                    } else {
                        btnFinishRide.setEnabled(false);
                    }
                });
    }



    /* ---------------- OTHER USER ---------------- */

    private void fetchOtherUserDetails() {

        if ("RIDER".equalsIgnoreCase(role)) {

            // Rider → get pillion from rideRequests
            if (requestId == null) return;

            db.collection("rideRequests")
                    .document(requestId)
                    .get()
                    .addOnSuccessListener(reqDoc -> {
                        if (!reqDoc.exists()) return;
                        String pillionId = reqDoc.getString("pillionId");
                        loadUserProfile(pillionId);
                    });

        } else {

            // Pillion → get rider from rides
            if (rideId == null) return;

            db.collection("rides")
                    .document(rideId)
                    .get()
                    .addOnSuccessListener(rideDoc -> {
                        if (!rideDoc.exists()) return;
                        String riderId = rideDoc.getString("riderId");
                        loadUserProfile(riderId);
                    });
        }
    }


    private void loadUserProfile(String uid) {

        if (uid == null) return;

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) return;

                    String name = doc.getString("name");
                    String phone = doc.getString("phone");

                    tvPersonName.setText(
                            name != null ? name : "User"
                    );

                    tvPhone.setText(
                            phone != null ? phone : "Not available"
                    );

                    if (phone != null) {
                        btnCall.setEnabled(true);
                        btnCall.setOnClickListener(v -> callPerson(phone));
                    }
                });
    }


    /* ---------------- RIDE DATA ---------------- */

    private void fetchRide() {

        if (rideId == null) return;

        db.collection("rides")
                .document(rideId)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) return;

                    riderEncoded = doc.getString("overview_polyline");

                    if (mMap != null) drawPolylines();
                });
    }

    private void fetchRideRequest() {

        if (requestId == null) return;

        db.collection("rideRequests")
                .document(requestId)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) return;

                    pillionEncoded = doc.getString("pillionPolyline");

                    if (mMap != null) drawPolylines();
                });
    }

    /* ---------------- MAP ---------------- */

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        drawPolylines();
    }

    private void drawPolylines() {

        try {
            if (mMap == null) return;

            mMap.clear();
            LatLngBounds.Builder bounds = new LatLngBounds.Builder();

            List<LatLng> riderPts = null;
            List<LatLng> pillionPts = null;

            // 🟢 RIDER ROUTE + RIDER MARKER
            if (riderEncoded != null) {
                riderPts = PolylineMatchUtils.decodePolyline(riderEncoded);
                if (riderPts != null && !riderPts.isEmpty()) {

                    // Route
                    mMap.addPolyline(new PolylineOptions()
                            .addAll(riderPts)
                            .color(Color.GREEN)
                            .width(10f));

                    // Rider marker (PNG)
                    LatLng riderStart = riderPts.get(0);
                    mMap.addMarker(new MarkerOptions()
                            .position(riderStart)
                            .title("Rider")
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.rider))
                            .anchor(0.5f, 0.7f));

                    for (LatLng p : riderPts) bounds.include(p);
                }
            }

            // 🔵 PILLION ROUTE + PICKUP MARKER
            if (pillionEncoded != null) {
                pillionPts = PolylineMatchUtils.decodePolyline(pillionEncoded);
                if (pillionPts != null && !pillionPts.isEmpty()) {

                    // Route
                    mMap.addPolyline(new PolylineOptions()
                            .addAll(pillionPts)
                            .color(Color.BLUE)
                            .width(10f));

                    // Pillion pickup marker (PNG)
                    LatLng pillionStart = pillionPts.get(0);
                    mMap.addMarker(new MarkerOptions()
                            .position(pillionStart)
                            .title("Pillion Pickup")
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.pillion))
                            .anchor(0.5f, 0.7f));

                    for (LatLng p : pillionPts) bounds.include(p);
                }
            }

            // 🔴 MEETING POINT (DEFAULT MARKER)
            if (riderPts != null && pillionPts != null) {
                LatLng meet = findMeetingPoint(pillionPts, riderPts, 5);
                if (meet != null) {
                    mMap.addMarker(new MarkerOptions()
                            .position(meet)
                            .title("Meeting Point"));
                    bounds.include(meet);
                }
            }

            mMap.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds.build(), 150));

        } catch (Exception e) {
            Log.e(TAG, "Map error", e);
        }
    }


    private LatLng findMeetingPoint(
            List<LatLng> pillion,
            List<LatLng> rider,
            double threshold) {

        for (LatLng p : pillion) {
            for (LatLng r : rider) {
                float[] res = new float[1];
                android.location.Location.distanceBetween(
                        p.latitude, p.longitude,
                        r.latitude, r.longitude,
                        res
                );
                if (res[0] <= threshold) return p;
            }
        }
        return null;
    }

    /* ---------------- FINISH RIDE ---------------- */

    private void finishRide() {

        if (requestId == null || rideId == null) return;

        if ("RIDER".equalsIgnoreCase(role)) {

            // 🔴 RIDER ACTION
            btnFinishRide.setEnabled(false);

            // 1️⃣ Mark ride completed
            db.collection("rides")
                    .document(rideId)
                    .update("status", "COMPLETED")
                    .addOnSuccessListener(v -> {

                        // 2️⃣ Mark payment pending
                        db.collection("rideRequests")
                                .document(requestId)
                                .update("paymentStatus", "PENDING");

                        // 3️⃣ Notify pillion
                        showNotification(
                                "Ride completed",
                                "Collect payment"
                        );



                        paymentListener = db.collection("rideRequests")
                                        .document(requestId)
                                        .addSnapshotListener((doc, e) -> {

                                            if (doc == null || !doc.exists()) return;

                                            String payment = doc.getString("paymentStatus");

                                            if ("PAID".equals(payment)) {
                                                if (paymentListener != null) paymentListener.remove();
                                                stopLiveTracking();
                                                goHome();
                                            }
                                        });



                    });

        } else {

            // 🔵 PILLION ACTION
            btnFinishRide.setEnabled(false);
           // db.collection("rideRequests")
                  //  .document(requestId)
                   // .update("status", "COMPLETED")
                   // .addOnSuccessListener(v -> {

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "COMPLETED");
            updates.put("paymentStatus", "PAID");
            // 1️⃣ Mark payment done
            db.collection("rideRequests")
                    .document(requestId)
                    .update(updates)
                    .addOnSuccessListener(v -> {

                        // 2️⃣ Notify rider
                        showNotification(
                                "Ride completed",
                                "pay the amount."
                        );
                        stopLiveTracking();

                        // 3️⃣ Navigate pillion home
                        goHome();
                    });

        }
    }
    private void stopLiveTracking() {

        // 🔴 Stop rider GPS updates
        if (locationClient != null && locationCallback != null) {
            locationClient.removeLocationUpdates(locationCallback);
        }

        // 🔵 Stop pillion Firestore listener
        if (liveLocationListener != null) {
            liveLocationListener.remove();
            liveLocationListener = null;
        }

        // 🧹 Remove live location document
        if (rideId != null) {
            db.collection("rideLive")
                    .document(rideId)
                    .delete();
        }
    }

    private void goHome() {
        Intent i = new Intent(this, HomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
    private void showNotification(String title, String message) {

        String channelId = "ride_updates";

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(
                            channelId,
                            "Ride Updates",
                            NotificationManager.IMPORTANCE_HIGH
                    );
            manager.createNotificationChannel(channel);
        }

        Notification notification =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setAutoCancel(true)
                        .build();

        manager.notify((int) System.currentTimeMillis(), notification);
    }





    /* ---------------- CALL ---------------- */

    private void callPerson(String phone) {
        Intent i = new Intent(Intent.ACTION_DIAL);
        i.setData(Uri.parse("tel:" + phone));
        startActivity(i);
    }
}
