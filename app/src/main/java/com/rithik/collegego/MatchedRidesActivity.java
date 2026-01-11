package com.rithik.collegego; // replace if needed

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MatchedRidesActivity extends AppCompatActivity {

    private static final String TAG = "MatchedRidesActivity";
    private static final String DIRECTIONS_API_KEY = "google_maps_api"; // replace

    RecyclerView rvRides;
    RideAdapter adapter;
    List<RideModel> rideList = new ArrayList<>();
    TextView tvNoRides;

    // pillion inputs
    double pPickupLat, pPickupLng, pDropLat, pDropLng;
    String pillionEncoded = null; // may be passed or fetched

    FirebaseFirestore db;
    ExecutorService bg = Executors.newSingleThreadExecutor();

    // tuneable parameters
    final double DIST_THRESHOLD_METERS = 150.0; // how close to consider overlapping
    final double MATCH_THRESHOLD_PERCENT = 50.0; // require >= 50% overlap
    final int CANDIDATE_LIMIT = 50;
    private static final double INVALID_COORD = -1.0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_matched_rides);

        rvRides = findViewById(R.id.rvRides);
        rvRides.setLayoutManager(new LinearLayoutManager(this));

        db = FirebaseFirestore.getInstance();

        // ✅ FIRST read intent extras
        tvNoRides = findViewById(R.id.tvEmpty);

        pPickupLat = getIntent().getDoubleExtra("pickupLat", INVALID_COORD);
        pPickupLng = getIntent().getDoubleExtra("pickupLng", INVALID_COORD);
        pDropLat = getIntent().getDoubleExtra("dropLat", 0.0);
        pDropLng = getIntent().getDoubleExtra("dropLng", 0.0);
        pillionEncoded = getIntent().getStringExtra("pillionPolyline"); // may be null
        rvRides.setHasFixedSize(true);

        // ✅ NOW create adapter (with correct values)
        adapter = new RideAdapter(
                rideList,
                pillionEncoded,
                pPickupLat,
                pPickupLng,
                pDropLat,
                pDropLng
        );
        rvRides.setAdapter(adapter);

        if ((pPickupLat == INVALID_COORD || pPickupLng == INVALID_COORD)
                && (pillionEncoded == null || pillionEncoded.isEmpty())) {
            Toast.makeText(this, "Missing pillion route. Go back and enter pickup/drop.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Fetch pillion polyline if not passed
        if (pillionEncoded == null || pillionEncoded.isEmpty()) {
            Toast.makeText(this, "Computing route preview...", Toast.LENGTH_SHORT).show();
            bg.execute(this::fetchPillionPolylineThenMatch);
        } else {
            bg.execute(this::fetchCandidatesAndMatch);
        }
    }

    // If pillionEncoded missing, call Directions API to get it
    private void fetchPillionPolylineThenMatch() {
        try {
            String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=" + pPickupLat + "," + pPickupLng +
                    "&destination=" + pDropLat + "," + pDropLng +
                    "&key=" + DIRECTIONS_API_KEY;

            URL u = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            conn.disconnect();

            JSONObject json = new JSONObject(sb.toString());
            JSONArray routes = json.getJSONArray("routes");
            if (routes.length() == 0) {
                runOnUiThread(() -> Toast.makeText(this, "Could not compute pillion route", Toast.LENGTH_SHORT).show());
                return;
            }
            JSONObject route = routes.getJSONObject(0);
            JSONObject poly = route.getJSONObject("overview_polyline");
            pillionEncoded = poly.getString("points");

            // proceed to fetch candidates
            fetchCandidatesAndMatch();

        } catch (Exception e) {
            Log.e(TAG, "Directions API error: " + e.getMessage(), e);
            runOnUiThread(() -> Toast.makeText(this, "Failed to compute pillion route", Toast.LENGTH_SHORT).show());
        }
    }

    // Main matching flow: query Firestore, decode polylines, compute overlap%, filter & sort
    private void fetchCandidatesAndMatch() {
        try {
            // decode pillion polyline
            List<LatLng> pillionPts = PolylineMatchUtils.decodePolyline(pillionEncoded);
            if (pillionPts == null || pillionPts.size() < 2) {
                runOnUiThread(() -> Toast.makeText(this, "Pillion route invalid", Toast.LENGTH_SHORT).show());
                return;
            }

            // Query Firestore for candidate rides (status == "CREATED")
            // Optionally you could add time-range filters here if you pass a time
            db.collection("rides")
                    .whereEqualTo("status", "CREATED")
                    .limit(CANDIDATE_LIMIT)
                    .get()
                    .addOnSuccessListener((QuerySnapshot snapshot) -> {
                        bg.execute(() -> {
                            List<RideModel> matches = new ArrayList<>();

                            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                                try {
                                    String riderId = doc.getString("riderId");
                                    String pickupAddress = doc.getString("pickupAddress");
                                    String dropAddress = doc.getString("dropAddress");
                                    String riderPoly = doc.getString("overview_polyline");
                                    long amountLong = 0;
                                    try { amountLong = doc.getLong("amount") != null ? doc.getLong("amount") : 0; } catch (Exception ignored) {}
                                    int amount = (int) amountLong;
                                    String time = "";
                                    try { time = doc.getString("dateTime"); } catch (Exception ignored) {}

                                    if (riderPoly == null || riderPoly.isEmpty()) continue;

                                    List<LatLng> riderPts = PolylineMatchUtils.decodePolyline(riderPoly);
                                    if (riderPts == null || riderPts.size() < 2) continue;

                                    double rawPercent = PolylineMatchUtils.computeOverlapPercent(
                                            pillionPts,
                                            riderPts,
                                            DIST_THRESHOLD_METERS
                                    );

                                    int matchPercent = (int) Math.round(rawPercent);

// optional safety clamp
                                    matchPercent = Math.max(0, Math.min(100, matchPercent));

                                    if (matchPercent>= MATCH_THRESHOLD_PERCENT) {
                                        // get rider name if available (optional)
                                        String riderName = "Rider";
                                        if (doc.contains("riderName") && doc.getString("riderName") != null) {
                                            riderName = doc.getString("riderName");
                                        }


                                        RideModel rm = new RideModel(doc.getId(), riderName, pickupAddress, dropAddress, time, amount, matchPercent);
                                        matches.add(rm);
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Skipping ride " + doc.getId() + " due to invalid data");
                                }
                            }

                            // sort by overlap percent desc
                            Collections.sort(matches, new Comparator<RideModel>() {
                                @Override
                                public int compare(RideModel a, RideModel b) {
                                    return Double.compare(b.overlapPercent, a.overlapPercent);
                                }
                            });
                            if (isFinishing() || isDestroyed()) return;

                            // update UI
                            runOnUiThread(() -> {
                                rideList.clear();
                                rideList.addAll(matches);
                                adapter.notifyDataSetChanged();

                                if (rideList.isEmpty()) {
                                    tvNoRides.setVisibility(View.VISIBLE);
                                    rvRides.setVisibility(View.GONE);
                                } else {
                                    tvNoRides.setVisibility(View.GONE);
                                    rvRides.setVisibility(View.VISIBLE);
                                }

                            });
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Firestore query failed: " + e.getMessage(), e);
                        runOnUiThread(() -> Toast.makeText(MatchedRidesActivity.this, "Failed to fetch rides", Toast.LENGTH_SHORT).show());
                    });

        } catch (Exception e) {
            Log.e(TAG, "matching error: " + e.getMessage(), e);
            runOnUiThread(() -> Toast.makeText(this, "Matching error", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!bg.isShutdown()) {
            bg.shutdownNow();
        }

    }
}
