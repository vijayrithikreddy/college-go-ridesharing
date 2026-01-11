package com.rithik.collegego;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RideAdapter extends RecyclerView.Adapter<RideAdapter.RideViewHolder> {

    List<RideModel> list;

    // Optional pillion data (may be null if not provided)
    private String pillionEncoded = null;
    private double pillionPickupLat = 0.0;
    private double pillionPickupLng = 0.0;
    private double pillionDropLat = 0.0;
    private double pillionDropLng = 0.0;

    /**
     * Legacy constructor - behaves exactly like before (no click actions will be added).
     */
    public RideAdapter(List<RideModel> list) {
        this.list = list;
    }

    /**
     * New constructor - provide pillion route and coords so the adapter can launch RideDetailsActivity
     * when a card is clicked.
     */
    public RideAdapter(List<RideModel> list,
                       String pillionEncoded,
                       double pillionPickupLat,
                       double pillionPickupLng,
                       double pillionDropLat,
                       double pillionDropLng) {
        this.list = list;
        this.pillionEncoded = pillionEncoded;
        this.pillionPickupLat = pillionPickupLat;
        this.pillionPickupLng = pillionPickupLng;
        this.pillionDropLat = pillionDropLat;
        this.pillionDropLng = pillionDropLng;
    }

    @NonNull
    @Override
    public RideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ride, parent, false);
        return new RideViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RideViewHolder holder, int position) {
        RideModel rm = list.get(position);

        holder.tvRiderName.setText(rm.riderName);
        holder.tvRoute.setText(rm.pickupAddress + " → " + rm.dropAddress);
        holder.tvTime.setText("Time: " + rm.time);
        holder.tvAmount.setText("₹" + rm.amount);
        holder.tvMatch.setText("Match: " + rm.overlapPercent + "%");

        // If pillion data was passed to adapter, enable click to open RideDetailsActivity
        if (pillionEncoded != null && !pillionEncoded.isEmpty()) {
            holder.itemView.setOnClickListener(v -> {
                Intent i = new Intent(v.getContext(), RideDetailsActivity.class);
                i.putExtra("rideId", rm.rideId);
                i.putExtra("pillionPolyline", pillionEncoded);
                i.putExtra("pillionPickupLat", pillionPickupLat);
                i.putExtra("pillionPickupLng", pillionPickupLng);
                i.putExtra("pillionDropLat", pillionDropLat);
                i.putExtra("pillionDropLng", pillionDropLng);
                i.putExtra("matchPercent", rm.overlapPercent);
                v.getContext().startActivity(i);
            });
        } else {
            // ensure no stale click listener remains when using legacy constructor
            holder.itemView.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class RideViewHolder extends RecyclerView.ViewHolder {
        TextView tvRiderName, tvRoute, tvTime, tvAmount, tvMatch;

        public RideViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRiderName = itemView.findViewById(R.id.tvRiderName);
            tvRoute = itemView.findViewById(R.id.tvRoute);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvMatch = itemView.findViewById(R.id.tvMatch);
        }
    }
}
