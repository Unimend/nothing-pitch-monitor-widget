package com.unimend.pitchmonitor;

final class YinDetector {
    private static final float THRESHOLD = 0.16f;
    private final float[] difference;

    YinDetector(int frameSize) { difference = new float[frameSize / 2]; }

    float detect(short[] samples, int count, int sampleRate) {
        int half = Math.min(difference.length, count / 2);
        if (half < 4) return Float.NaN;
        for (int tau = 0; tau < half; tau++) difference[tau] = 0f;
        for (int tau = 1; tau < half; tau++) {
            float sum = 0f;
            int limit = count - tau;
            for (int i = 0; i < limit; i += 2) {
                float delta = samples[i] - samples[i + tau];
                sum += delta * delta;
            }
            difference[tau] = sum;
        }

        difference[0] = 1f;
        float running = 0f;
        for (int tau = 1; tau < half; tau++) {
            running += difference[tau];
            difference[tau] = running == 0f ? 1f : difference[tau] * tau / running;
        }
        int candidate = -1;
        int minimumTau = Math.max(2, sampleRate / 1100);
        for (int tau = minimumTau; tau < half; tau++) {
            if (tau >= minimumTau && difference[tau] < THRESHOLD) {
                while (tau + 1 < half && difference[tau + 1] < difference[tau]) tau++;
                candidate = tau;
                break;
            }
        }
        if (candidate < 1) return Float.NaN;
        float betterTau = candidate;
        if (candidate > 1 && candidate + 1 < half) {
            float left = difference[candidate - 1];
            float center = difference[candidate];
            float right = difference[candidate + 1];
            float denominator = 2f * (2f * center - right - left);
            if (Math.abs(denominator) > 0.000001f) betterTau += (right - left) / denominator;
        }
        float frequency = sampleRate / betterTau;
        return frequency >= 45f && frequency <= 1200f ? frequency : Float.NaN;
    }
}
