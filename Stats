import java.util.Arrays;

/**
 * Descriptive statistics over a sample of doubles.
 */
public final class Stats {

    private final double[] sorted;
    private final double mean;
    private final double std;

    public Stats(double[] values) {
        this.sorted = values.clone();
        Arrays.sort(this.sorted);
        double sum = 0;
        for (double v : values) sum += v;
        this.mean = sum / values.length;
        double sq = 0;
        for (double v : values) sq += (v - mean) * (v - mean);
        this.std = Math.sqrt(sq / (values.length - 1));
    }

    public double mean()   { return mean; }
    public double std()    { return std; }
    public double min()    { return sorted[0]; }
    public double max()    { return sorted[sorted.length - 1]; }
    public double median() { return percentile(50); }

    /** Linear-interpolated percentile, p in [0, 100]. */
    public double percentile(double p) {
        double idx = p / 100.0 * (sorted.length - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        double frac = idx - lo;
        return sorted[lo] * (1 - frac) + sorted[hi] * frac;
    }

    /** Fraction of samples strictly below the threshold. */
    public double probBelow(double threshold) {
        int i = Arrays.binarySearch(sorted, threshold);
        int insertion = i >= 0 ? i : -(i + 1);
        return (double) insertion / sorted.length;
    }

    /**
     * Expected value of the samples at or below the p-th percentile
     * (used for CVaR / expected shortfall on a return distribution).
     */
    public double tailMean(double p) {
        int n = Math.max(1, (int) Math.floor(p / 100.0 * sorted.length));
        double sum = 0;
        for (int i = 0; i < n; i++) sum += sorted[i];
        return sum / n;
    }

    /** Simple ASCII histogram of the sample, `bins` rows tall. */
    public String histogram(int bins, int width) {
        double lo = percentile(0.5), hi = percentile(99.5); // clip extreme tails for readability
        double binW = (hi - lo) / bins;
        if (binW <= 0) return "(degenerate distribution)\n";
        int[] counts = new int[bins];
        for (double v : sorted) {
            int b = (int) ((v - lo) / binW);
            if (b >= 0 && b < bins) counts[b]++;
        }
        int maxCount = Arrays.stream(counts).max().orElse(1);
        StringBuilder sb = new StringBuilder();
        for (int b = 0; b < bins; b++) {
            double mid = lo + (b + 0.5) * binW;
            int barLen = (int) Math.round((double) counts[b] / maxCount * width);
            sb.append(String.format("%10.2f | %s%n", mid, "█".repeat(barLen)));
        }
        return sb.toString();
    }
}
