package com.rithik.collegego;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class RiderRequestsAdapter
        extends RecyclerView.Adapter<RiderRequestsAdapter.RequestViewHolder> {

    List<RideRequestModel> list;

    public RiderRequestsAdapter(List<RideRequestModel> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {

        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ride, parent, false);
        return new RequestViewHolder(v);
    }

    @Override
    public void onBindViewHolder(
            @NonNull RequestViewHolder holder, int position) {

        RideRequestModel rm = list.get(position);
        Context context = holder.itemView.getContext();

        // 🔵 Reuse item_ride fields
        holder.tvRiderName.setText(
                rm.pillionName != null ? rm.pillionName : "Pillion"
        );

        holder.tvRoute.setText(
                (rm.pickupAddress != null ? rm.pickupAddress : "Pickup")
                        + " → " +
                        (rm.dropAddress != null ? rm.dropAddress : "Drop")
        );

        // 🔹 Time
        String time = rm.time != null && !rm.time.isEmpty()
                ? rm.time
                : "Time not set";
        holder.tvTime.setText(time);

// 🔹 Amount
        holder.tvAmount.setText("₹" + rm.amount);

// 🔹 Match percent (INT)
        int match = (int) Math.round(rm.matchPercent);
        holder.tvMatch.setText(match + "%\n Match");

        holder.tvAmount.setText("Tap to view details");


        // ✅ ONLY ACTION: open RideDetailsActivity
        holder.itemView.setOnClickListener(v -> {

            Intent i = new Intent(context, RideDetailsActivity.class);

            i.putExtra("rideId", rm.rideId);

            // 🔑 pillion data (THIS fixes rider-side map)
            i.putExtra("pillionPolyline", rm.pillionPolyline);
            i.putExtra("matchPercent", rm.matchPercent);

            i.putExtra("pillionPickupLat", rm.pickupLat);
            i.putExtra("pillionPickupLng", rm.pickupLng);
            i.putExtra("pillionDropLat", rm.dropLat);
            i.putExtra("pillionDropLng", rm.dropLng);

            context.startActivity(i);
        });

    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class RequestViewHolder extends RecyclerView.ViewHolder {

        TextView tvRiderName, tvRoute, tvTime, tvAmount, tvMatch;

        public RequestViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRiderName = itemView.findViewById(R.id.tvRiderName);
            tvRoute = itemView.findViewById(R.id.tvRoute);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvMatch = itemView.findViewById(R.id.tvMatch);
        }
    }
}
