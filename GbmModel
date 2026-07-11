import java.util.random.RandomGenerator;

/**
 * Geometric Brownian Motion: dS/S = mu dt + sigma dW.
 * Log-returns are normal with mean (mu - sigma^2/2) dt and stdev sigma sqrt(dt).
 * @param mu    annualized drift (e.g. 0.08 for 8%/yr)
 * @param sigma annualized volatility (e.g. 0.20 for 20%/yr)
 */
public record GbmModel(double mu, double sigma) implements PriceModel {

    @Override
    public double sampleLogReturn(RandomGenerator rng, double dt) {
        return (mu - 0.5 * sigma * sigma) * dt + sigma * Math.sqrt(dt) * rng.nextGaussian();
    }

    @Override
    public String describe() {
        return String.format("GBM  (mu=%.2f%%/yr, sigma=%.2f%%/yr)", mu * 100, sigma * 100);
    }
}
