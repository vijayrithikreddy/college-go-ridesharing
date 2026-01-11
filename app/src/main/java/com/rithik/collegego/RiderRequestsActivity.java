package com.rithik.collegego;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class RiderRequestsActivity extends AppCompatActivity {

    RecyclerView rvRequests;
    TextView tvEmpty;
    FirebaseFirestore db;
    ListenerRegistration requestListener;

    String rideId;
    String currentRiderId;


    RiderRequestsAdapter adapter;
    List<RideRequestModel> requestList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rider_requests);
        requestNotificationPermissionIfNeeded();

        rvRequests = findViewById(R.id.rvRequests);
        tvEmpty = findViewById(R.id.tvEmpty);

        rvRequests.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RiderRequestsAdapter(requestList);
        rvRequests.setAdapter(adapter);


        showEmptyState();

        db = FirebaseFirestore.getInstance();
        currentRiderId = FirebaseAuth.getInstance().getUid();

        // get rideId
        rideId = getIntent().getStringExtra("rideId");
        if (rideId == null) {
            finish();
            return;
        }

        // 🔥 START LISTENING
        startListeningForRequests();
    }
    private void fetchPillionDetails(RideRequestModel model) {

        if (model.pillionId == null) return;

        // 1️⃣ Fetch pillion name
        db.collection("users").document(model.pillionId)
                .get()
                .addOnSuccessListener(userDoc -> {

                    model.pillionName = userDoc.exists()
                            ? userDoc.getString("name")
                            : "Pillion";

                    // 2️⃣ Fetch ride pickup & drop
                    db.collection("rides").document(model.rideId)
                            .get()
                            .addOnSuccessListener(rideDoc -> {

                                if (rideDoc.exists()) {
                                    model.pickupAddress = rideDoc.getString("pickupAddress");
                                    model.dropAddress = rideDoc.getString("dropAddress");
                                }

                                requestList.add(model);
                                adapter.notifyDataSetChanged();
                            });
                });
    }


    private void startListeningForRequests() {

        requestListener = db.collection("rideRequests")
                .whereEqualTo("rideId", rideId)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener((snapshots, error) -> {

                    if (error != null) {
                        Log.e("RiderRequests", "Listener error: " + error.getMessage());
                        return;
                    }

                    if (snapshots == null) return;

                    // 🔴 Track previous state to trigger notification once
                    boolean wasEmpty = requestList.isEmpty();

                    requestList.clear();

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {

                        RideRequestModel model = new RideRequestModel();

                        model.requestId = doc.getId();
                        model.rideId = doc.getString("rideId");
                        model.pillionId = doc.getString("pillionId");

                        model.pillionPolyline = doc.getString("pillionPolyline");
                        model.matchPercent = doc.getDouble("matchPercent") != null
                                ? doc.getDouble("matchPercent") : 0.0;
                        model.time = doc.getString("time");   // 🔴 ADD
                        model.amount = doc.getLong("amount") != null
                                ? doc.getLong("amount").intValue()
                                : 0;                           // 🔴 ADD

                        model.pickupLat = doc.getDouble("requestedPickupLat");
                        model.pickupLng = doc.getDouble("requestedPickupLng");
                        model.dropLat = doc.getDouble("requestedDropLat");
                        model.dropLng = doc.getDouble("requestedDropLng");
                        fetchPillionDetails(model);
                    }

                    // 🔔 Show notification ONLY when first request arrives
                    if (wasEmpty && !snapshots.isEmpty()) {
                        showRideRequestNotification();
                    }

                    // update UI state
                    if (snapshots.isEmpty()) {
                        showEmptyState();
                    } else {
                        showRequestList();
                    }
                });
    }
    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        101
                );
            }
        }
    }

    private void showRideRequestNotification() {

        String channelId = "ride_requests";

        android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // create channel (Android O+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel =
                    new android.app.NotificationChannel(
                            channelId,
                            "Ride Requests",
                            android.app.NotificationManager.IMPORTANCE_HIGH
                    );
            manager.createNotificationChannel(channel);
        }

        android.app.Notification notification =
                new androidx.core.app.NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("College Go")
                        .setContentText("Got a ride request")
                        .setAutoCancel(true)
                        .build();

        manager.notify(1001, notification);
    }



    /**
     * Show empty state when there are no pillion requests
     */
    private void showEmptyState() {
        tvEmpty.setVisibility(View.VISIBLE);
        rvRequests.setVisibility(View.GONE);
    }

    /**
     * Show list when requests are available
     */
    private void showRequestList() {
        tvEmpty.setVisibility(View.GONE);
        rvRequests.setVisibility(View.VISIBLE);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (requestListener != null) {
            requestListener.remove();
        }
    }

}
