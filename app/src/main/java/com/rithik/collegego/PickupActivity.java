package com.rithik.collegego;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class PickupActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PICKUP_MAP = 2001;

    EditText etPickup;
    TextView tvPickupInfo;
    Button btnConfirmPickup;
    ImageView ivBack;
    LinearLayout layoutSelectOnMap;
    Button btnRegularBike, btnScooter;
    Button btnHelmetYes, btnHelmetNo;

    String selectedBikeType = "";
    Boolean helmetProvided = null;


    double pickupLat = 0.0;
    double pickupLng = 0.0;
    String pickupAddress = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pickup);

        etPickup = findViewById(R.id.etPickup);
        tvPickupInfo = findViewById(R.id.tvPickupInfo);
        btnConfirmPickup = findViewById(R.id.btnConfirmPickup);
        ivBack = findViewById(R.id.ivBack);
        layoutSelectOnMap = findViewById(R.id.layoutSelectOnMap);
        btnRegularBike = findViewById(R.id.btnRegularBike);
        btnScooter = findViewById(R.id.btnScooter);

        btnHelmetYes = findViewById(R.id.btnHelmetYes);
        btnHelmetNo = findViewById(R.id.btnHelmetNo);


        ivBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // 🔴 IMPORTANT: If user types manually, invalidate old coordinates
        etPickup.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                pickupLat = 0.0;
                pickupLng = 0.0;
            }
        });

        // 🔵 Open map picker only when user clicks
        layoutSelectOnMap.setOnClickListener(v -> {
            Intent intent = new Intent(PickupActivity.this, MapPickerActivity.class);

            if (pickupLat != 0.0 || pickupLng != 0.0) {
                intent.putExtra("initialLat", pickupLat);
                intent.putExtra("initialLng", pickupLng);
            }

            startActivityForResult(intent, REQUEST_CODE_PICKUP_MAP);
        });
        btnRegularBike.setOnClickListener(v -> {
            selectedBikeType = "REGULAR";

            btnRegularBike.setBackgroundResource(R.drawable.btn_filled);
            btnRegularBike.setTextColor(getResources().getColor(android.R.color.white));

            btnScooter.setBackgroundResource(R.drawable.btn_outline);
            btnScooter.setTextColor(getResources().getColor(R.color.primary_blue));
        });

        btnScooter.setOnClickListener(v -> {
            selectedBikeType = "SCOOTER";

            btnScooter.setBackgroundResource(R.drawable.btn_filled);
            btnScooter.setTextColor(getResources().getColor(android.R.color.white));

            btnRegularBike.setBackgroundResource(R.drawable.btn_outline);
            btnRegularBike.setTextColor(getResources().getColor(R.color.primary_blue));
        });
        btnHelmetYes.setOnClickListener(v -> {
            helmetProvided = true;

            btnHelmetYes.setBackgroundResource(R.drawable.btn_filled);
            btnHelmetYes.setTextColor(getResources().getColor(android.R.color.white));

            btnHelmetNo.setBackgroundResource(R.drawable.btn_outline);
            btnHelmetNo.setTextColor(getResources().getColor(R.color.primary_blue));
        });

        btnHelmetNo.setOnClickListener(v -> {
            helmetProvided = false;

            btnHelmetNo.setBackgroundResource(R.drawable.btn_filled);
            btnHelmetNo.setTextColor(getResources().getColor(android.R.color.white));

            btnHelmetYes.setBackgroundResource(R.drawable.btn_outline);
            btnHelmetYes.setTextColor(getResources().getColor(R.color.primary_blue));
        });


        btnConfirmPickup.setOnClickListener(v -> {
            String text = etPickup.getText().toString().trim();

            if (text.isEmpty()) {
                Toast.makeText(this, "Please enter pickup location", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedBikeType.isEmpty()) {
                Toast.makeText(this, "Select bike type", Toast.LENGTH_SHORT).show();
                return;
            }
            if (helmetProvided == null) {
                Toast.makeText(this, "Select helmet option", Toast.LENGTH_SHORT).show();
                return;
            }


            pickupAddress = text;

            // If pickupLat/Lng not set via map → geocode typed address
            if (pickupLat == 0.0 && pickupLng == 0.0) {
                geocodePickupAndProceed(pickupAddress);
            } else {
                proceedToDrop();
            }
        });

    }

    // 🔵 Handle map picker result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICKUP_MAP && resultCode == RESULT_OK && data != null) {
            pickupLat = data.getDoubleExtra("lat", 0.0);
            pickupLng = data.getDoubleExtra("lng", 0.0);
            pickupAddress = data.getStringExtra("address");

            if (pickupAddress == null) pickupAddress = "";

            etPickup.setText(pickupAddress);
            tvPickupInfo.setText("Pickup set from map");
        }
    }

    // 🔵 Convert manually entered address → lat/lng
    private void geocodePickupAndProceed(String addressText) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocationName(addressText, 1);

                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    pickupLat = addr.getLatitude();
                    pickupLng = addr.getLongitude();

                    runOnUiThread(this::proceedToDrop);
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this,
                                    "Could not find location for entered address",
                                    Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this,
                                "Failed to resolve address",
                                Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    // 🔵 Move to DropActivity with correct coordinates
    private void proceedToDrop() {
        Intent intent = new Intent(PickupActivity.this, DropActivity.class);
        intent.putExtra("pickupLat", pickupLat);
        intent.putExtra("pickupLng", pickupLng);
        intent.putExtra("pickupAddress", pickupAddress);
        startActivity(intent);
    }
}
