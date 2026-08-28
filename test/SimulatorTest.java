import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the simulator against properties that can be derived on paper,
 * so a wrong result is caught by the build instead of by eye.
 */
class SimulatorTest {

    private static final double S0 = 100.0;
    private static final double MU = 0.08;
    private static final double SIGMA = 0.20;
    private static final int DAYS = PriceModel.TRADING_DAYS;   // one year
    private static final long SEED = 42L;

    private static double mean(Simulator.PathResult[] results) {
        double sum = 0;
        for (Simulator.PathResult r : results) sum += r.terminalPrice();
        return sum / results.length;
    }

    @Test
    @DisplayName("mean terminal price matches the analytic GBM expectation E[S_T] = S0 * e^(mu*T)")
    void meanTerminalPriceMatchesAnalyticExpectation() {
        int paths = 200_000;
        var results = new Simulator(new GbmModel(MU, SIGMA), SEED).run(S0, DAYS, paths);

        double expected = S0 * Math.exp(MU * 1.0);
        double actual = mean(results);
        double relativeError = Math.abs(actual - expected) / expected;

        // The standard error of the mean here is about 0.05, so 0.5 % is a wide
        // band statistically but still narrow enough to catch a missing Ito
        // correction, which would shift the mean by e^(sigma^2/2) = +2 %.
        assertTrue(relativeError < 0.005,
                "expected ~%.4f but simulated %.4f (relative error %.4f%%)"
                        .formatted(expected, actual, relativeError * 100));
    }

    @Test
    @DisplayName("the error shrinks when the number of paths grows")
    void errorShrinksWithMorePaths() {
        double expected = S0 * Math.exp(MU * 1.0);
        var model = new GbmModel(MU, SIGMA);

        double errorFew = Math.abs(mean(new Simulator(model, SEED).run(S0, DAYS, 1_000)) - expected);
        double errorMany = Math.abs(mean(new Simulator(model, SEED).run(S0, DAYS, 400_000)) - expected);

        // Monte Carlo error falls with 1/sqrt(n), so a 400-fold increase should
        // cut it by roughly 20x. Asserting only "smaller" keeps this robust.
        assertTrue(errorMany < errorFew,
                "error with 400k paths (%.4f) should be below the error with 1k paths (%.4f)"
                        .formatted(errorMany, errorFew));
    }

    @Test
    @DisplayName("without volatility every path is the deterministic drift path")
    void zeroVolatilityIsDeterministic() {
        var results = new Simulator(new GbmModel(MU, 0.0), SEED).run(S0, DAYS, 100);

        double expected = S0 * Math.exp(MU * 1.0);
        for (Simulator.PathResult r : results) {
            assertEquals(expected, r.terminalPrice(), 1e-9,
                    "with sigma = 0 the terminal price must equal the drift path exactly");
            assertEquals(0.0, r.maxDrawdown(), 1e-12,
                    "a strictly rising path cannot have a drawdown");
        }
    }

    @Test
    @DisplayName("the same seed gives identical results despite parallel execution")
    void sameSeedIsReproducible() {
        var model = new GbmModel(MU, SIGMA);
        var first = new Simulator(model, SEED).run(S0, DAYS, 50_000);
        var second = new Simulator(model, SEED).run(S0, DAYS, 50_000);

        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i].terminalPrice(), second[i].terminalPrice(), 0.0,
                    "path " + i + " differs between two runs with the same seed");
        }
    }

    @Test
    @DisplayName("a different seed gives different paths")
    void differentSeedChangesTheResult() {
        var model = new GbmModel(MU, SIGMA);
        var first = new Simulator(model, SEED).run(S0, DAYS, 1_000);
        var other = new Simulator(model, SEED + 1).run(S0, DAYS, 1_000);

        boolean anyDifferent = false;
        for (int i = 0; i < first.length && !anyDifferent; i++) {
            anyDifferent = first[i].terminalPrice() != other[i].terminalPrice();
        }
        assertTrue(anyDifferent, "a different seed must not reproduce the same paths");
    }

    @Test
    @DisplayName("drawdown stays inside [0, 1)")
    void drawdownIsWithinBounds() {
        var results = new Simulator(new GbmModel(MU, SIGMA), SEED).run(S0, DAYS, 10_000);

        for (Simulator.PathResult r : results) {
            assertFalse(r.maxDrawdown() < 0.0, "drawdown must not be negative");
            assertTrue(r.maxDrawdown() < 1.0, "a price above zero cannot lose 100 %");
        }
    }
}
