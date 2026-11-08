import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ЛР8: локальний аналізатор об'єктних метрик (CK + Lorenz-Kidd + MOOD) за байткодом.
 *
 * Навіщо: SonarQube Community НЕ віддає через api/measures метрик coupling_between_objects, lcom4, DIT та MOOD
 * (їх немає в api/metrics/search). Цей інструмент рахує їх сам з target/classes та віддає JSON у стилі Sonar API.
 *
 * Запуск (JDK 21, з кореня проєкту, після mvn compile):
 *   java tools/CkMetrics.java target/classes lab8 --json docs/lab8/metrics/now.json
 *
 * Визначення:
 *   WMC   = Σ цикломатичної складності методів (1 + розгалуження + case + catch)
 *   CBO   = к-сть ІНШИХ типів проєкту, на які клас посилається (efferent coupling; supertypes не рахуються — їх міряє DIT)
 *   RFC   = власні методи + унікальні викликані чужі методи
 *   LCOM  = HS (Henderson-Sellers, 0..1, ціль ≤0.2); додатково LCOM4 (компоненти за спільними полями) та LCOM-CK (P-Q)
 *   DIT   = довжина ланцюжка суперкласів до Object включно (клас напряму від Object => 1)
 *   PIM   = к-сть публічних методів (власні + успадковані, без Object)
 *   NOO / NOA = перевизначені / додані методи (перевизначення методів ПРОЄКТНИХ суперкласів)
 *   MHF/AHF = частка прихованих (private|protected) методів/полів; COF = Σ зв'язків / (N²-N)
 */
public class CkMetrics {

    static final class Row {
        String name; boolean iface, record, enm;
        int wmc, cbo, rfc, dit, pim, nom, noo, noa, fanIn, lcom4, lcomCk;
        double lcomHs;
        Set<String> coupled = new TreeSet<>();
    }

    static final class Mth {
        String name; boolean ctor, synthetic, isStatic;
        int cc = 1;
        Set<String> fields = new HashSet<>();
        Set<String> calls = new HashSet<>();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: java tools/CkMetrics.java <classesDir> <packagePrefix> [--json out.json]");
            System.exit(2);
        }
        Path dir = Path.of(args[0]).toAbsolutePath();
        String prefix = args[1];
        Path jsonOut = null;
        for (int i = 2; i < args.length - 1; i++) if (args[i].equals("--json")) jsonOut = Path.of(args[i + 1]);

