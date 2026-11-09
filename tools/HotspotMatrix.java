import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * HotspotMatrix (Лабораторна робота №7) - синергетична матриця критичних зон.
 *
 * Зводить в одну таблицю:
 *   - статичну складність файлів (CSV від ComplexityMeter --csv), та
 *   - динаміку розробки: Churn Rate з git log (вивід "uniq -c": "  15 path/File.java").
 *
 * Запуск (JDK 21, без залежностей):
 *   java tools/HotspotMatrix.java --complexity complexity.csv --churn churn.txt --out out-dir
 * Опції:
 *   --cc-threshold N      межа "висока складність" (за замовчуванням 10, умова: CC > N)
 *   --churn-threshold N   межа "висока мінливість" (умова: коміти > N; за замовчуванням - медіана)
 *   --metric max|sum      складність файлу = max CC серед методів (за замовчуванням, буквально "CC>10")
 *                         або сума CC усіх методів (WMC - сигнал "God Object" з багатьох дрібних методів)
 */
public class HotspotMatrix {

    record FileStat(String file, int methods, int ccSum, int ccMax, int commits, String category) {}

    static final String HIGH = "High Risk (Hotspot)";
    static final String SLEEPING = "Sleeping Danger";
    static final String ACTIVE = "Active Development";
    static final String STABLE = "Stable Code";
    static final List<String> ORDER = List.of(HIGH, SLEEPING, ACTIVE, STABLE);

    public static void main(String[] args) throws IOException {
        Path complexity = null, churn = null, outDir = Paths.get(".");
        int ccThreshold = 10;
        Integer churnThreshold = null;
        boolean useMax = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--complexity" -> complexity = Paths.get(args[++i]);
                case "--churn" -> churn = Paths.get(args[++i]);
                case "--out" -> outDir = Paths.get(args[++i]);
                case "--cc-threshold" -> ccThreshold = Integer.parseInt(args[++i]);
                case "--churn-threshold" -> churnThreshold = Integer.parseInt(args[++i]);
                case "--metric" -> useMax = !"sum".equals(args[++i]);
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        if (complexity == null || churn == null) {
            System.err.println("Usage: java tools/HotspotMatrix.java --complexity c.csv --churn churn.txt [--out dir]");
            System.exit(2);
        }

        Map<String, int[]> cc = readComplexity(complexity);          // file -> {methods, sum, max}
        Map<String, Integer> commits = readChurn(churn);

        List<Integer> churnValues = new ArrayList<>();
        for (String f : cc.keySet()) churnValues.add(commits.getOrDefault(f, 0));
        int churnLimit = churnThreshold != null ? churnThreshold : median(churnValues);

        List<FileStat> stats = new ArrayList<>();
        for (Map.Entry<String, int[]> e : cc.entrySet()) {
            int[] v = e.getValue();
            int c = commits.getOrDefault(e.getKey(), 0);
            int complexityValue = useMax ? v[2] : v[1];
            boolean highCc = complexityValue > ccThreshold;
            boolean highChurn = c > churnLimit;
            String cat = highCc ? (highChurn ? HIGH : SLEEPING) : (highChurn ? ACTIVE : STABLE);
            stats.add(new FileStat(e.getKey(), v[0], v[1], v[2], c, cat));
        }
        stats.sort(Comparator.<FileStat>comparingInt(s -> ORDER.indexOf(s.category()))
                .thenComparing(Comparator.comparingInt(FileStat::commits).reversed())
                .thenComparing(Comparator.comparingInt(FileStat::ccSum).reversed()));

        StringBuilder md = new StringBuilder();
        md.append("### Hotspots matrix\n\n");
        md.append("Складність файлу: ").append(useMax ? "max CC методу" : "сума CC методів")
          .append(" (висока, якщо > ").append(ccThreshold).append("); мінливість: коміти за період")
          .append(" (висока, якщо > ").append(churnLimit).append(").\n\n");
        md.append("| Файл | Методів | CC (сума) | CC (max) | Коміти | Категорія ризику |\n");
        md.append("|---|---:|---:|---:|---:|---|\n");
        StringBuilder csv = new StringBuilder("file,methods,cc_sum,cc_max,commits,category\n");
        for (FileStat s : stats) {
            md.append("| `").append(s.file()).append("` | ").append(s.methods()).append(" | ").append(s.ccSum())
              .append(" | ").append(s.ccMax()).append(" | ").append(s.commits()).append(" | ")
              .append(s.category()).append(" |\n");
            csv.append(s.file()).append(',').append(s.methods()).append(',').append(s.ccSum()).append(',')
               .append(s.ccMax()).append(',').append(s.commits()).append(',').append(s.category()).append('\n');
        }
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve("hotspots.md"), md.toString());
        Files.writeString(outDir.resolve("hotspots.csv"), csv.toString());
        System.out.println(md);
        System.out.println("Written: " + outDir.resolve("hotspots.md") + ", " + outDir.resolve("hotspots.csv"));
    }

    static Map<String, int[]> readComplexity(Path p) throws IOException {
        Map<String, int[]> map = new TreeMap<>();
        List<String> lines = readText(p).lines().toList();
        for (int i = 1; i < lines.size(); i++) {                    // перший рядок - заголовок
            String[] c = lines.get(i).split(",");
            if (c.length < 7) continue;
            int cyc = Integer.parseInt(c[5].trim());
            int[] v = map.computeIfAbsent(c[0].trim().replace('\\', '/'), k -> new int[3]);
            v[0]++;
            v[1] += cyc;
            v[2] = Math.max(v[2], cyc);
        }
        return map;
    }

    static Map<String, Integer> readChurn(Path p) throws IOException {
        Map<String, Integer> map = new HashMap<>();
        Pattern line = Pattern.compile("^\\s*(\\d+)\\s+(.+?)\\s*$");
        for (String l : readText(p).lines().toList()) {
            Matcher m = line.matcher(l);
            if (m.matches()) map.merge(m.group(2).replace('\\', '/'), Integer.parseInt(m.group(1)), Integer::sum);
        }
        return map;
    }

    /** Читає текст з урахуванням BOM (PowerShell 5.1 любить писати UTF-16 / UTF-8 з BOM). */
    static String readText(Path p) throws IOException {
        byte[] b = Files.readAllBytes(p);
        Charset cs = StandardCharsets.UTF_8;
        int off = 0;
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) { cs = StandardCharsets.UTF_16LE; off = 2; }
        else if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) { cs = StandardCharsets.UTF_16BE; off = 2; }
        else if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) { off = 3; }
        return new String(b, off, b.length - off, cs);
    }

    static int median(List<Integer> values) {
        if (values.isEmpty()) return 0;
        List<Integer> s = new ArrayList<>(values);
        Collections.sort(s);
        return s.get((s.size() - 1) / 2);
    }
}
