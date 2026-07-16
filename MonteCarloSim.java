import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Monte Carlo stock price simulator.
 * Usage:
 *   java MonteCarloSim.java [options]
 *
 * Options (all have defaults):
 *   --s0 <price>        starting price                       (default 100)
 *   --mu <drift>        annualized drift, decimal            (default 0.08)
 *   --sigma <vol>       annualized volatility, decimal       (default 0.20)
 *   --days <n>          horizon in trading days              (default 252)
 *   --paths <n>         number of simulated paths            (default 100000)
 *   --seed <n>          RNG seed for reproducibility         (default 42)
 *   --model <name>      gbm | jump                           (default gbm)
 *   --lambda <n>        [jump] jumps per year                (default 4)
 *   --jump-mean <x>     [jump] mean log-jump size            (default -0.03)
 *   --jump-std <x>      [jump] stdev of log-jump size        (default 0.05)
 *   --target <price>    also report P(terminal >= target)
 *   --export <file>     write 20 sample paths as CSV
 */

public final class MonteCarloSim {

    public static void main(String[] args) {
        Map<String, String> opts = parseArgs(args);

        double s0     = getD(opts, "s0", 100.0);
        double mu     = getD(opts, "mu", 0.08);
        double sigma  = getD(opts, "sigma", 0.20);
        int    days   = (int) getD(opts, "days", 252);
        int    paths  = (int) getD(opts, "paths", 100_000);
        long   seed   = (long) getD(opts, "seed", 42);
        String modelName = opts.getOrDefault("model", "gbm");

        PriceModel model = switch (modelName) {
            case "gbm"  -> new GbmModel(mu, sigma);
            case "jump" -> new JumpDiffusionModel(mu, sigma,
                    getD(opts, "lambda", 4.0),
                    getD(opts, "jump-mean", -0.03),
                    getD(opts, "jump-std", 0.05));
            default -> throw new IllegalArgumentException(
                    "Unknown model '" + modelName + "' (use gbm or jump)");
        };

        Simulator sim = new Simulator(model, seed);
        long t0 = System.nanoTime();
        Simulator.PathResult[] results = sim.run(s0, days, paths);
        double elapsedMs = (System.nanoTime() - t0) / 1e6;

        double[] terminal  = new double[paths];
        double[] returns   = new double[paths];   // simple return over the horizon
        double[] drawdowns = new double[paths];

        for (int i = 0; i < paths; i++) {
            terminal[i]  = results[i].terminalPrice();
            returns[i]   = terminal[i] / s0 - 1.0;
            drawdowns[i] = results[i].maxDrawdown();
        }
        Stats priceStats = new Stats(terminal);
        Stats retStats   = new Stats(returns);
        Stats ddStats    = new Stats(drawdowns);

        double years = days / (double) PriceModel.TRADING_DAYS;

        System.out.println("=".repeat(64));
        System.out.println("Monte Carlo Stock Simulator");
        System.out.println("=".repeat(64));
        System.out.printf ("Model     : %s%n", model.describe());
        System.out.printf ("Start     : %.2f   Horizon: %d trading days (%.2f yr)%n", s0, days, years);
        System.out.printf ("Paths     : %,d   Seed: %d   Runtime: %.0f ms%n", paths, seed, elapsedMs);
        System.out.println();

        System.out.println("Terminal price distribution");
        System.out.printf ("  mean %.2f   median %.2f   stdev %.2f%n",
                priceStats.mean(), priceStats.median(), priceStats.std());
        System.out.printf ("  p5 %.2f   p25 %.2f   p75 %.2f   p95 %.2f%n",
                priceStats.percentile(5), priceStats.percentile(25),
                priceStats.percentile(75), priceStats.percentile(95));
        System.out.printf ("  min %.2f   max %.2f%n", priceStats.min(), priceStats.max());
        System.out.println();

        System.out.println("Horizon return");
        System.out.printf ("  mean %+.2f%%   median %+.2f%%   P(loss) %.1f%%%n",
                retStats.mean() * 100, retStats.median() * 100,
                retStats.probBelow(0.0) * 100);
        System.out.printf ("  VaR 95%% %+.2f%%   VaR 99%% %+.2f%%   (return at percentile)%n",
                retStats.percentile(5) * 100, retStats.percentile(1) * 100);
        System.out.printf ("  CVaR 95%% %+.2f%%   CVaR 99%% %+.2f%%   (mean of tail)%n",
                retStats.tailMean(5) * 100, retStats.tailMean(1) * 100);
        if (opts.containsKey("target")) {
            double target = Double.parseDouble(opts.get("target"));
            System.out.printf("  P(terminal >= %.2f) = %.1f%%%n",
                    target, (1.0 - priceStats.probBelow(target)) * 100);
        }
        System.out.println();

        System.out.println("Max drawdown along path");
        System.out.printf ("  median %.1f%%   p75 %.1f%%   p95 %.1f%%   worst %.1f%%%n",
                ddStats.median() * 100, ddStats.percentile(75) * 100,
                ddStats.percentile(95) * 100, ddStats.max() * 100);
        System.out.println();

        System.out.println("Terminal price histogram (0.5–99.5 pct range)");
        System.out.print(priceStats.histogram(20, 44));

        if (opts.containsKey("export")) {
            exportPaths(sim, s0, days, opts.get("export"));
        }
    }

    
    private static void exportPaths(Simulator sim, double s0, int days, String file) {
        int n = 20;
        double[][] sample = sim.samplePaths(s0, days, n);
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(file)))) {
            StringBuilder header = new StringBuilder("day");
            for (int p = 0; p < n; p++) header.append(",path").append(p + 1);
            out.println(header);
            for (int d = 0; d <= days; d++) {
                StringBuilder row = new StringBuilder(String.valueOf(d));
                for (int p = 0; p < n; p++) row.append(',').append(String.format("%.4f", sample[p][d]));
                out.println(row);
            }
            System.out.printf("%nExported %d sample paths to %s%n", n, file);
        } catch (IOException e) {
            System.err.println("Export failed: " + e.getMessage());
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + args[i]);
            }
            String key = args[i].substring(2);
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for --" + key);
            }
            opts.put(key, args[++i]);
        }
        return opts;
    }
    
    private static double getD(Map<String, String> opts, String key, double def) {
        String v = opts.get(key);
        return v == null ? def : Double.parseDouble(v);
    }
}
