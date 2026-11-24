"""
visualize.py — Visualisasi hasil eksperimen SOKA-K2 Metaheuristik

Menghasilkan chart PNG dan summary CSV dari results_hibrida.csv dan results_murni.csv.

Penggunaan:
    python visualize.py                          # proses kedua file
    python visualize.py results_hibrida.csv      # proses satu file saja
"""

import csv
import sys
import os
from collections import defaultdict
import statistics as stats
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.ticker as mticker

# ---------------------------------------------------------------------------
# Konfigurasi
# ---------------------------------------------------------------------------

ALGOS  = ["MCT", "FCFS", "MINMIN", "ALO", "GOA"]
LABELS = {"MCT": "MCT", "FCFS": "FCFS", "MINMIN": "Min-Min", "ALO": "ALO", "GOA": "GOA"}
COLORS = {"MCT": "#1f77b4", "FCFS": "#d62728", "MINMIN": "#2ca02c", "ALO": "#ff7f0e", "GOA": "#9467bd"}
MARKERS = {"MCT": "o", "FCFS": "s", "MINMIN": "^", "ALO": "D", "GOA": "P"}

# Seed dataset sintetik (sesuaikan jika berbeda)
SEED_FILES = ["synthetic_seed1.csv", "synthetic_seed2.csv", "synthetic_seed3.csv"]
DATASET_DIR = os.path.join(os.path.dirname(__file__), "SOKA_v2", "Dataset-Sintetik")

MIN_N = 100
MAX_N = 1000

# ---------------------------------------------------------------------------
# Fungsi bantu
# ---------------------------------------------------------------------------

def load(path: str) -> dict:
    """Muat CSV hasil eksperimen ke dict data[algo][n] -> list of metric dicts."""
    data = {a: defaultdict(list) for a in ALGOS}
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            algo = row["algorithm"]
            if algo not in ALGOS:
                continue
            n = int(row["n_tasks"])
            if MIN_N <= n <= MAX_N:
                data[algo][n].append({
                    "makespan": float(row["makespan"]),
                    "di":       float(row["degree_of_imbalance"]),
                    "util":     float(row["utilization"]),
                    "tput":     float(row["throughput"]),
                    "cost":     float(row["total_cost"]),
                    "dataset_file":   row["dataset_file"],
                    "total_vm_mips":  float(row["total_vm_mips"]),
                })
    return data


def mean_series(data: dict, algo: str, key: str):
    ns = sorted(data[algo].keys())
    ys = [stats.mean(r[key] for r in data[algo][n]) for n in ns]
    return ns, ys


def std_series(data: dict, algo: str, key: str) -> list:
    ns = sorted(data[algo].keys())
    return [
        stats.stdev(r[key] for r in data[algo][n]) if len(data[algo][n]) > 1 else 0.0
        for n in ns
    ]


def load_seed_mi() -> dict:
    """Muat total MI per seed per n dari file dataset sintetik."""
    tot_mi: dict = {}
    for sf in SEED_FILES:
        path = os.path.join(DATASET_DIR, sf)
        if not os.path.exists(path):
            print(f"[WARN] Dataset sintetik tidak ditemukan: {path} — chart deviasi dilewati.")
            return {}
        with open(path, newline="", encoding="utf-8") as f:
            rd = csv.reader(f)
            next(rd)  # skip header
            mi_all = [int(r[1]) for r in rd]
        tot_mi[sf] = mi_all  # simpan list lengkap; iris saat dipakai
    return tot_mi


OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "visualization")

def fig_path(prefix: str, name: str) -> str:
    """Buat path output chart: visualization/<prefix>_<name>.png"""
    return os.path.join(OUT_DIR, f"{prefix}_{name}.png")


def save_fig(fig, path: str, dpi: int = 150):
    fig.savefig(path, dpi=dpi, bbox_inches="tight")
    plt.close(fig)
    print(f"  ✓  {os.path.basename(path)}")


# ---------------------------------------------------------------------------
# Chart: satu file
# ---------------------------------------------------------------------------

