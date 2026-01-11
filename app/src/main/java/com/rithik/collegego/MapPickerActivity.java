package com.rithik.collegego; // use your actual package

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MapPickerActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    Button btnConfirmLocation;

    double initialLat = 0.0, initialLng = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_picker);

        btnConfirmLocation = findViewById(R.id.btnConfirmLocation);

        // Get initial lat/lng if passed (optional)
        initialLat = getIntent().getDoubleExtra("initialLat", 0.0);
        initialLng = getIntent().getDoubleExtra("initialLng", 0.0);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapPicker);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        btnConfirmLocation.setOnClickListener(v -> confirmLocation());
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        LatLng startPoint;

        if (initialLat != 0.0 || initialLng != 0.0) {
            startPoint = new LatLng(initialLat, initialLng);
        } else {
            // Default: some city center (Hyderabad for example)
            startPoint = new LatLng(17.3850, 78.4867);
        }

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startPoint, 15f));
    }

    private void confirmLocation() {
        if (mMap == null) {
            Toast.makeText(this, "Map not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        LatLng target = mMap.getCameraPosition().target;
        double lat = target.latitude;
        double lng = target.longitude;

        String address = getAddressFromLatLng(lat, lng);

        Intent data = new Intent();
        data.putExtra("lat", lat);
        data.putExtra("lng", lng);
        data.putExtra("address", address);
        data.putExtra("type", getIntent().getIntExtra("type", -1));

        setResult(RESULT_OK, data);
        finish();
    }

    private String getAddressFromLatLng(double lat, double lng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                StringBuilder sb = new StringBuilder();
                if (addr.getSubThoroughfare() != null) sb.append(addr.getSubThoroughfare()).append(" ");
                if (addr.getThoroughfare() != null) sb.append(addr.getThoroughfare()).append(", ");
                if (addr.getLocality() != null) sb.append(addr.getLocality()).append(", ");
                if (addr.getAdminArea() != null) sb.append(addr.getAdminArea()).append(", ");
                if (addr.getCountryName() != null) sb.append(addr.getCountryName());
                return sb.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Selected location";
    }
}
