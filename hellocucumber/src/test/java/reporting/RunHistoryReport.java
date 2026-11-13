package reporting;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dataProviders.ConfigFileReader;
import io.cucumber.htmlformatter.MessagesToHtmlWriter;
import io.cucumber.messages.ndjson.Serializer;
import io.cucumber.messages.types.Envelope;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCaseFinished;
import managers.FileReaderManager;

/**
 * Cucumber plugin that keeps a numbered copy of every run's HTML report, so earlier results aren't lost
 * when the next run overwrites {@code target/cucumber-reports/imdb.html}. Registered in RunImdbTests, so it
 * covers Maven runs and IntelliJ runs of RunImdbTests, but not right-click runs of a single .feature file.
 * <p>
 * Each run is written to {@code <historyDir>/run-NNNN_yyyy-MM-dd_HH-mm[_filtered|_dry-run].html} with
 * Cucumber's public HTML report library (the same report as imdb.html, failure screenshots included) and
 * listed in {@code <historyDir>/index.html} with its passed, failed and skipped scenarios. The newest
 * {@code historyKeep} full and filtered runs and, counted separately, the newest {@code historyKeepDryRuns}
 * dry runs are kept, so dry runs never push real runs out; the numbers keep counting up. {@code historyDir}
 * (default {@code test-history}, git-ignored), {@code historyKeep} (default 50) and {@code historyKeepDryRuns}
 * (default 10; 0 keeps all) come from configuration.properties and can be overridden with {@code -D}.
 * <p>
 * Problems writing the history are printed as warnings and never fail the test run. An invalid
 * {@code historyKeep} or {@code historyKeepDryRuns} fails at start, like any other configuration error. A run
 * that is killed before it finishes (Ctrl-C), or whose history couldn't be saved, has no runs.tsv line; the
 * next index lists its report as incomplete. Two runs at the same time can each lose the other's runs.tsv
 * line the same way (there is no lock; the suite runs one browser at a time).
 */
public class RunHistoryReport implements ConcurrentEventListener {
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm", Locale.ROOT);
    private static final DateTimeFormatter SHOWN_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
    /** run-0007_2026-11-13_12-30.html, run-0008_2026-11-13_12-31_filtered.html, ... */
    private static final Pattern REPORT_FILE =
            Pattern.compile("run-(\\d{1,9})_(\\d{4}-\\d{2}-\\d{2})_(\\d{2})-(\\d{2})(?:_(filtered|dry-run))?\\.html");
    private static final String RUNS_FILE = "runs.tsv";
    private static final String INDEX_FILE = "index.html";

    private final Path dir;
    private final int keep;
    private final int keepDryRuns;
    private final String runType;
    private final LocalDateTime started = LocalDateTime.now();
    private Path reportFile;
    private int runNumber;
    private OutputStream out;
    private MessagesToHtmlWriter html;
    private int passed;
    private int failed;
    private int skipped;

    public RunHistoryReport() {
        ConfigFileReader config = FileReaderManager.getInstance().getConfigReader();
        this.dir = config.getHistoryDir();
        this.keep = config.getHistoryKeep();   // invalid values fail the run here, like other config errors
        this.keepDryRuns = config.getHistoryKeepDryRuns();
        this.runType = runType();
        try {
            Files.createDirectories(dir);
            createReportFile();
            out = Files.newOutputStream(reportFile);
            Serializer serializer = new Serializer();
            this.html = MessagesToHtmlWriter.builder(serializer::writeValue).build(out);
        } catch (IOException | RuntimeException e) {
            warn("run history disabled for this run: " + e);
            closeQuietly(out);
            deleteQuietly(reportFile);
            this.html = null;
        }
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        if (html == null) return;
        publisher.registerHandlerFor(TestCaseFinished.class, this::countScenario);
        publisher.registerHandlerFor(Envelope.class, this::writeMessage);
    }

    /**
     * "dry-run", "filtered" (scenarios selected with -D: Cucumber filters, or Surefire's groups, which see
     * Cucumber tags) or "full". The @manual filter in junit-platform.properties is not a system property, so
     * it doesn't count.
     */
    private static String runType() {
        if (Boolean.parseBoolean(System.getProperty("cucumber.execution.dry-run"))) return "dry-run";
        for (String key : List.of("cucumber.filter.name", "cucumber.filter.tags", "cucumber.features",
                "groups", "excludedGroups")) {
            String value = System.getProperty(key);
            if (value != null && !value.isBlank()) return "filtered";
        }
        return "full";
    }

