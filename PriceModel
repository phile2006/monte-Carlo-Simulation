import java.util.random.RandomGenerator;

/**
 * A stochastic model for the log-return of a stock over one time step.
 */
public interface PriceModel {

    /** Number of trading days per year used for annualization. */
    int TRADING_DAYS = 252;

    /**
     * Draw one log-return for a time step of length dt (in years).
     */
    double sampleLogReturn(RandomGenerator rng, double dt);

    /** Human-readable description for the report header. */
    String describe();
}
