package com.rithik.collegego;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

public class SplashActivity extends AppCompatActivity {

    FirebaseAuth mAuth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        VideoView videoView;

        videoView = findViewById(R.id.videoView);

        Uri videoUri = Uri.parse(
                "android.resource://" + getPackageName() + "/" + R.raw.splash_video
        );

        videoView.setVideoURI(videoUri);
        videoView.start();

        // ⏳ After video delay → existing logic
        new Handler().postDelayed(this::decideNextScreen, 1200);
    }


    private void decideNextScreen() {

        // 🔴 NOT LOGGED IN
        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        String uid = mAuth.getCurrentUser().getUid();

        // 🔵 FETCH USER ROLE
        db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {

                    if (!userDoc.exists()) {
                        goHome();
                        return;
                    }

                    String role = userDoc.getString("role");
                    if (role == null) role = "PILLION";

                    if ("RIDER".equalsIgnoreCase(role)) {
                        handleRiderFlow(uid);
                    } else {
                        handlePillionFlow(uid);
                    }
                })
                .addOnFailureListener(e -> goHome());
    }

    /* ================= RIDER FLOW ================= */

    private void handleRiderFlow(String riderId) {

        // 1️⃣ If ride is ACCEPTED → FinalRideActivity
        db.collection("rides")
                .whereEqualTo("riderId", riderId)
                .whereEqualTo("status", "ACCEPTED")
                .limit(1)
                .get()
                .addOnSuccessListener(rideSnap -> {

                    if (!rideSnap.isEmpty()) {

                        String rideId =
                                rideSnap.getDocuments().get(0).getId();

                        // fetch accepted request
                        db.collection("rideRequests")
                                .whereEqualTo("rideId", rideId)
                                .whereEqualTo("status", "ACCEPTED")
                                .limit(1)
                                .get()
                                .addOnSuccessListener(reqSnap -> {

                                    if (!reqSnap.isEmpty()) {

                                        String requestId =
                                                reqSnap.getDocuments().get(0).getId();

                                        Intent i =
                                                new Intent(this, FinalRideActivity.class);
                                        i.putExtra("rideId", rideId);
                                        i.putExtra("requestId", requestId);
                                        startActivity(i);
                                        finish();
                                    } else {
                                        goHome();
                                    }
                                });

                    } else {
                        // no accepted ride → check pending requests
                        checkPendingRideRequests(riderId);
                    }
                })
                .addOnFailureListener(e -> goHome());
    }


    private void checkPendingRideRequests(String riderId) {

        db.collection("rides")
                .whereEqualTo("riderId", riderId)
                .whereEqualTo("status", "CREATED")
                .get()
                .addOnSuccessListener(ridesSnap -> {

                    if (ridesSnap.isEmpty()) {
                        goHome();
                        return;
                    }

                    String rideId = ridesSnap.getDocuments().get(0).getId();

                    db.collection("rideRequests")
                            .whereEqualTo("rideId", rideId)
                            .whereEqualTo("status", "PENDING")
                            .get()
                            .addOnSuccessListener(reqSnap -> {

                                if (!reqSnap.isEmpty()) {
                                    Intent i = new Intent(this, RiderRequestsActivity.class);
                                    i.putExtra("rideId", rideId);
                                    startActivity(i);
                                    finish();
                                } else {
                                    goHome();
                                }
                            });
                })
                .addOnFailureListener(e -> goHome());
    }

    /* ================= PILLION FLOW ================= */

    private void handlePillionFlow(String pillionId) {

        db.collection("rideRequests")
                .whereEqualTo("pillionId", pillionId)
                .whereIn("status", java.util.List.of("PENDING", "ACCEPTED"))
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (snapshot.isEmpty()) {
                        goHome();
                        return;
                    }

                    DocumentSnapshot req = snapshot.getDocuments().get(0);
                    String rideId = req.getString("rideId");
                    String requestId = req.getId();
                    String status = req.getString("status");

                    Intent i;
                    if ("ACCEPTED".equals(status)) {
                        i = new Intent(this, FinalRideActivity.class);
                    } else {
                        i = new Intent(this, RideDetailsActivity.class);
                    }

                    i.putExtra("rideId", rideId);
                    i.putExtra("requestId", requestId);
                    startActivity(i);
                    finish();
                })
                .addOnFailureListener(e -> goHome());
    }


    /* ================= COMMON ================= */

    private void openFinalRide(String rideId) {
        Intent i = new Intent(this, FinalRideActivity.class);
        i.putExtra("rideId", rideId);
        startActivity(i);
        finish();
    }

    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }
}
