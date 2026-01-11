package com.rithik.collegego;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.libraries.places.api.Places;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class HomeActivity extends AppCompatActivity {
    FirebaseAuth mAuth;
    FirebaseFirestore db;
    TextView tvGreeting;
    View cardRider, cardPillion;
    private void saveFcmToken() {

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {

                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(uid)
                            .update("fcmToken", token)
                            .addOnSuccessListener(v ->
                                    Log.d("FCM", "Token saved successfully"))
                            .addOnFailureListener(e ->
                                    Log.e("FCM", "Token save failed", e));
                });
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        saveFcmToken();
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), "AIzaSyDYd2Y1n86BgTeSoYl58NYsyJT3NfQP0Zs");
        }
        tvGreeting = findViewById(R.id.tvGreeting);
        cardRider = findViewById(R.id.cardRider);
        cardPillion = findViewById(R.id.cardPillion);
        // Rider card views
        View cardRider = findViewById(R.id.cardRider);
        TextView riderTitle = cardRider.findViewById(R.id.tvTitle);
        TextView riderSub = cardRider.findViewById(R.id.tvSubTitle);
        ImageView riderImg = cardRider.findViewById(R.id.imgMode);
        loadUserName();

// Set Rider content
        riderTitle.setText("Continue as Rider");
        riderSub.setText("Ride giver");
        riderImg.setImageResource(R.drawable.riderim);


// Pillion card views
        View cardPillion = findViewById(R.id.cardPillion);
        TextView pillionTitle = cardPillion.findViewById(R.id.tvTitle);
        TextView pillionSub = cardPillion.findViewById(R.id.tvSubTitle);
        ImageView pillionImg = cardPillion.findViewById(R.id.imgMode);

// Set Pillion content
        pillionTitle.setText("Continue as Pillion");
        pillionSub.setText("Ride taker");
        pillionImg.setImageResource(R.drawable.pillionim);




        cardRider.setOnClickListener(v -> {

            String uid = mAuth.getCurrentUser().getUid();

            db.collection("users")
                    .document(uid)
                    .update("role", "RIDER")
                    .addOnSuccessListener(aVoid -> {
                        startActivity(
                                new Intent(HomeActivity.this, PickupActivity.class)
                        );
                    });
        });


        cardPillion.setOnClickListener(v -> {

            String uid = mAuth.getCurrentUser().getUid();

            db.collection("users")
                    .document(uid)
                    .update("role", "PILLION")
                    .addOnSuccessListener(aVoid -> {
                        startActivity(
                                new Intent(HomeActivity.this, PillionEntryActivity.class)
                        );
                    });
        });


    }
    private void loadUserName() {

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {

                        String name = doc.getString("name");
                        if (name == null || name.trim().isEmpty()) return;

                        // Extract first name
                        String firstName = name.trim().split("\\s+")[0];

                        // Capitalize first letter
                        firstName = firstName.substring(0, 1).toUpperCase()
                                + firstName.substring(1).toLowerCase();

                        tvGreeting.setText("Hello, " + firstName);
                    }
                });
    }


}
