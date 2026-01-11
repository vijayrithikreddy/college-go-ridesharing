package com.rithik.collegego; // <-- replace with your actual package

import com.google.android.gms.maps.model.LatLng;
import java.util.ArrayList;
import java.util.List;

public class PolylineMatchUtils {

    // Decode encoded polyline
    public static List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lng += dlng;

            LatLng p = new LatLng((double) lat / 1E5, (double) lng / 1E5);
            poly.add(p);
        }
        return poly;
    }

    // Haversine distance in meters
    public static double haversineDistance(LatLng a, LatLng b) {
        double R = 6371000.0;
        double dLat = Math.toRadians(b.latitude - a.latitude);
        double dLon = Math.toRadians(b.longitude - a.longitude);
        double lat1 = Math.toRadians(a.latitude);
        double lat2 = Math.toRadians(b.latitude);

        double x = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x));
        return R * c;
    }

    // Distance from point to segment AB
    public static double pointToSegmentDistance(LatLng p, LatLng a, LatLng b) {

        double A_lat = Math.toRadians(a.latitude);
        double A_lng = Math.toRadians(a.longitude);
        double B_lat = Math.toRadians(b.latitude);
        double B_lng = Math.toRadians(b.longitude);
        double P_lat = Math.toRadians(p.latitude);
        double P_lng = Math.toRadians(p.longitude);

        double xAB = B_lng - A_lng;
        double yAB = B_lat - A_lat;
        double xAP = P_lng - A_lng;
        double yAP = P_lat - A_lat;

        double ab2 = xAB * xAB + yAB * yAB;
        double t = 0;
        if (ab2 > 0) t = (xAP * xAB + yAP * yAB) / ab2;
        if (t < 0) t = 0;
        if (t > 1) t = 1;

        double projLat = A_lat + t * yAB;
        double projLng = A_lng + t * xAB;

        LatLng proj = new LatLng(Math.toDegrees(projLat), Math.toDegrees(projLng));
        return haversineDistance(p, proj);
    }

    // Overlap meters between pillion and rider routes
    public static double computeOverlapMeters(List<LatLng> pillionPts, List<LatLng> riderPts, double thresholdMeters) {
        if (pillionPts == null || riderPts == null || pillionPts.size() < 2) return 0;

        double overlapSum = 0;

        for (int i = 0; i < pillionPts.size() - 1; i++) {

            LatLng pA = pillionPts.get(i);
            LatLng pB = pillionPts.get(i + 1);

            double segLen = haversineDistance(pA, pB);
            LatLng mid = new LatLng((pA.latitude + pB.latitude) / 2, (pA.longitude + pB.longitude) / 2);

            LatLng[] samples = new LatLng[]{pA, mid, pB};

            boolean overlapped = false;

            for (LatLng sample : samples) {
                double minDist = Double.MAX_VALUE;

                for (int j = 0; j < riderPts.size() - 1; j++) {
                    LatLng rA = riderPts.get(j);
                    LatLng rB = riderPts.get(j + 1);

                    double d = pointToSegmentDistance(sample, rA, rB);
                    if (d < minDist) minDist = d;
                    if (minDist <= thresholdMeters) break;
                }

                if (minDist <= thresholdMeters) {
                    overlapped = true;
                    break;
                }
            }

            if (overlapped) {
                overlapSum += segLen;
            }
        }

        return overlapSum;
    }

    // Overlap percentage
    public static double computeOverlapPercent(List<LatLng> pillionPts, List<LatLng> riderPts, double thresholdMeters) {

        double total = 0;
        for (int i = 0; i < pillionPts.size() - 1; i++) {
            total += haversineDistance(pillionPts.get(i), pillionPts.get(i + 1));
        }

        if (total <= 0) return 0;

        double overlap = computeOverlapMeters(pillionPts, riderPts, thresholdMeters);

        return (overlap / total) * 100.0;
    }
}
