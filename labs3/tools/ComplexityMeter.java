import com.sun.source.tree.*;
import com.sun.source.util.*;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * ComplexityMeter — аналізатор складності методів Java (Лабораторна робота №3).
 *
 * Обчислює для КОЖНОГО методу:
 *   - Cyclomatic Complexity (правило SonarSource S1541: 1 + if/for/while/do/case/catch/?:/&&/||);
 *   - Cognitive Complexity  (специфікація SonarSource, правило S3776): інкременти за
 *     керуючі конструкції + штраф за вкладеність; послідовності &&/|| рахуються як одна
 *     послідовність; лямбди збільшують рівень вкладеності.
 *
 * Запуск (JDK 21+, без збірки і без залежностей):
 *     java tools/ComplexityMeter.java src/main/java
 *     java tools/ComplexityMeter.java src/main/java --only LoadTest
 *     java tools/ComplexityMeter.java src/main/java --fail-over     (quality gate: код завершення 1,
 *                                                                    якщо Cognitive > 15 або Cyclomatic > 10)
 *
 * Той самий інструмент і та сама конфігурація правил використовуються і ДО, і ПІСЛЯ
 * рефакторингу, тому порівняння коректне. Для перехресної перевірки результати
 * можна порівняти зі SonarQube for IDE (правило java:S3776).
 */
public class ComplexityMeter {

    static final int COGNITIVE_LIMIT = 15;
    static final int CYCLOMATIC_LIMIT = 10;

    record Row(String owner, String method, int line, int lines, int cyc, int cog) {
        boolean over() { return cog > COGNITIVE_LIMIT || cyc > CYCLOMATIC_LIMIT; }
    }

