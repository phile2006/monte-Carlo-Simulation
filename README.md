# Monte Carlo Stock Simulator (Java)

Simulates stock price paths under Geometric Brownian Motion or Merton
jump-diffusion and reports the terminal price distribution, horizon returns,
VaR/CVaR, path drawdowns, and an ASCII histogram.

Requires Java 22+ (uses the multi-file source launcher — no build step).


## Run

```sh
cd monteCarloSimulation
java MonteCarloSim.java                          # defaults: GBM, S0=100, mu=8%, sigma=20%, 1yr, 100k paths
java MonteCarloSim.java --sigma 0.35 --days 63   # 3-month horizon, 35% vol
java MonteCarloSim.java --model jump --lambda 6 --jump-mean -0.04
java MonteCarloSim.java --target 120             # adds P(terminal >= 120)
java MonteCarloSim.java --export paths.csv       # writes 20 sample paths as CSV
```

## Options

| Flag | Default | Meaning |
|---|---|---|
| `--s0` | 100 | starting price |
| `--mu` | 0.08 | annualized drift (decimal) |
| `--sigma` | 0.20 | annualized volatility (decimal) |
| `--days` | 252 | horizon in trading days |
| `--paths` | 100000 | number of simulated paths |
| `--seed` | 42 | RNG seed (results are reproducible per seed) |
| `--model` | gbm | `gbm` or `jump` |
| `--lambda` | 4 | *(jump)* expected jumps per year |
| `--jump-mean` | -0.03 | *(jump)* mean log-jump size |
| `--jump-std` | 0.05 | *(jump)* stdev of log-jump size |
| `--target` | — | report P(terminal ≥ target) |
| `--export` | — | CSV file for 20 sample paths |

## Notes

- Paths run in parallel across cores; each path gets its own splittable RNG
  stream, so a fixed seed gives identical results regardless of thread count.
- Jump-diffusion drift is compensated by `lambda * (E[e^J] - 1)`, so `--mu` is
  the total expected return including jumps for both models.
- VaR is reported as the return at the 5th/1st percentile; CVaR as the mean of
  that tail.
- Sanity check: with defaults, simulated mean/median terminal prices match the
  analytic GBM values (`S0*e^{mu T}` ≈ 108.33, `S0*e^{(mu-sigma²/2)T}` ≈ 106.18)
  to within Monte Carlo error.

## Files

- `MonteCarloSim.java` — CLI entry point and report
- `Simulator.java` — parallel path engine (terminal price + max drawdown per path)
- `PriceModel.java` / `GbmModel.java` / `JumpDiffusionModel.java` — models
- `Stats.java` — percentiles, VaR/CVaR tail mean, histogram
