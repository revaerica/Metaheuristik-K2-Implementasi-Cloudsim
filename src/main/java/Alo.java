import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Ant Lion Optimizer, diadaptasi dari Mirjalili (2015) untuk penjadwalan task ke VM:
 * random walk kumulatif, roulette wheel, elitisme, penyusutan rentang.
 * Fitness = skor gabungan F1/F2/F3 (lihat Fitness.java).
 * SEED_MCT = false -> populasi awal murni acak (metaheuristik murni); true -> satu antlion awal diisi solusi MCT (hibrida).
 */
public class Alo {
    static long SEED = 42;       // diubah BatchRunner/DemoRunner untuk beberapa pengulangan
    static int POP = 30, ITER = 100;   // dipakai bersama oleh ALO dan GOA; bisa diubah saat demo
    static boolean SEED_MCT = false;   // false = murni acak, true = satu individu awal = solusi MCT
    static boolean TRACE = false; // true = cetak konvergensi (dipakai DemoRunner)

    static void assign(List<Cloudlet> cl, List<Vm> vms, double[][] readyByPe, double[] vmBusy) {
        final int POP = Alo.POP, ITER = Alo.ITER;
        int n = cl.size(), m = vms.size();
        Random rnd = new Random(SEED);

        double[] seed = mctSeed(cl, vms);
        double[] mctObj = Fitness.objectives(toMap(seed, m), cl, vms);
        Fitness.Ref ref = new Fitness.Ref(mctObj);
        double mctScore = Fitness.refScore(mctObj, ref);

        double[][] lion = new double[POP][n];
        double[] fit = new double[POP];
        for (int p = 0; p < POP; p++) {
            for (int d = 0; d < n; d++) lion[p][d] = rnd.nextDouble() * m;
            fit[p] = Fitness.eval(toMap(lion[p], m), cl, vms, ref);
        }
        if (SEED_MCT) { // [SEED MCT] individu 0 = solusi MCT (hibrida)
            lion[0] = seed;
            fit[0] = Fitness.eval(toMap(lion[0], m), cl, vms, ref);
        }

        double[] elitePos = lion[argmin(fit)].clone();
        double eliteFit = fit[argmin(fit)];
        final double LB = 0, UB = m;

        for (int t = 1; t <= ITER; t++) {
            double[] w = new double[POP];
            double wSum = 0;
            for (int p = 0; p < POP; p++) { w[p] = 1.0 / fit[p]; wSum += w[p]; }
            double I = shrinkRatio(t, ITER);

            double[][] ant = new double[POP][n];
            double[] antFit = new double[POP];
            for (int p = 0; p < POP; p++) {
                int sel = roulette(w, wSum, rnd);
                for (int d = 0; d < n; d++) {
                    // ant = (RA + RE) / 2: random walk di sekitar antlion terpilih (roulette) dan elite
                    double ra = walk(lion[sel][d], LB, UB, I, t, ITER, rnd);
                    double re = walk(elitePos[d], LB, UB, I, t, ITER, rnd);
                    ant[p][d] = clamp(0.5 * (ra + re), m);
                }
                antFit[p] = Fitness.eval(toMap(ant[p], m), cl, vms, ref);
            }
            // gabung antlion + ant, simpan POP terbaik sebagai antlion baru
            double[][] pool = new double[2 * POP][];
            double[] poolFit = new double[2 * POP];
            for (int p = 0; p < POP; p++) { pool[p] = lion[p]; poolFit[p] = fit[p]; pool[POP + p] = ant[p]; poolFit[POP + p] = antFit[p]; }
            Integer[] idx = new Integer[2 * POP];
            for (int i = 0; i < idx.length; i++) idx[i] = i;
            java.util.Arrays.sort(idx, (x, y) -> Double.compare(poolFit[x], poolFit[y]));
            for (int p = 0; p < POP; p++) { lion[p] = pool[idx[p]]; fit[p] = poolFit[idx[p]]; }
            if (fit[0] < eliteFit) { eliteFit = fit[0]; elitePos = lion[0].clone(); }
            if (TRACE && (t == 1 || t % 10 == 0))
                System.out.printf(Locale.US, "  [ALO] iterasi %3d/%d   skor terbaik = %.4f   (skor MCT = %.4f)%n", t, ITER, eliteFit, mctScore);
        }
        apply(toMap(elitePos, m), cl, vms, readyByPe, vmBusy);
    }