def plot_makespan(data: dict, prefix: str, title_suffix: str):
    fig, ax = plt.subplots(figsize=(9, 5.5))
    for a in ALGOS:
        ns, y = mean_series(data, a, "makespan")
        if not ns:
            continue
        stds = std_series(data, a, "makespan")
        ax.plot(ns, y, label=LABELS[a], color=COLORS[a],
                linewidth=1.8, marker=MARKERS[a], markersize=5)
        ax.fill_between(ns,
                         [m - s for m, s in zip(y, stds)],
                         [m + s for m, s in zip(y, stds)],
                         color=COLORS[a], alpha=0.12)
    ax.set_xlabel("Jumlah task (n)")
    ax.set_ylabel("Makespan rata-rata (detik)")
    ax.set_title(f"Makespan vs Ukuran Dataset — {title_suffix} ({MIN_N}–{MAX_N})")
    ax.legend()
    ax.grid(alpha=0.3)
    fig.tight_layout()
    save_fig(fig, fig_path(prefix, "makespan"))


def plot_makespan_deviation(data: dict, seed_mi: dict, prefix: str, title_suffix: str):
    if not seed_mi:
        return

    # Bangun dev[algo][n] = list of % deviation
    dev = {a: defaultdict(list) for a in ALGOS}
    for a in ALGOS:
        for n, records in data[a].items():
            for r in records:
                sf = r["dataset_file"]
                if sf not in seed_mi:
                    continue
                mi_list = seed_mi[sf]
                lb = sum(mi_list[:n]) / r["total_vm_mips"]
                if lb > 0:
                    dev[a][n].append((r["makespan"] - lb) / lb * 100)

    fig, ax = plt.subplots(figsize=(9, 5.5))
    for a in ALGOS:
        ns = sorted(dev[a].keys())
        if not ns:
            continue
        ys = [stats.mean(dev[a][n]) for n in ns]
        ax.plot(ns, ys, label=LABELS[a], color=COLORS[a],
                linewidth=1.8, marker=MARKERS[a], markersize=5)
    ax.axhline(0, color="gray", linestyle="--", linewidth=1)
    ax.set_xlabel("Jumlah task (n)")
    ax.set_ylabel("Deviasi makespan thd batas bawah (%)")
    ax.set_title(f"Deviasi Makespan thd Batas Bawah Teoritis — {title_suffix} ({MIN_N}–{MAX_N})")
    ax.legend()
    ax.grid(alpha=0.3)
    fig.tight_layout()
    save_fig(fig, fig_path(prefix, "makespan_dev"))


def plot_imbalance(data: dict, prefix: str, title_suffix: str):
    fig, ax = plt.subplots(figsize=(9, 5.5))
    for a in ALGOS:
        ns, y = mean_series(data, a, "di")
        if not ns:
            continue
        ax.plot(ns, y, label=LABELS[a], color=COLORS[a],
                linewidth=1.8, marker=MARKERS[a], markersize=5)
    ax.set_xlabel("Jumlah task (n)")
    ax.set_ylabel("Degree of Imbalance (rata-rata)")
    ax.set_title(f"Degree of Imbalance vs Ukuran Dataset — {title_suffix} ({MIN_N}–{MAX_N})")
    ax.legend()
    ax.grid(alpha=0.3)
    fig.tight_layout()
    save_fig(fig, fig_path(prefix, "imbalance"))


def plot_utilization(data: dict, prefix: str, title_suffix: str):
    fig, ax = plt.subplots(figsize=(9, 5.5))
    for a in ALGOS:
        ns, y = mean_series(data, a, "util")
        if not ns:
            continue
        ax.plot(ns, y, label=LABELS[a], color=COLORS[a],
                linewidth=1.8, marker=MARKERS[a], markersize=5)
    ax.set_xlabel("Jumlah task (n)")
    ax.set_ylabel("Utilization (rata-rata)")
    ax.set_title(f"Utilisasi VM vs Ukuran Dataset — {title_suffix} ({MIN_N}–{MAX_N})")
    ax.set_ylim(0, 1.05)
    ax.yaxis.set_major_formatter(mticker.PercentFormatter(xmax=1))
    ax.legend()
    ax.grid(alpha=0.3)
    fig.tight_layout()
    save_fig(fig, fig_path(prefix, "utilization"))