    /** Takes the next free run number; creating the file atomically means two runs can't share one. */
    private void createReportFile() throws IOException {
        int next = highestRunNumber() + 1;
        String suffix = runType.equals("full") ? "" : "_" + runType;
        while (true) {
            Path candidate = dir.resolve(String.format(Locale.ROOT, "run-%04d_%s%s.html", next, FILE_STAMP.format(started), suffix));
            try {
                Files.createFile(candidate);
                this.runNumber = next;
                this.reportFile = candidate;
                return;
            } catch (FileAlreadyExistsException e) {
                next++;
            }
        }
    }

    /** The highest run number in the report files or the run list, so numbers never repeat after pruning. */
    private int highestRunNumber() throws IOException {
        int highest = 0;
        for (Path report : reportFiles()) highest = Math.max(highest, numberOf(report));
        for (RunRecord run : readRuns()) highest = Math.max(highest, run.number());
        return highest;
    }

    private void countScenario(TestCaseFinished event) {
        Status status = event.getResult().getStatus();
        if (status == Status.PASSED) passed++;
        else if (status == Status.SKIPPED) skipped++;
        else failed++;   // FAILED, UNDEFINED, PENDING, AMBIGUOUS
    }

    /** Writes each message to the report; the last one (testRunFinished) completes it and the history. */
    private void writeMessage(Envelope envelope) {
        if (html == null) return;
        try {
            html.write(envelope);
            if (envelope.getTestRunFinished().isPresent()) {
                html.close();
                html = null;
                finishRun();
            }
        } catch (IOException | RuntimeException e) {
            warn("could not write " + reportFile + ": " + e);
            try {
                html.close();
            } catch (IOException | RuntimeException ignored) {
                // Already reporting a failure; the run itself carries on.
            }
            closeQuietly(out);   // the writer may be broken, so close the file itself as well
            html = null;
        }
    }

    private void finishRun() {
        try {
            List<RunRecord> runs = readRuns();
            runs.add(new RunRecord(runNumber, SHOWN_STAMP.format(started), runType, passed, failed, skipped,
                    reportFile.getFileName().toString()));
            pruneOldReports();
            Set<String> present = reportFiles().stream().map(p -> p.getFileName().toString()).collect(Collectors.toSet());
            runs.removeIf(run -> !present.contains(run.file()));
            writeRuns(runs);
            writeIndex(runs, present);
            System.out.println("Run history: saved as " + reportFile.getFileName() + " in " + dir
                    + " (" + passed + " passed, " + failed + " failed, " + skipped + " skipped)");
        } catch (IOException | RuntimeException e) {
            warn("could not update the run history in " + dir + ": " + e);
        }
    }

    /**
     * Keeps the newest {@code keep} full and filtered reports and, counted separately, the newest
     * {@code keepDryRuns} dry-run reports (0 keeps all). The current run's report is never removed.
     */
    private void pruneOldReports() throws IOException {
        List<Path> reports = reportFiles();
        pruneGroup(reports.stream().filter(p -> !isDryRun(p)).collect(Collectors.toList()), keep);
        pruneGroup(reports.stream().filter(RunHistoryReport::isDryRun).collect(Collectors.toList()), keepDryRuns);
    }

    private void pruneGroup(List<Path> group, int limit) throws IOException {
        if (limit <= 0 || group.size() <= limit) return;
        List<Path> oldestFirst = group.stream()
                .filter(p -> !p.equals(reportFile))
                .sorted(Comparator.comparingInt(RunHistoryReport::numberOf))
                .collect(Collectors.toList());
        int excess = group.size() - limit;
        for (int i = 0; i < excess && i < oldestFirst.size(); i++) {
            Files.deleteIfExists(oldestFirst.get(i));
        }
    }

    private List<Path> reportFiles() throws IOException {
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> REPORT_FILE.matcher(p.getFileName().toString()).matches())
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private static int numberOf(Path report) {
        Matcher m = REPORT_FILE.matcher(report.getFileName().toString());
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;   // at most 9 digits, so it always fits
    }

    private static boolean isDryRun(Path report) {
        return report.getFileName().toString().endsWith("_dry-run.html");
    }