    /** Rasio penyusutan I = 10^w * t/T, w bertingkat 2..6 (Mirjalili 2015); I = 1 di awal. */
    private static double shrinkRatio(int t, int T) {
        int w = 0;
        if (t > 0.95 * T) w = 6; else if (t > 0.9 * T) w = 5; else if (t > 0.75 * T) w = 4;
        else if (t > 0.5 * T) w = 3; else if (t > 0.1 * T) w = 2;
        return w == 0 ? 1.0 : Math.pow(10, w) * t / T;
    }

    /** Posisi ke-t dari random walk kumulatif (+1/-1, T langkah), dinormalisasi ke rentang di sekitar antlion. */
    private static double walk(double antlion, double lb, double ub, double I, int t, int T, Random r) {
        double lo = lb / I, hi = ub / I;
        lo = r.nextDouble() < 0.5 ? lo + antlion : -lo + antlion;
        hi = r.nextDouble() >= 0.5 ? hi + antlion : -hi + antlion;
        int s = 0, min = 0, max = 0, atT = 0;
        long bits = 0;
        for (int k = 1; k <= T; k++) {
            if (((k - 1) & 63) == 0) bits = r.nextLong();
            s += ((bits >>> ((k - 1) & 63)) & 1L) == 1L ? 1 : -1;
            if (s < min) min = s;
            if (s > max) max = s;
            if (k == t) atT = s;
        }
        return (atT - min) * (hi - lo) / (double) (max - min) + lo;
    }

    /** Solusi MCT dalam encoding kontinu (indeks VM + 0.5). Dipakai ALO & GOA sebagai individu awal. */
    static double[] mctSeed(List<Cloudlet> cl, List<Vm> vms) {
        int m = vms.size();
        double[][] ready = new double[m][];
        for (int j = 0; j < m; j++) ready[j] = new double[(int) vms.get(j).getNumberOfPes()];
        double[] x = new double[cl.size()];
        for (int i = 0; i < cl.size(); i++) {
            int bestJ = 0; double bestCt = Double.MAX_VALUE;
            for (int j = 0; j < m; j++) {
                int p = BatchRunner.earliestPe(ready[j]);
                double ct = ready[j][p] + cl.get(i).getLength() / vms.get(j).getMips();
                if (ct < bestCt) { bestCt = ct; bestJ = j; }
            }
            int p = BatchRunner.earliestPe(ready[bestJ]);
            ready[bestJ][p] += cl.get(i).getLength() / vms.get(bestJ).getMips();
            x[i] = bestJ + 0.5;
        }
        return x;
    }

    static void apply(int[] map, List<Cloudlet> cl, List<Vm> vms, double[][] readyByPe, double[] vmBusy) {
        for (int i = 0; i < map.length; i++) {
            int j = map[i], p = BatchRunner.earliestPe(readyByPe[j]);
            double dur = cl.get(i).getLength() / vms.get(j).getMips();
            cl.get(i).setVm(vms.get(j));
            readyByPe[j][p] += dur;
            vmBusy[j] += dur;
        }
    }

    static int[] toMap(double[] x, int m) {
        int[] map = new int[x.length];
        for (int i = 0; i < x.length; i++) map[i] = (int) Math.max(0, Math.min(m - 1, Math.floor(x[i])));
        return map;
    }

    static double clamp(double v, int m) { return Math.max(0, Math.min(m - 1e-9, v)); }

    private static int argmin(double[] a) {
        int b = 0;
        for (int i = 1; i < a.length; i++) if (a[i] < a[b]) b = i;
        return b;
    }

    private static int roulette(double[] w, double sum, Random rnd) {
        double r = rnd.nextDouble() * sum, acc = 0;
        for (int i = 0; i < w.length; i++) { acc += w[i]; if (acc >= r) return i; }
        return w.length - 1;
    }
}