def plot_throughput(data: dict, prefix: str, title_suffix: str):
    fig, ax = plt.subplots(figsize=(9, 5.5))
    for a in ALGOS:
        ns, y = mean_series(data, a, "tput")
        if not ns:
            continue
        ax.plot(ns, y, label=LABELS[a], color=COLORS[a],
                linewidth=1.8, marker=MARKERS[a], markersize=5)
    ax.set_xlabel("Jumlah task (n)")
    ax.set_ylabel("Throughput rata-rata (task/s)")
    ax.set_title(f"Throughput vs Ukuran Dataset — {title_suffix} ({MIN_N}–{MAX_N})")
    ax.legend()
    ax.grid(alpha=0.3)
    fig.tight_layout()
    save_fig(fig, fig_path(prefix, "throughput"))


def plot_cost(data: dict, prefix: str, title_suffix: str):
    fig, ax = plt.subplots(figsize=(9, 5.5))
    for a in ALGOS:
        ns, y = mean_series(data, a, "cost")
        if not ns:
            continue
        ax.plot(ns, y, label=LABELS[a], color=COLORS[a],
                linewidth=1.8, marker=MARKERS[a], markersize=5)
    ax.set_xlabel("Jumlah task (n)")
    ax.set_ylabel("Total Cost rata-rata")
    ax.set_title(f"Total Cost vs Ukuran Dataset — {title_suffix} ({MIN_N}–{MAX_N})")
    ax.legend()
    ax.grid(alpha=0.3)
    fig.tight_layout()
    save_fig(fig, fig_path(prefix, "cost"))


# ---------------------------------------------------------------------------
# Chart: perbandingan hibrida vs murni (overlay, 1 metrik per algo)
# ---------------------------------------------------------------------------

def plot_comparison(data_h: dict, data_m: dict, metric: str,
                    ylabel: str, title_base: str, out_name: str):
    """
    Satu figure dengan subplot per algoritma, membandingkan hibrida vs murni.
    """
    n_algo = len(ALGOS)
    ncols = 3
    nrows = (n_algo + ncols - 1) // ncols
    fig, axes = plt.subplots(nrows, ncols, figsize=(5 * ncols, 4 * nrows), sharey=False)
    axes = axes.flatten()

    for idx, a in enumerate(ALGOS):
        ax = axes[idx]
        for lbl, dset, ls in [("Hibrida", data_h, "-"), ("Murni", data_m, "--")]:
            ns, y = mean_series(dset, a, metric)
            if ns:
                ax.plot(ns, y, label=lbl, linestyle=ls, color=COLORS[a],
                        linewidth=1.8, marker=MARKERS[a], markersize=4)
        ax.set_title(LABELS[a])
        ax.set_xlabel("n tasks")
        ax.set_ylabel(ylabel)
        ax.legend(fontsize=8)
        ax.grid(alpha=0.3)

    # Sembunyikan subplot kosong
    for idx in range(len(ALGOS), len(axes)):
        axes[idx].set_visible(False)

    fig.suptitle(f"{title_base} — Hibrida vs Murni ({MIN_N}–{MAX_N})", fontsize=13, y=1.01)
    fig.tight_layout()
    out = os.path.join(OUT_DIR, out_name)
    save_fig(fig, out)


# ---------------------------------------------------------------------------
# Summary CSV
# ---------------------------------------------------------------------------

