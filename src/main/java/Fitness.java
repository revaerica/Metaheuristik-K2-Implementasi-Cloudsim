import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.List;

/**
 * Fungsi tujuan gabungan sesuai draft design:
 *   F1 = makespan, F2 = total cost (sum T_j/3600 * tarif_j), F3 = Degree of Imbalance.
 *   skor = w1*F1/F1_ref + w2*F2/F2_ref + w3*F3/F3_ref, bobot 0,45 / 0,45 / 0,10.
 * Normalisasi memakai solusi MCT sebagai acuan, jadi skor acuan MCT dicetak apa adanya (1,0 hanya bila DI MCT >= 0,05); skor di bawah skor MCT berarti lebih baik.
 */
public final class Fitness {
    static double W1 = 0.45, W2 = 0.45, W3 = 0.10;

    /** Nilai acuan (dari solusi MCT) untuk normalisasi. */
    static final class Ref {
        final double f1, f2, f3;
        Ref(double[] o) { f1 = o[0]; f2 = o[1]; f3 = Math.max(o[2], 0.05); }
    }

    /** Hitung {F1, F2, F3} untuk satu solusi (indeks VM per task). Pola sama dengan mctAssign di BatchRunner. */
    static double[] objectives(int[] map, List<Cloudlet> cl, List<Vm> vms) {
        int m = vms.size();
        double[][] ready = new double[m][];
        for (int j = 0; j < m; j++) ready[j] = new double[(int) vms.get(j).getNumberOfPes()];
        double[] busy = new double[m];
        double makespan = 0;
        for (int i = 0; i < map.length; i++) {
            int j = map[i], p = BatchRunner.earliestPe(ready[j]);
            double dur = cl.get(i).getLength() / vms.get(j).getMips();
            ready[j][p] += dur;
            busy[j] += dur;
            makespan = Math.max(makespan, ready[j][p]);
        }
        double cost = 0, tMax = 0, tMin = Double.MAX_VALUE, tSum = 0;
        for (int j = 0; j < m; j++) {
            cost += busy[j] / 3600.0 * BatchRunner.VM_COST_PER_HOUR[j];
            tMax = Math.max(tMax, busy[j]);
            tMin = Math.min(tMin, busy[j]);
            tSum += busy[j];
        }
        double tAvg = tSum / m;
        double di = tAvg > 0 ? (tMax - tMin) / tAvg : 0;
        return new double[]{makespan, cost, di};
    }

    static double score(double[] o, Ref r) {
        return W1 * o[0] / r.f1 + W2 * o[1] / r.f2 + W3 * o[2] / r.f3;
    }

    /** Skor solusi acuan (MCT) itu sendiri. Bernilai 1,0 hanya jika DI MCT >= 0,05 (batas bawah penyebut F3). */
    static double refScore(double[] mctObjectives, Ref r) { return score(mctObjectives, r); }

    static double eval(int[] map, List<Cloudlet> cl, List<Vm> vms, Ref r) {
        return score(objectives(map, cl, vms), r);
    }
}