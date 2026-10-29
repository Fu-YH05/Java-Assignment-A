package com.cst3492.ta.util;

/** Vector math shared by the writer and the retriever. */
public final class VectorMath {

    private VectorMath() {
    }

    /** L2-normalises the vector in place; zero vectors are left untouched. */
    public static void normalize(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += x * x;
        }
        if (sum <= 0) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < v.length; i++) {
            v[i] /= norm;
        }
    }

    /** Dot product of two equal-length vectors. */
    public static float dot(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("dimension mismatch: " + a.length + " vs " + b.length);
        }
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