    public static void main(String[] args) throws Exception {
        List<Path> roots = new ArrayList<>();
        boolean failOver = false;
        String only = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--fail-over" -> failOver = true;
                case "--only" -> only = args[++i];
                default -> roots.add(Paths.get(args[i]));
            }
        }
        if (roots.isEmpty()) {
            System.err.println("Usage: java tools/ComplexityMeter.java <file|dir>... [--only Name] [--fail-over]");
            System.exit(2);
        }

        List<Path> files = new ArrayList<>();
        for (Path r : roots) {
            if (Files.isDirectory(r)) {
                try (Stream<Path> s = Files.walk(r)) {
                    s.filter(p -> p.toString().endsWith(".java")).sorted().forEach(files::add);
                }
            } else {
                files.add(r);
            }
        }

        List<Row> rows = analyze(files);
        if (only != null) {
            String f = only;
            rows = rows.stream().filter(r -> (r.owner + "." + r.method).contains(f)).toList();
        }
        print(rows);

        boolean anyOver = rows.stream().anyMatch(Row::over);
        if (failOver && anyOver) {
            System.out.println("QUALITY GATE: FAIL (є методи з Cognitive > " + COGNITIVE_LIMIT
                    + " або Cyclomatic > " + CYCLOMATIC_LIMIT + ")");
            System.exit(1);
        }
        System.out.println("QUALITY GATE: " + (anyOver ? "WARN (поріг перевищено)" : "PASS"));
    }

    // ------------------------------------------------------------------ аналіз

    static List<Row> analyze(List<Path> files) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<Row> rows = new ArrayList<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, fm, null,
                    List.of("-proc:none"), null, fm.getJavaFileObjectsFromPaths(files));
            Trees trees = Trees.instance(task);
            SourcePositions sp = trees.getSourcePositions();
            for (CompilationUnitTree unit : task.parse()) {
                new TreeScanner<Void, Deque<String>>() {
                    @Override
                    public Void visitClass(ClassTree node, Deque<String> owners) {
                        owners.addLast(node.getSimpleName().toString());
                        try {
                            return super.visitClass(node, owners);
                        } finally {
                            owners.removeLast();
                        }
                    }

                    @Override
                    public Void visitMethod(MethodTree m, Deque<String> owners) {
                        if (m.getBody() != null) {
                            LineMap lm = unit.getLineMap();
                            long start = sp.getStartPosition(unit, m);
                            long end = sp.getEndPosition(unit, m);
                            int l1 = (int) lm.getLineNumber(start);
                            int l2 = (int) lm.getLineNumber(end);
                            Cyclomatic cyc = new Cyclomatic();
                            cyc.scan(m.getBody(), null);
                            Cognitive cog = new Cognitive();
                            cog.scan(m.getBody(), null);
                            String name = m.getName().contentEquals("<init>")
                                    ? owners.peekLast() : m.getName().toString();
                            rows.add(new Row(String.join(".", owners), name, l1, l2 - l1 + 1,
                                    cyc.count, cog.score));
                        }
                        return super.visitMethod(m, owners);
                    }
                }.scan(unit, new ArrayDeque<>());
            }
        }
        return rows;
    }

    static void print(List<Row> rows) {
        System.out.printf("%-48s %5s %6s %6s %5s%n", "Method", "Line", "Lines", "Cyclo", "Cogn");
        System.out.println("-".repeat(76));
        for (Row r : rows) {
            System.out.printf("%-48s %5d %6d %6d %5d %s%n",
                    r.owner + "." + r.method + "()", r.line, r.lines, r.cyc, r.cog,
                    r.over() ? "  <-- перевищено" : "");
        }
        System.out.println("-".repeat(76));
        int maxCog = rows.stream().mapToInt(Row::cog).max().orElse(0);
        int maxCyc = rows.stream().mapToInt(Row::cyc).max().orElse(0);
        int sumCog = rows.stream().mapToInt(Row::cog).sum();
        int sumCyc = rows.stream().mapToInt(Row::cyc).sum();
        System.out.printf("Methods: %d | max Cognitive: %d | max Cyclomatic: %d | sum Cognitive: %d | sum Cyclomatic: %d%n",
                rows.size(), maxCog, maxCyc, sumCog, sumCyc);
        System.out.printf("Thresholds: Cognitive <= %d, Cyclomatic <= %d%n", COGNITIVE_LIMIT, CYCLOMATIC_LIMIT);
    }

    // ------------------------------------------------------- Cyclomatic (S1541)

    static boolean isLogical(Tree.Kind k) {
        return k == Tree.Kind.CONDITIONAL_AND || k == Tree.Kind.CONDITIONAL_OR;
    }

    static class Cyclomatic extends TreeScanner<Void, Void> {
        int count = 1;

        @Override public Void visitIf(IfTree n, Void p) { count++; return super.visitIf(n, p); }
        @Override public Void visitForLoop(ForLoopTree n, Void p) { count++; return super.visitForLoop(n, p); }
        @Override public Void visitEnhancedForLoop(EnhancedForLoopTree n, Void p) { count++; return super.visitEnhancedForLoop(n, p); }
        @Override public Void visitWhileLoop(WhileLoopTree n, Void p) { count++; return super.visitWhileLoop(n, p); }
        @Override public Void visitDoWhileLoop(DoWhileLoopTree n, Void p) { count++; return super.visitDoWhileLoop(n, p); }
        @Override public Void visitCatch(CatchTree n, Void p) { count++; return super.visitCatch(n, p); }
        @Override public Void visitConditionalExpression(ConditionalExpressionTree n, Void p) { count++; return super.visitConditionalExpression(n, p); }

        @Override
        public Void visitCase(CaseTree n, Void p) {
            if (!n.toString().stripLeading().startsWith("default")) count++;
            return super.visitCase(n, p);
        }

        @Override
        public Void visitBinary(BinaryTree n, Void p) {
            if (isLogical(n.getKind())) count++;
            return super.visitBinary(n, p);
        }

        @Override public Void visitClass(ClassTree n, Void p) { return null; } // вкладені класи — окремо
    }

    // ------------------------------------------------------ Cognitive (S3776)

    static class Cognitive extends TreeScanner<Void, Void> {
        int score = 0;
        int nesting = 0;

        private void inc(boolean withNesting) {
            score += 1 + (withNesting ? nesting : 0);
        }

        private void nested(Tree body) {
            nesting++;
            scan(body, null);
            nesting--;
        }

        @Override public Void visitIf(IfTree n, Void p) { ifStatement(n, false); return null; }

        private void ifStatement(IfTree n, boolean elseIf) {
            if (elseIf) score += 1; else inc(true);
            scan(n.getCondition(), null);
            nested(n.getThenStatement());
            StatementTree otherwise = n.getElseStatement();
            if (otherwise instanceof IfTree e) {
                ifStatement(e, true);
            } else if (otherwise != null) {
                score += 1;                       // else: +1 без штрафу за вкладеність
                nested(otherwise);
            }
        }

        @Override
        public Void visitForLoop(ForLoopTree n, Void p) {
            inc(true);
            scan(n.getInitializer(), null);
            scan(n.getCondition(), null);
            scan(n.getUpdate(), null);
            nested(n.getStatement());
            return null;
        }

        @Override
        public Void visitEnhancedForLoop(EnhancedForLoopTree n, Void p) {
            inc(true);
            scan(n.getExpression(), null);
            nested(n.getStatement());
            return null;
        }

        @Override
        public Void visitWhileLoop(WhileLoopTree n, Void p) {
            inc(true);
            scan(n.getCondition(), null);
            nested(n.getStatement());
            return null;
        }

        @Override
        public Void visitDoWhileLoop(DoWhileLoopTree n, Void p) {
            inc(true);
            nested(n.getStatement());
            scan(n.getCondition(), null);
            return null;
        }

        @Override
        public Void visitCatch(CatchTree n, Void p) {
            inc(true);
            nested(n.getBlock());
            return null;
        }

        @Override
        public Void visitSwitch(SwitchTree n, Void p) {
            inc(true);
            scan(n.getExpression(), null);
            nesting++;
            scan(n.getCases(), null);
            nesting--;
            return null;
        }

        @Override
        public Void visitSwitchExpression(SwitchExpressionTree n, Void p) {
            inc(true);
            scan(n.getExpression(), null);
            nesting++;
            scan(n.getCases(), null);
            nesting--;
            return null;
        }

        @Override
        public Void visitConditionalExpression(ConditionalExpressionTree n, Void p) {
            inc(true);
            scan(n.getCondition(), null);
            nesting++;
            scan(n.getTrueExpression(), null);
            scan(n.getFalseExpression(), null);
            nesting--;
            return null;
        }

        @Override
        public Void visitLambdaExpression(LambdaExpressionTree n, Void p) {
            nested(n.getBody());                  // лямбда: лише підвищує вкладеність
            return null;
        }

        @Override
        public Void visitBreak(BreakTree n, Void p) {
            if (n.getLabel() != null) score += 1;
            return null;
        }

        @Override
        public Void visitContinue(ContinueTree n, Void p) {
            if (n.getLabel() != null) score += 1;
            return null;
        }

        @Override
        public Void visitBinary(BinaryTree n, Void p) {
            if (!isLogical(n.getKind())) return super.visitBinary(n, p);
            List<Tree.Kind> ops = new ArrayList<>();
            List<ExpressionTree> operands = new ArrayList<>();
            flatten(n, ops, operands);
            int sequences = 1;                     // нова послідовність при зміні оператора
            for (int i = 1; i < ops.size(); i++) {
                if (ops.get(i) != ops.get(i - 1)) sequences++;
            }
            score += sequences;
            for (ExpressionTree o : operands) scan(o, null);
            return null;
        }

        private void flatten(ExpressionTree e, List<Tree.Kind> ops, List<ExpressionTree> operands) {
            if (e instanceof BinaryTree b && isLogical(b.getKind())) {
                flatten(b.getLeftOperand(), ops, operands);
                ops.add(b.getKind());
                operands.add(b.getRightOperand());
            } else {
                operands.add(e);
            }
        }

        @Override public Void visitClass(ClassTree n, Void p) { return null; }
    }
}
