import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.IntStream;

/**
 * Runs Monte Carlo price paths for a given model.
 * Paths are simulated in parallel; each path gets its own splittable RNG
 * stream so results are reproducible for a fixed seed regardless of
 * thread scheduling.
 */
public final class Simulator {

    /** Per-path outcome: terminal price and worst peak-to-trough drawdown. */
    public record PathResult(double terminalPrice, double maxDrawdown) {}

    private final PriceModel model;
    private final long seed;

    public Simulator(PriceModel model, long seed) {
        this.model = model;
        this.seed = seed;
    }

    /**
     * Simulate {@code numPaths} paths of {@code days} daily steps starting at {@code s0}.
     */
    public PathResult[] run(double s0, int days, int numPaths) {
        var factory = RandomGeneratorFactory.<RandomGenerator.SplittableGenerator>of("L64X128MixRandom");
        RandomGenerator.SplittableGenerator root = factory.create(seed);
        // Pre-split one independent stream per path (deterministic order).
        RandomGenerator[] rngs = new RandomGenerator[numPaths];
        for (int i = 0; i < numPaths; i++) {
            rngs[i] = root.split();
        }

        double dt = 1.0 / PriceModel.TRADING_DAYS;
        PathResult[] results = new PathResult[numPaths];
        IntStream.range(0, numPaths).parallel().forEach(p -> {
            RandomGenerator rng = rngs[p];
            double logPrice = Math.log(s0);
            double peakLog = logPrice;
            double maxDd = 0.0;
            for (int d = 0; d < days; d++) {
                logPrice += model.sampleLogReturn(rng, dt);
                if (logPrice > peakLog) {
                    peakLog = logPrice;
                } else {
                    double dd = 1.0 - Math.exp(logPrice - peakLog);
                    if (dd > maxDd) maxDd = dd;
                }
            }
            results[p] = new PathResult(Math.exp(logPrice), maxDd);
        });
        return results;
    }