def write_summary(data: dict, path: str):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow([
            "algorithm", "n_tasks",
            "makespan_mean", "makespan_std",
            "di_mean", "di_std",
            "util_mean",
            "throughput_mean",
            "cost_mean",
        ])
        for a in ALGOS:
            for n in sorted(data[a].keys()):
                rows = data[a][n]
                def m(key): return stats.mean(r[key] for r in rows)
                def s(key): return stats.stdev(r[key] for r in rows) if len(rows) > 1 else 0.0
                w.writerow([
                    a, n,
                    f"{m('makespan'):.2f}",  f"{s('makespan'):.2f}",
                    f"{m('di'):.4f}",        f"{s('di'):.4f}",
                    f"{m('util'):.4f}",
                    f"{m('tput'):.6f}",
                    f"{m('cost'):.4f}",
                ])
    print(f"  ✓  {os.path.basename(path)}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def process_single(csv_path: str, prefix: str, label: str, seed_mi: dict):
    """Proses satu file CSV dan hasilkan semua chart + summary."""
    print(f"\n{'='*55}")
    print(f" Mode: {label}  ({os.path.basename(csv_path)})")
    print(f"{'='*55}")
    data = load(csv_path)

    plot_makespan(data, prefix, label)
    plot_makespan_deviation(data, seed_mi, prefix, label)
    plot_imbalance(data, prefix, label)
    plot_utilization(data, prefix, label)
    plot_throughput(data, prefix, label)
    plot_cost(data, prefix, label)

    summary_path = os.path.join(OUT_DIR, f"summary_{prefix}.csv")
    write_summary(data, summary_path)
    return data


def main():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    os.makedirs(OUT_DIR, exist_ok=True)
    print(f"Output folder: {OUT_DIR}")

    # Tentukan file yang diproses
    if len(sys.argv) > 1:
        # Mode: satu file eksplisit
        path = sys.argv[1]
        prefix = os.path.splitext(os.path.basename(path))[0].replace("results_", "")
        os.makedirs(OUT_DIR, exist_ok=True)
        print(f"Output folder: {OUT_DIR}")
        seed_mi = load_seed_mi()
        process_single(path, prefix, prefix.capitalize(), seed_mi)
        print("\nSelesai.")
        return

    # Mode default: proses hibrida + murni + chart perbandingan
    path_h = os.path.join(base_dir, "results_hibrida.csv")
    path_m = os.path.join(base_dir, "results_murni.csv")

    seed_mi = load_seed_mi()

    data_h = process_single(path_h, "hibrida", "Hibrida", seed_mi)
    data_m = process_single(path_m, "murni",   "Murni",   seed_mi)

    # Chart perbandingan Hibrida vs Murni
    print(f"\n{'='*55}")
    print(" Chart perbandingan Hibrida vs Murni")
    print(f"{'='*55}")
    plot_comparison(data_h, data_m, "makespan", "Makespan (s)",
                    "Makespan",         "cmp_makespan.png")
    plot_comparison(data_h, data_m, "di",       "Degree of Imbalance",
                    "Degree of Imbalance", "cmp_imbalance.png")
    plot_comparison(data_h, data_m, "util",     "Utilization",
                    "Utilisasi VM",     "cmp_utilization.png")
    plot_comparison(data_h, data_m, "tput",     "Throughput (task/s)",
                    "Throughput",       "cmp_throughput.png")
    plot_comparison(data_h, data_m, "cost",     "Total Cost",
                    "Total Cost",       "cmp_cost.png")

    print("\nSelesai! File yang dihasilkan:")
    generated = [
        "hibrida_makespan.png", "hibrida_makespan_dev.png",
        "hibrida_imbalance.png", "hibrida_utilization.png",
        "hibrida_throughput.png", "hibrida_cost.png",
        "murni_makespan.png", "murni_makespan_dev.png",
        "murni_imbalance.png", "murni_utilization.png",
        "murni_throughput.png", "murni_cost.png",
        "cmp_makespan.png", "cmp_imbalance.png",
        "cmp_utilization.png", "cmp_throughput.png", "cmp_cost.png",
        "summary_hibrida.csv", "summary_murni.csv",
    ]
    for f in generated:
        print(f"  • {f}")


if __name__ == "__main__":
    main()
