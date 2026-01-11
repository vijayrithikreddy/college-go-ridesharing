package com.rithik.collegego;  // use your actual package

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class DropActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_DROP_MAP = 2002;

    ImageView ivBackDrop;
    EditText etDrop, etTime, etAmount;
    TextView tvDropInfo;
    LinearLayout layoutSelectOnMapDrop;
    Button btnConfirmDrop;

    double pickupLat, pickupLng;
    String pickupAddress;

    double dropLat = 0.0;
    double dropLng = 0.0;
    String dropAddress = "";
    long selectedTimeMillis = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drop);

        ivBackDrop = findViewById(R.id.ivBackDroped);
        etDrop = findViewById(R.id.etDroped);
        etTime = findViewById(R.id.etdropTime);
        etAmount = findViewById(R.id.etAmount);
        tvDropInfo = findViewById(R.id.tvDropedInfo);
        layoutSelectOnMapDrop = findViewById(R.id.layoutSelectOnMapDroped);
        btnConfirmDrop = findViewById(R.id.btnConfirmDroped);

        // Get pickup details from Intent
        pickupLat = getIntent().getDoubleExtra("pickupLat", 0.0);
        pickupLng = getIntent().getDoubleExtra("pickupLng", 0.0);
        pickupAddress = getIntent().getStringExtra("pickupAddress");

        ivBackDrop.setOnClickListener(v -> onBackPressed());
        etTime.setOnClickListener(v -> openTimePicker());

        layoutSelectOnMapDrop.setOnClickListener(v -> {
            Intent intent = new Intent(DropActivity.this, MapPickerActivity.class);
            startActivityForResult(intent, REQUEST_CODE_DROP_MAP);
        });


        btnConfirmDrop.setOnClickListener(v -> handleConfirmDrop());
    }
    private void openTimePicker() {

        java.util.Calendar now = java.util.Calendar.getInstance();

        int currentHour = now.get(java.util.Calendar.HOUR_OF_DAY);
        int currentMinute = now.get(java.util.Calendar.MINUTE);

        android.app.TimePickerDialog dialog =
                new android.app.TimePickerDialog(
                        this,
                        (view, hourOfDay, minute) -> {

                            java.util.Calendar selected =
                                    java.util.Calendar.getInstance();

                            selected.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay);
                            selected.set(java.util.Calendar.MINUTE, minute);
                            selected.set(java.util.Calendar.SECOND, 0);

                            // 🔴 PREVENT PAST TIME
                            if (selected.before(java.util.Calendar.getInstance())) {
                                Toast.makeText(
                                        this,
                                        "Please select a future time",
                                        Toast.LENGTH_SHORT
                                ).show();
                                return;
                            }

                            // ✅ FORMAT FOR UI
                            String formatted =
                                    new java.text.SimpleDateFormat(
                                            "hh:mm a",
                                            java.util.Locale.getDefault()
                                    ).format(selected.getTime());

                            etTime.setText(formatted);

                            // ✅ STORE FOR LOGIC
                            selectedTimeMillis = selected.getTimeInMillis();

                        },
                        currentHour,
                        currentMinute,
                        false
                );

        dialog.show();
    }


    private void handleConfirmDrop() {
        String dropText = etDrop.getText().toString().trim();
        String timeText = etTime.getText().toString().trim();
        String amountText = etAmount.getText().toString().trim();

        if (dropText.isEmpty()) {
            Toast.makeText(this, "Please enter drop location", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedTimeMillis == 0L) {
            Toast.makeText(this, "Please select time", Toast.LENGTH_SHORT).show();
            return;
        }

        if (amountText.isEmpty()) {
            Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show();
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountText);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show();
            return;
        }

        // Convert drop address text to lat/lng (simple geocoding)
        dropAddress = dropText;
        double[] coords = getLatLngFromAddress(dropText);
        dropLat = coords[0];
        dropLng = coords[1];

        if (dropLat == 0.0 && dropLng == 0.0) {
            Toast.makeText(this, "Could not get coordinates for drop. Using text only.", Toast.LENGTH_SHORT).show();
        }

        // Go to OfferRideActivity with all details
        android.content.Intent intent = new android.content.Intent(DropActivity.this, OfferRideActivity.class);
        intent.putExtra("pickupLat", pickupLat);
        intent.putExtra("pickupLng", pickupLng);
        intent.putExtra("pickupAddress", pickupAddress);

        intent.putExtra("dropLat", dropLat);
        intent.putExtra("dropLng", dropLng);
        intent.putExtra("dropAddress", dropAddress);

        intent.putExtra("timeText", etTime.getText().toString());
        intent.putExtra("timeMillis", selectedTimeMillis);

        intent.putExtra("amount", amount);

        startActivity(intent);

    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_DROP_MAP && resultCode == RESULT_OK && data != null) {
            dropLat = data.getDoubleExtra("lat", 0.0);
            dropLng = data.getDoubleExtra("lng", 0.0);
            dropAddress = data.getStringExtra("address");
            if (dropAddress == null) dropAddress = "";

            etDrop.setText(dropAddress);
            tvDropInfo.setText("Drop set from map");
        }
    }


    private double[] getLatLngFromAddress(String addressStr) {
        double[] result = {0.0, 0.0};
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(addressStr, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                result[0] = addr.getLatitude();
                result[1] = addr.getLongitude();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }
}