    private List<RunRecord> readRuns() throws IOException {
        Path file = dir.resolve(RUNS_FILE);
        List<RunRecord> runs = new ArrayList<>();
        if (!Files.exists(file)) return runs;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] f = line.split("\t", -1);
            if (f.length != 7 || !REPORT_FILE.matcher(f[6]).matches()) continue;   // skip a damaged line
            try {
                runs.add(new RunRecord(Integer.parseInt(f[0]), f[1], f[2], Integer.parseInt(f[3]),
                        Integer.parseInt(f[4]), Integer.parseInt(f[5]), f[6]));
            } catch (NumberFormatException e) {
                // Skip a damaged line rather than lose the whole history.
            }
        }
        return runs;
    }

    private void writeRuns(List<RunRecord> runs) throws IOException {
        List<String> lines = runs.stream()
                .map(r -> String.join("\t", String.valueOf(r.number()), r.started(), r.type(), String.valueOf(r.passed()),
                        String.valueOf(r.failed()), String.valueOf(r.skipped()), r.file()))
                .collect(Collectors.toList());
        Files.write(dir.resolve(RUNS_FILE), lines, StandardCharsets.UTF_8);
    }

    private void writeIndex(List<RunRecord> runs, Set<String> present) throws IOException {
        List<Row> rows = new ArrayList<>();
        for (RunRecord r : runs) {
            rows.add(new Row(r.number(), r.file(), r.started(), r.type(),
                    String.valueOf(r.passed()), String.valueOf(r.failed()), String.valueOf(r.skipped()), r.failed() > 0));
        }
        // Reports without a runs.tsv line: runs that were stopped before they finished.
        Set<String> recorded = runs.stream().map(RunRecord::file).collect(Collectors.toSet());
        for (String file : present) {
            if (recorded.contains(file)) continue;
            Matcher m = REPORT_FILE.matcher(file);
            if (!m.matches()) continue;
            String type = (m.group(5) == null ? "full" : m.group(5)) + ", incomplete";
            rows.add(new Row(Integer.parseInt(m.group(1)), file, m.group(2) + " " + m.group(3) + ":" + m.group(4), type,
                    "–", "–", "–", true));
        }
        rows.sort(Comparator.comparingInt(Row::number).reversed());

        StringBuilder table = new StringBuilder();
        for (Row r : rows) {
            table.append("<tr class=\"").append(r.problem() ? "fail" : "pass").append("\">")
                    .append("<td><a href=\"").append(escape(r.file())).append("\">Run ").append(r.number()).append("</a></td>")
                    .append("<td>").append(escape(r.started())).append("</td>")
                    .append("<td>").append(escape(r.type())).append("</td>")
                    .append("<td>").append(r.passed()).append("</td>")
                    .append("<td>").append(r.failed()).append("</td>")
                    .append("<td>").append(r.skipped()).append("</td></tr>\n");
        }
        String page = "<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">\n"
                + "<title>IMDb test run history</title>\n<style>\n"
                + "body{font-family:system-ui,sans-serif;margin:2rem;color:#222;max-width:60rem}\n"
                + "table{border-collapse:collapse}th,td{padding:.4rem .9rem;border-bottom:1px solid #ddd;text-align:left}\n"
                + "th{background:#f3f3f3}tr.fail td:nth-child(5){color:#b00020;font-weight:bold}\n"
                + "tr.pass td:nth-child(5){color:#2e7d32}\n</style></head><body>\n"
                + "<h1>IMDb test run history</h1>\n"
                + "<p>Newest first. "
                + keptText()
                + "</p>\n<p>Counts are scenarios that ran. Scenarios left out by a tag or name filter, such as the"
                + " <code>@manual</code> ones, aren't counted; Skipped means a scenario that ran with its steps skipped."
                + " <code>filtered</code> runs cover only the scenarios selected with <code>-D</code>."
                + " <code>dry-run</code> runs open no browser: there, Passed means every step has a definition and"
                + " Failed counts scenarios with undefined or ambiguous steps."
                + " <code>incomplete</code> runs were stopped before they finished, or their history couldn't be"
                + " saved (see that run's console warning).</p>\n"
                + "<table>\n<tr><th>Run</th><th>Started</th><th>Type</th><th>Passed</th><th>Failed</th><th>Skipped</th></tr>\n"
                + table + "</table>\n</body></html>\n";
        Files.write(dir.resolve(INDEX_FILE), page.getBytes(StandardCharsets.UTF_8));
    }

    private String keptText() {
        String real = keep > 0 ? "the newest " + keep + " full and filtered runs" : "all full and filtered runs";
        String dry = keepDryRuns > 0 ? "the newest " + keepDryRuns + " dry runs" : "all dry runs";
        return "Kept: " + real + " and, counted separately, " + dry + ".";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void closeQuietly(OutputStream out) {
        if (out == null) return;
        try {
            out.close();
        } catch (IOException ignored) {
            // Nothing more to do; the warning has already been printed.
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // A leftover empty file is pruned like any other report.
        }
    }

    private static void warn(String message) {
        System.out.println(">>> Run history: " + message);
    }

    /** One line of runs.tsv. */
    private record RunRecord(int number, String started, String type, int passed, int failed, int skipped, String file) {
    }

    /** One row of index.html. */
    private record Row(int number, String file, String started, String type,
                       String passed, String failed, String skipped, boolean problem) {
    }
}
