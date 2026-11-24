import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Grasshopper Optimisation Algorithm, diadaptasi dari Saremi, Mirjalili & Lewis (2017)
 * untuk penjadwalan task ke VM. Fitness = skor gabungan F1/F2/F3 (lihat Fitness.java).
 * Saklar Alo.SEED_MCT: false = populasi awal murni acak, true = satu belalang awal diisi solusi MCT (hibrida).
 */
public class Goa {
    static long SEED = 42;
    static boolean TRACE = false;

    static void assign(List<Cloudlet> cl, List<Vm> vms, double[][] readyByPe, double[] vmBusy) {
        final int POP = Alo.POP, ITER = Alo.ITER;   // disamakan dengan ALO
        final double F = 0.5, L = 1.5, C_MAX = 1.0, C_MIN = 0.00001;
        int n = cl.size(), m = vms.size();
        final double LB = 0, UB = m;
        Random rnd = new Random(SEED);

        double[] seed = Alo.mctSeed(cl, vms);
        double[] mctObj = Fitness.objectives(Alo.toMap(seed, m), cl, vms);
        Fitness.Ref ref = new Fitness.Ref(mctObj);
        double mctScore = Fitness.refScore(mctObj, ref);

        double[][] x = new double[POP][n];
        double[] target = null;
        double targetFit = Double.MAX_VALUE;
        for (int p = 0; p < POP; p++) {
            for (int d = 0; d < n; d++) x[p][d] = LB + rnd.nextDouble() * (UB - LB);
            double f = Fitness.eval(Alo.toMap(x[p], m), cl, vms, ref);
            if (f < targetFit) { targetFit = f; target = x[p].clone(); }
        }
        if (Alo.SEED_MCT) { // [SEED MCT] individu 0 = solusi MCT (hibrida)
            x[0] = seed;
            double f0 = Fitness.eval(Alo.toMap(x[0], m), cl, vms, ref);
            if (f0 < targetFit) { targetFit = f0; target = x[0].clone(); }
        }

        for (int t = 0; t < ITER; t++) {
            double c = C_MAX - t * (C_MAX - C_MIN) / ITER;
            double[][] nx = new double[POP][n];

            for (int i = 0; i < POP; i++) {
                // Jarak dihitung per dimensi, mengikuti kode referensi penulis (MATLAB), bukan jarak Euclidean
                // seluruh dimensi: dengan ratusan dimensi, jarak Euclidean membuat langkah mendekati nol.
                // Jarak dipetakan ke 2 + (d mod 2) sebelum masuk fungsi s(.), seperti di kode referensi.
                for (int j = 0; j < POP; j++) {
                    if (j == i) continue;
                    for (int d = 0; d < n; d++) {
                        double df = x[j][d] - x[i][d], dist = Math.abs(df);
                        double r = 2.0 + (dist % 2.0);
                        double s = F * Math.exp(-r / L) - Math.exp(-r);
                        nx[i][d] += c * (UB - LB) / 2 * s * df / (dist + 1e-12);
                    }
                }
                for (int d = 0; d < n; d++) nx[i][d] = Alo.clamp(c * nx[i][d] + target[d], m);
            }

            x = nx;
            for (int p = 0; p < POP; p++) {
                double f = Fitness.eval(Alo.toMap(x[p], m), cl, vms, ref);
                if (f < targetFit) { targetFit = f; target = x[p].clone(); }
            }
            int it = t + 1;
            if (TRACE && (it == 1 || it % 10 == 0))
                System.out.printf(Locale.US, "  [GOA] iterasi %3d/%d   skor terbaik = %.4f   (skor MCT = %.4f)%n", it, ITER, targetFit, mctScore);
        }
        Alo.apply(Alo.toMap(target, m), cl, vms, readyByPe, vmBusy);
    }
}