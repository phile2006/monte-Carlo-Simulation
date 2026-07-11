import java.util.random.RandomGenerator;

/**
 * Merton jump-diffusion: GBM plus a compound Poisson jump component.
 * Jumps arrive at rate lambda per year; each jump multiplies the price by
 * exp(J) with J ~ Normal(jumpMean, jumpStd).
 *
 * The diffusion drift is compensated by lambda * (E[e^J] - 1) so that `mu`
 * remains the total expected return of the process including jumps.
 *
 * @param mu       annualized total expected drift
 * @param sigma    annualized diffusion volatility
 * @param lambda   expected number of jumps per year
 * @param jumpMean mean of the log-jump size (negative for crash-like jumps)
 * @param jumpStd  stdev of the log-jump size
 */
public record JumpDiffusionModel(double mu, double sigma, double lambda, double jumpMean, double jumpStd) implements PriceModel {

    @Override
    public double sampleLogReturn(RandomGenerator rng, double dt) {
        double kappa = Math.expm1(jumpMean + 0.5 * jumpStd * jumpStd); // E[e^J] - 1
        double diffusion = (mu - lambda * kappa - 0.5 * sigma * sigma) * dt
                + sigma * Math.sqrt(dt) * rng.nextGaussian();

        int jumps = samplePoisson(rng, lambda * dt);
        double jumpSum = 0.0;
        for (int i = 0; i < jumps; i++) {
            jumpSum += jumpMean + jumpStd * rng.nextGaussian();
        }
        return diffusion + jumpSum;
    }

    /** Knuth's algorithm*/
    private static int samplePoisson(RandomGenerator rng, double mean) {
        double l = Math.exp(-mean);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= rng.nextDouble();
        } while (p > l);
        return k - 1;
    }

    
    @Override
    public String describe() {
        return String.format(
                "Merton jump-diffusion  (mu=%.2f%%/yr, sigma=%.2f%%/yr, lambda=%.1f/yr, jump ~ N(%.2f%%, %.2f%%))",
                mu * 100, sigma * 100, lambda, jumpMean * 100, jumpStd * 100);
    }
}