        List<String> names = new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".class")).toList()) {
                String n = dir.relativize(p).toString().replace('\\', '/').replace('/', '.');
                n = n.substring(0, n.length() - 6);
                if (n.startsWith(prefix + ".") && !n.matches(".*\\$\\d+.*") && !n.endsWith("package-info")) names.add(n);
            }
        }
        Collections.sort(names);
        if (names.isEmpty()) { System.err.println("No classes under " + dir + " for prefix " + prefix); System.exit(1); }

        URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, CkMetrics.class.getClassLoader());
        Map<String, Class<?>> classes = new LinkedHashMap<>();
        for (String n : names) classes.put(n, Class.forName(n, false, loader));

        Map<String, List<String>> blocks = javap(dir, names);
        Map<String, Row> rows = new LinkedHashMap<>();
        for (String n : names) rows.put(n, analyse(n, classes.get(n), blocks.getOrDefault(n, List.of()), classes, prefix));
        for (Row r : rows.values()) for (String c : r.coupled) rows.get(c).fanIn++;

        // ---------- MOOD ----------
        int methods = 0, hiddenM = 0, fields = 0, hiddenF = 0, couplings = 0;
        for (Class<?> c : classes.values()) {
            if (c.isInterface()) continue;
            for (Method m : c.getDeclaredMethods()) {
                if (m.isSynthetic() || m.getName().startsWith("lambda$")) continue;
                methods++;
                if (Modifier.isPrivate(m.getModifiers()) || Modifier.isProtected(m.getModifiers())) hiddenM++;
            }
            for (var f : c.getDeclaredFields()) {
                if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) continue;
                fields++;
                if (Modifier.isPrivate(f.getModifiers()) || Modifier.isProtected(f.getModifiers())) hiddenF++;
            }
        }
        for (Row r : rows.values()) couplings += r.coupled.size();
        int n = rows.size();
        double mhf = methods == 0 ? 0 : 100.0 * hiddenM / methods;
        double ahf = fields == 0 ? 0 : 100.0 * hiddenF / fields;
        double cof = n < 2 ? 0 : 100.0 * couplings / ((double) n * n - n);

        // ---------- output ----------
        List<Row> sorted = new ArrayList<>(rows.values());
        sorted.sort(Comparator.comparingInt((Row r) -> -r.cbo).thenComparing(r -> r.name));
        System.out.printf("%-44s %4s %4s %4s %7s %5s %7s %4s %4s %4s %4s %4s%n",
                "class", "WMC", "CBO", "RFC", "LCOM-HS", "LCOM4", "LCOM-CK", "DIT", "PIM", "NOM", "NOO", "NOA");
        for (Row r : sorted) {
            System.out.printf(Locale.ROOT, "%-44s %4d %4d %4d %7.2f %5d %7d %4d %4d %4d %4d %4d%s%n",
                    r.name, r.wmc, r.cbo, r.rfc, r.lcomHs, r.lcom4, r.lcomCk, r.dit, r.pim, r.nom, r.noo, r.noa,
                    r.iface ? "  (interface)" : r.record ? "  (record)" : "");
        }
        System.out.printf(Locale.ROOT, "%nMOOD: MHF=%.1f%%  AHF=%.1f%%  COF=%.2f%%  (types=%d, couplings=%d)%n", mhf, ahf, cof, n, couplings);

        if (jsonOut != null) {
            if (jsonOut.getParent() != null) Files.createDirectories(jsonOut.getParent());
            StringBuilder sb = new StringBuilder("{\n  \"scope\": \"" + prefix + "\",\n");
            sb.append(String.format(Locale.ROOT, "  \"mood\": {\"mhf\": \"%.1f\", \"ahf\": \"%.1f\", \"cof\": \"%.2f\", \"types\": \"%d\"},\n", mhf, ahf, cof, n));
            sb.append("  \"components\": [\n");
            for (int i = 0; i < sorted.size(); i++) {
                Row r = sorted.get(i);
                sb.append("    {\"key\": \"").append(r.name).append("\", \"path\": \"src/main/java/")
                  .append(r.name.replace('.', '/')).append(".java\", \"measures\": [");
                sb.append(m("class_complexity", r.wmc)).append(',').append(m("coupling_between_objects", r.cbo)).append(',')
                  .append(m("rfc", r.rfc)).append(',').append(String.format(Locale.ROOT, "{\"metric\":\"lcom_hs\",\"value\":\"%.2f\"},", r.lcomHs))
                  .append(m("lcom4", r.lcom4)).append(',').append(m("lcom_ck", r.lcomCk)).append(',')
                  .append(m("depth_in_tree", r.dit)).append(',').append(m("pim", r.pim)).append(',')
                  .append(m("overridden_methods", r.noo)).append(',').append(m("added_methods", r.noa)).append("]}");
                sb.append(i + 1 < sorted.size() ? ",\n" : "\n");
            }
            sb.append("  ]\n}\n");
            Files.writeString(jsonOut, sb);
            System.out.println("JSON written to " + jsonOut);
        }
    }

    static String m(String k, int v) { return "{\"metric\":\"" + k + "\",\"value\":\"" + v + "\"}"; }

    // ------------------------------------------------------------------ javap
    static Map<String, List<String>> javap(Path dir, List<String> names) throws IOException, InterruptedException {
        Path jh = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "javap.exe" : "javap");
        List<String> cmd = new ArrayList<>(List.of(Files.exists(jh) ? jh.toString() : "javap", "-c", "-p", "-cp", dir.toString()));
        cmd.addAll(names);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        List<String> lines = new ArrayList<>(new String(p.getInputStream().readAllBytes()).lines().toList());
        p.waitFor();
        Map<String, List<String>> out = new LinkedHashMap<>();
        Pattern hdr = Pattern.compile("\\b(?:class|interface|enum)\\s+([\\w.$]+)");
        List<String> cur = null;
        for (String l : lines) {
            if (!l.isEmpty() && !Character.isWhitespace(l.charAt(0)) && l.endsWith("{")) {
                Matcher mm = hdr.matcher(l);
                if (mm.find()) { cur = new ArrayList<>(); out.put(mm.group(1), cur); }
            }
            if (cur != null) cur.add(l);
        }
        return out;
    }

    // --------------------------------------------------------------- analysis
    static Row analyse(String name, Class<?> c, List<String> block, Map<String, Class<?>> all, String prefix) {
        Row r = new Row();
        r.name = name; r.iface = c.isInterface(); r.record = c.isRecord(); r.enm = c.isEnum();

        // --- bytecode part
        List<Mth> ms = new ArrayList<>();
        Pattern mh = Pattern.compile("^  ([^\\s(][^(]*)\\((.*)\\)(?: throws [^;]*)?;$");
        Pattern ins = Pattern.compile("^\\s{4,}\\d+: (\\w+)(.*)$");
        Pattern fld = Pattern.compile("(getfield|putfield)\\s+#\\d+\\s+//\\s+Field\\s+([^:\\s]+):");
        Pattern mth = Pattern.compile("//\\s+(?:Interface)?Method\\s+(\\S+?):");
        Pattern caseLine = Pattern.compile("^\\s{7,}(?:-?\\d+|default): \\d+$");
        Pattern exc = Pattern.compile("^\\s{7,}\\d+\\s+\\d+\\s+\\d+\\s+(\\S.*)$");
        Mth cur = null;
        for (String l : block) {
            Matcher h = mh.matcher(l);
            if (h.matches()) {
                cur = new Mth();
                String[] t = h.group(1).trim().split("\\s+");
                cur.name = t[t.length - 1];
                cur.isStatic = Arrays.asList(t).contains("static");
                cur.ctor = cur.name.equals(name);
                cur.synthetic = cur.name.startsWith("lambda$") || cur.name.startsWith("access$");
                ms.add(cur);
                continue;
            }
            if (cur == null) continue;
            Matcher i = ins.matcher(l);
            if (i.matches()) {
                String op = i.group(1);
                if (op.startsWith("if") || op.equals("ifnull") || op.equals("ifnonnull")) cur.cc++;
                Matcher f = fld.matcher(l);
                if (f.find() && !f.group(2).contains("/") && !f.group(2).contains(".")) cur.fields.add(f.group(2));
                Matcher c2 = mth.matcher(l);
                if (c2.find()) {
                    String t = c2.group(1).replace("\"", "");
                    cur.calls.add(t.contains("/") || t.contains(".") ? t : name + "." + t);
                }
            } else if (caseLine.matcher(l).matches() && !l.contains("default")) {
                cur.cc++;
            } else {
                Matcher e = exc.matcher(l);
                if (e.matches() && !e.group(1).startsWith("any")) cur.cc++;
            }
        }
        for (Mth m : ms) r.wmc += m.cc;

        // RFC
        Set<String> own = new HashSet<>();
        for (Mth m : ms) own.add(name + "." + m.name);
        Set<String> remote = new HashSet<>();
        for (Mth m : ms) for (String call : m.calls) if (!own.contains(call)) remote.add(call);
        r.rfc = ms.size() + remote.size();

        // CBO (efferent): project types mentioned anywhere in the class
        Set<String> supers = new HashSet<>();
        for (Class<?> s = c.getSuperclass(); s != null; s = s.getSuperclass()) supers.add(s.getName());
        for (Class<?> s : allInterfaces(c)) supers.add(s.getName());
        Matcher pm = Pattern.compile("(?<![\\w$])" + Pattern.quote(prefix) + "[./][\\w$./]*").matcher(String.join("\n", block));
        while (pm.find()) {
            StringBuilder fq = new StringBuilder();
            for (String seg : pm.group().split("[./]")) {
                if (seg.isEmpty()) break;
                if (fq.length() > 0) fq.append('.');
                fq.append(seg);
                if (Character.isUpperCase(seg.charAt(0))) break;
            }
            String t = fq.toString();
            if (all.containsKey(t) && !t.equals(name) && !supers.contains(t) && !t.startsWith(name + "$")) r.coupled.add(t);
        }
        r.cbo = r.coupled.size();

        // --- reflection part
        int d = 0;
        for (Class<?> s = c.getSuperclass(); s != null; s = s.getSuperclass()) d++;
        r.dit = d;
        Set<String> pub = new HashSet<>();
        for (Method m : c.getMethods())
            if (m.getDeclaringClass() != Object.class && !m.isBridge() && !m.isSynthetic()) pub.add(sig(m));
        r.pim = pub.size();
        int nom = 0, noo = 0, declaredPublic = 0;
        for (Method m : c.getDeclaredMethods()) {
            if (m.isSynthetic() || m.isBridge() || m.getName().startsWith("lambda$")) continue;
            nom++;
            if (Modifier.isPublic(m.getModifiers())) declaredPublic++;
            if (!Modifier.isPrivate(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()) && overridesProject(c, m, all)) noo++;
        }
        r.nom = nom; r.noo = noo; r.noa = Math.max(0, declaredPublic - noo);

        // --- LCOM (instance fields vs instance methods)
        if (!r.iface && !r.record && !r.enm) {
            List<String> fs = new ArrayList<>();
            for (var f : c.getDeclaredFields())
                if (!f.isSynthetic() && !Modifier.isStatic(f.getModifiers())) fs.add(f.getName());
            List<Set<String>> use = new ArrayList<>();
            for (Mth m : ms) if (!m.ctor && !m.synthetic && !m.isStatic && !m.name.contains("{")) {
                Set<String> u = new HashSet<>(m.fields); u.retainAll(fs); use.add(u);
            }
            int mm = use.size(), a = fs.size();
            if (mm > 1 && a > 0) {
                int sum = 0;
                for (String f : fs) for (Set<String> u : use) if (u.contains(f)) sum++;
                r.lcomHs = Math.max(0, (mm - (double) sum / a) / (mm - 1));
            }
            int p = 0, q = 0;
            int[] parent = new int[mm];
            for (int i = 0; i < mm; i++) parent[i] = i;
            for (int i = 0; i < mm; i++) for (int j = i + 1; j < mm; j++) {
                Set<String> x = new HashSet<>(use.get(i)); x.retainAll(use.get(j));
                if (x.isEmpty()) p++; else { q++; parent[find(parent, i)] = find(parent, j); }
            }
            r.lcomCk = Math.max(p - q, 0);
            Set<Integer> comps = new HashSet<>();
            for (int i = 0; i < mm; i++) comps.add(find(parent, i));
            r.lcom4 = mm == 0 ? 0 : comps.size();
        }
        return r;
    }

    static int find(int[] p, int x) { while (p[x] != x) x = p[x] = p[p[x]]; return x; }

    static String sig(Method m) { return m.getName() + Arrays.toString(m.getParameterTypes()); }

    static Set<Class<?>> allInterfaces(Class<?> c) {
        Set<Class<?>> out = new LinkedHashSet<>();
        for (Class<?> k = c; k != null; k = k.getSuperclass())
            for (Class<?> i : k.getInterfaces()) { out.add(i); out.addAll(allInterfaces(i)); }
        return out;
    }

    static boolean overridesProject(Class<?> c, Method m, Map<String, Class<?>> all) {
        for (Class<?> s = c.getSuperclass(); s != null && all.containsKey(s.getName()); s = s.getSuperclass()) {
            try {
                Method sm = s.getDeclaredMethod(m.getName(), m.getParameterTypes());
                if (!Modifier.isPrivate(sm.getModifiers()) && !Modifier.isStatic(sm.getModifiers())) return true;
            } catch (NoSuchMethodException ignored) { }
        }
        return false;
    }
}
