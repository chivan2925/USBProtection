package com.group.usbshield.agent.usbguard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

/** Member 2 — Week 2. Runs USBGuard CLI without a shell, sudo or unbounded waits. */
public class UsbGuardCommandRunner {
    private final Duration timeout;
    public UsbGuardCommandRunner() { this(Duration.ofSeconds(10)); }
    public UsbGuardCommandRunner(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout");
        this.timeout = timeout;
    }
    public CommandResult run(String... arguments) {
        List<String> command = new ArrayList<>(); command.add("usbguard");
        for (String arg : arguments) {
            Objects.requireNonNull(arg, "USBGuard argument");
            if (arg.indexOf('\0') >= 0) throw new IllegalArgumentException("NUL in argument");
            command.add(arg);
        }
        Process process = null;
        ExecutorService reader = Executors.newSingleThreadExecutor();
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            Process live = process;
            Future<String> output = reader.submit(() -> new String(live.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("USBGuard command timed out: " + arguments[0]);
            }
            return new CommandResult(process.exitValue(), output.get(2, TimeUnit.SECONDS));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot execute USBGuard (check installation / IPC ACL)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("USBGuard command interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Cannot collect USBGuard response", e);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            reader.shutdownNow();
        }
    }
}
