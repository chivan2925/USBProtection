package com.group.usbshield.agent.usbguard;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class UsbGuardCommandRunner {
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;
    private final Duration timeout;

    public UsbGuardCommandRunner() {
        this(Duration.ofSeconds(10));
    }

    public UsbGuardCommandRunner(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout");
        }
        this.timeout = timeout;
    }

    public CommandResult run(String... arguments) {
        if (arguments == null || arguments.length == 0) {
            throw new IllegalArgumentException("USBGuard subcommand required");
        }
        List<String> command = new ArrayList<>();
        command.add("usbguard");
        for (String argument : arguments) {
            Objects.requireNonNull(argument, "USBGuard argument");
            if (argument.isBlank() || argument.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Invalid USBGuard argument");
            }
            command.add(argument);
        }
        Process process = null;
        ExecutorService reader = Executors.newSingleThreadExecutor();
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            Process launched = process;
            Future<String> output = reader.submit(() -> readLimited(launched));
            if (!process.waitFor(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("USBGuard command timed out: " + arguments[0]);
            }
            return new CommandResult(process.exitValue(), output.get(2, TimeUnit.SECONDS));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot execute USBGuard (check installation / IPC ACL)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("USBGuard command interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Cannot collect USBGuard response", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            reader.shutdownNow();
        }
    }

    private static String readLimited(Process process) throws IOException {
        try (InputStream input = process.getInputStream(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (buffer.size() + count > MAX_OUTPUT_BYTES) {
                    process.destroyForcibly();
                    throw new IOException("USBGuard output exceeds safe limit");
                }
                buffer.write(chunk, 0, count);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
