package com.resumetailor.pdf;

import com.resumetailor.common.ServiceUnavailableException;
import com.resumetailor.config.PdfProperties;
import com.resumetailor.resume.ResumeFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Compiles resumes to PDF inside a throwaway container.
 *
 * <p>The sandboxing is the point, not an optimisation. LaTeX is a full programming
 * language that can read and write files, and the markup being compiled was written by
 * an LLM moments earlier. Each flag below closes a specific door:
 *
 * <ul>
 *   <li>{@code --network none} -- a document cannot phone home or fetch anything.</li>
 *   <li>{@code -no-shell-escape} -- {@code \write18} cannot execute shell commands.</li>
 *   <li>{@code --memory} / {@code --cpus} -- a runaway macro expansion cannot exhaust the host.</li>
 *   <li>{@code --security-opt no-new-privileges} -- no privilege escalation inside the container.</li>
 *   <li>a per-run temp directory as the only mount -- nothing else on disk is reachable.</li>
 *   <li>a wall-clock timeout -- an infinite loop in TeX cannot hang the server.</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "resume-tailor.pdf", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DockerTexCompiler implements PdfCompiler {

    private static final String SOURCE_STEM = "main";
    private static final int LOG_TAIL_CHARS = 8000;

    private final PdfProperties properties;

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public PdfCompileResult compile(String source, ResumeFormat format) {
        Path workDir = createWorkDir();
        try {
            String extension = format.fileExtension();
            Path sourceFile = workDir.resolve(SOURCE_STEM + "." + extension);
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

            String containerName = "resume-tailor-tex-" + UUID.randomUUID();
            ProcessOutput output = run(buildCommand(workDir, containerName, source, format), containerName);

            // TeX's own main.log is the useful record: it holds the full error context and the
            // "runsystem(...)...disabled." lines, none of which reach the terminal output.
            Path texLog = workDir.resolve(SOURCE_STEM + ".log");
            String compileLog = Files.exists(texLog)
                    ? Files.readString(texLog, StandardCharsets.ISO_8859_1)
                    : output.text();

            Path pdf = workDir.resolve(SOURCE_STEM + ".pdf");
            String error = firstTexError(compileLog);
            if (error == null) {
                error = firstTexError(output.text());
            }
            // A PDF existing is not success. In nonstopmode TeX pushes past errors -- an
            // undefined macro is simply dropped -- and still writes output, so content can
            // silently vanish from the resume the user is about to send. Any "!" error fails.
            if (!Files.exists(pdf) || error != null) {
                throw new PdfCompilationException(
                        "The document did not compile cleanly. "
                                + (error != null ? error : "See the compiler log for details."),
                        tail(compileLog));
            }
            return new PdfCompileResult(Files.readAllBytes(pdf), tail(compileLog));
        } catch (IOException ex) {
            throw new ServiceUnavailableException("Could not write the file to compile: " + ex.getMessage(), ex);
        } finally {
            deleteQuietly(workDir);
        }
    }

    private List<String> buildCommand(Path workDir, String containerName, String source, ResumeFormat format) {
        List<String> command = new ArrayList<>(List.of(
                properties.dockerBinary(), "run", "--rm",
                "--name", containerName,
                "--network", "none",
                "--security-opt", "no-new-privileges",
                "--memory", properties.memoryLimit(),
                "--cpus", properties.cpuLimit(),
                "-v", workDir.toAbsolutePath() + ":/work",
                "-w", "/work",
                properties.image(),
                "sh", "-c", innerCommand(source, format)));
        return command;
    }

    private String innerCommand(String source, ResumeFormat format) {
        if (format == ResumeFormat.MARKDOWN) {
            return "pandoc " + SOURCE_STEM + ".md -o " + SOURCE_STEM + ".pdf"
                    + " --pdf-engine=xelatex -V geometry:margin=1in";
        }
        String engine = latexEngineFor(source);
        // Two passes so cross-references and page totals settle. nonstopmode stops TeX
        // waiting for input on an error; compile() then treats any logged error as failure.
        String pass = engine + " -interaction=nonstopmode -no-shell-escape " + SOURCE_STEM + ".tex";
        return pass + "; " + pass + "; true";
    }

    /**
     * Templates using fontspec or system fonts only build under xelatex; everything else
     * is faster and more predictable under pdflatex.
     */
    static String latexEngineFor(String source) {
        String lower = source.toLowerCase();
        boolean needsUnicodeEngine = lower.contains("fontspec")
                || lower.contains("\\setmainfont")
                || lower.contains("unicode-math")
                || lower.contains("polyglossia");
        return needsUnicodeEngine ? "xelatex" : "pdflatex";
    }

    private ProcessOutput run(List<String> command, String containerName) {
        log.debug("Compiling with: {}", String.join(" ", command));
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException ex) {
            throw new ServiceUnavailableException(
                    "Could not run '" + properties.dockerBinary() + "'. Is Docker Desktop running? "
                            + "Start it, then build the image once with: "
                            + "docker build -t " + properties.image() + " docker/tex", ex);
        }

        StringBuilder text = new StringBuilder();
        try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
            // Drain the pipe on a separate thread: a full OS pipe buffer would otherwise
            // block the compiler before we ever reach the timeout check.
            Thread pump = new Thread(() -> {
                try {
                    reader.lines().forEach(line -> text.append(line).append('\n'));
                } catch (Exception ignored) {
                    // Stream closed when the process was killed; whatever we captured is enough.
                }
            }, "tex-log-pump");
            pump.setDaemon(true);
            pump.start();

            boolean finished = process.waitFor(properties.compileTimeoutSeconds(), TimeUnit.SECONDS);
            pump.join(2000);

            if (!finished) {
                process.destroyForcibly();
                killContainer(containerName);
                throw new PdfCompilationException(
                        "Compilation timed out after " + properties.compileTimeoutSeconds()
                                + "s. The document may contain an infinite loop.", tail(text.toString()));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            killContainer(containerName);
            throw new ServiceUnavailableException("PDF compilation was interrupted", ex);
        } catch (IOException ex) {
            throw new ServiceUnavailableException("Could not read compiler output: " + ex.getMessage(), ex);
        }

        String out = text.toString();
        if (out.contains("Unable to find image") || out.contains("pull access denied")) {
            throw new ServiceUnavailableException(
                    "The TeX image '" + properties.image() + "' is not built yet. Run once:\n"
                            + "  docker build -t " + properties.image() + " docker/tex");
        }
        if (out.contains("Cannot connect to the Docker daemon")) {
            throw new ServiceUnavailableException("Docker is not running. Start Docker Desktop and try again.");
        }
        return new ProcessOutput(out);
    }

    /** {@code --rm} usually suffices, but a forcibly killed CLI can leave the container behind. */
    private void killContainer(String containerName) {
        try {
            new ProcessBuilder(properties.dockerBinary(), "rm", "-f", containerName)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            Thread.currentThread().interrupt();
            log.debug("Could not clean up container {}", containerName, ex);
        }
    }

    private Path createWorkDir() {
        try {
            return Files.createTempDirectory("resume-tailor-tex-");
        } catch (IOException ex) {
            throw new ServiceUnavailableException("Could not create a temporary build directory", ex);
        }
    }

    private void deleteQuietly(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort; the OS reclaims the temp directory regardless.
                }
            });
        } catch (IOException ignored) {
            // Nothing actionable.
        }
    }

    /**
     * Returns the first real TeX error, which is far more useful than the last line, or
     * null when there is none. TeX marks errors with a leading "!"; warnings such as
     * "Overfull \hbox" or "LaTeX Warning" never start with one, so they do not fail a build.
     */
    static String firstTexError(String log) {
        if (log == null) {
            return null;
        }
        for (String line : log.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("!") && !trimmed.startsWith("!  ==>")) {
                return trimmed;
            }
        }
        return null;
    }

    private static String tail(String log) {
        if (log == null) {
            return "";
        }
        return log.length() <= LOG_TAIL_CHARS ? log : log.substring(log.length() - LOG_TAIL_CHARS);
    }

    private record ProcessOutput(String text) {
    }
}
