import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Demo interaktif penjadwalan task di CloudSim Plus (FCFS, Min-Min, MCT, ALO, GOA).
 * Semua pengaturan bisa diganti saat program berjalan, tanpa compile ulang.
 *
 * Interaktif :  DemoRunner
 * Sekali jalan:  DemoRunner [n] [file_dataset] [seed] [seedMct true/false]
 */
public class DemoRunner {
    static final String DIR = "SOKA_v2/Dataset-Sintetik/";
    static final String[] ORDER = {"FCFS", "MINMIN", "MCT", "ALO", "GOA"};

    static int n = 100;
    static String data = DIR + "synthetic_seed1.csv";
    static long seed = 1;
    static List<String> selected = new ArrayList<>(Arrays.asList(ORDER));

    public static void main(String[] args) {
        try { // matikan log INFO CloudSim supaya layar demo bersih
            org.cloudsimplus.util.Log.setLevel(ch.qos.logback.classic.Level.OFF);
        } catch (Throwable ignored) { }
        BatchRunner.validateInfrastructure();
        Alo.TRACE = true;
        Goa.TRACE = true;

        if (args.length > 0) {
            n = Integer.parseInt(args[0]);
            if (args.length > 1) data = args[1];
            if (args.length > 2) seed = Long.parseLong(args[2]);
            if (args.length > 3) Alo.SEED_MCT = Boolean.parseBoolean(args[3]);
            run();
            return;
        }

        help();
        show();
        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.print("\n> ");
            if (!in.hasNextLine()) break;
            String line = in.nextLine().trim();
            if (line.isEmpty()) { run(); continue; }
            String[] t = line.split("\\s+");
            String cmd = t[0].toLowerCase(Locale.ROOT);
            try {
                switch (cmd) {
                    case "run", "r" -> run();
                    case "n" -> { n = Integer.parseInt(t[1]); show(); }
                    case "data", "d" -> { data = t[1].matches("[1-3]") ? DIR + "synthetic_seed" + t[1] + ".csv" : t[1]; show(); }
                    case "algo", "a" -> { setAlgo(t[1]); show(); }
                    case "seed", "s" -> { seed = Long.parseLong(t[1]); show(); }
                    case "seedmct", "hibrida", "h" -> { Alo.SEED_MCT = t[1].equalsIgnoreCase("on") || t[1].equalsIgnoreCase("true"); show(); }
                    case "pop" -> { Alo.POP = Integer.parseInt(t[1]); show(); }
                    case "iter" -> { Alo.ITER = Integer.parseInt(t[1]); show(); }
                    case "w" -> { Fitness.W1 = Double.parseDouble(t[1]); Fitness.W2 = Double.parseDouble(t[2]); Fitness.W3 = Double.parseDouble(t[3]); show(); }
                    case "trace" -> { Alo.TRACE = Goa.TRACE = t[1].equalsIgnoreCase("on"); show(); }
                    case "show" -> show();
                    case "help", "?" -> help();
                    case "quit", "q", "exit" -> { return; }
                    default -> System.out.println("Perintah tidak dikenal. Ketik 'help'.");
                }
            } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
                System.out.println("Argumen kurang/salah. Ketik 'help'.");
            }
        }
    }

    static void setAlgo(String s) {
        if (s.equalsIgnoreCase("all")) { selected = new ArrayList<>(Arrays.asList(ORDER)); return; }
        List<String> sel = new ArrayList<>();
        for (String a : s.toUpperCase(Locale.ROOT).replace("-", "").split(",")) {
            if (!Arrays.asList(ORDER).contains(a)) { System.out.println("Algoritma tidak dikenal: " + a); return; }
            sel.add(a);
        }
        selected = sel;
    }

    static void help() {
        System.out.println("""

            PERINTAH (Enter kosong = jalankan):
              run | r              jalankan dengan pengaturan sekarang
              n 500                jumlah task
              data 2               dataset sintetik 1/2/3 (atau path file CSV)
              algo ALO,GOA         pilih algoritma (FCFS,MINMIN,MCT,ALO,GOA | all); MCT selalu ikut sebagai acuan
              seed 3               seed acak ALO/GOA (ganti -> hasil ALO/GOA berubah)
              seedmct on|off       off = ALO/GOA murni acak, on = satu individu awal diisi solusi MCT (hibrida)
              pop 30 / iter 100    populasi dan jumlah iterasi
              w 0.45 0.45 0.10     bobot fitness F1 makespan, F2 cost, F3 DI
              trace on|off         tampilkan/sembunyikan konvergensi
              show | help | quit""");
    }

    static void show() {
        System.out.printf(Locale.US,
                "%n[Pengaturan] n=%d | data=%s | algo=%s | seed=%d | seedMCT=%s | populasi=%d | iterasi=%d | bobot=%.2f/%.2f/%.2f%n",
                n, data.substring(data.lastIndexOf('/') + 1), String.join(",", selected), seed,
                Alo.SEED_MCT ? "ON (hibrida)" : "OFF (murni)", Alo.POP, Alo.ITER, Fitness.W1, Fitness.W2, Fitness.W3);
    }

    static void run() {
        show();
        List<String[]> rows = new ArrayList<>();
        for (String a : ORDER) {
            if (!selected.contains(a) && !a.equals("MCT")) continue;
            if (a.equals("ALO") || a.equals("GOA"))
                System.out.printf("%nMenjalankan %s (populasi %d, %d iterasi, awal: %s)...%n", a, Alo.POP, Alo.ITER,
                        Alo.SEED_MCT ? "satu individu = solusi MCT" : "murni acak");
            String row;
            try {
                row = BatchRunner.runOnce(a, n, data, seed);
            } catch (RuntimeException e) {
                System.out.println("Gagal menjalankan " + a + ": " + e.getMessage());
                return;
            }
            if (row == null) { System.out.println("Gagal membaca dataset: " + data); return; }
            rows.add(row.split(","));
        }

        // kolom: 0 algo, 4 makespan, 5 DI, 6 util, 7 throughput, 10 cost, 12 waktu algoritma (ms)
        String[] mct = rows.stream().filter(r -> r[0].equals("MCT")).findFirst().get();
        double mMk = Double.parseDouble(mct[4]), mCost = Double.parseDouble(mct[10]), mDi = Double.parseDouble(mct[5]);
        double maxMk = rows.stream().mapToDouble(r -> Double.parseDouble(r[4])).max().orElse(1);

        System.out.printf(Locale.US, "%n%-7s %11s %10s %7s %6s %10s %12s %10s  %s%n",
                "Algo", "Makespan(s)", "Cost(US$)", "DI", "Util", "Thruput", "Skor", "Waktu(ms)", "Makespan");
        for (String[] r : rows) {
            double mk = Double.parseDouble(r[4]), di = Double.parseDouble(r[5]), cost = Double.parseDouble(r[10]);
            double score = Fitness.W1 * mk / mMk + Fitness.W2 * cost / mCost + Fitness.W3 * di / Math.max(mDi, 0.05);
            int bar = (int) Math.round(30 * mk / maxMk);
            System.out.printf(Locale.US, "%-7s %11.2f %10.4f %7.3f %6.3f %10.4f %12.4f %10.1f  %s%n",
                    r[0], mk, cost, di, Double.parseDouble(r[6]), Double.parseDouble(r[7]), score,
                    Double.parseDouble(r[12]), "#".repeat(Math.max(bar, 1)));
        }
        System.out.println("Skor = bobot*F1/F1_MCT + bobot*F2/F2_MCT + bobot*F3/max(F3_MCT;0,05). Baris MCT = skor acuan (1,0 hanya bila DI MCT >= 0,05); makin kecil makin baik.");
    }
}