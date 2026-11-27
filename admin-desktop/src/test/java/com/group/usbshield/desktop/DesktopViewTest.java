package com.group.usbshield.desktop;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class DesktopViewTest {
    @TempDir Path directory;

    @BeforeAll static void startToolkit() throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            ready.countDown();
        });
        assertTrue(ready.await(15, TimeUnit.SECONDS), "JavaFX toolkit did not start");
    }

    @AfterAll static void stopToolkit() { Platform.exit(); }

    @Test void actualFxmlSupportsLoginDashboardNavigationAndLogout() throws Exception {
        try (var fixture = new LocalHttpsFixture(directory, "ip:127.0.0.1");
             var controller = new DesktopController(new CentralClient(fixture.origin(), CentralTls.trust(fixture.ca)))) {
            var requests = new CopyOnWriteArrayList<String>();
            fixture.server.createContext("/", exchange -> CentralClientTest.admin(exchange, requests));
            fixture.server.start();
            var view = fx(() -> {
                var loader = new FXMLLoader(DesktopApplication.class.getResource("desktop.fxml"));
                loader.setController(controller);
                Parent root = loader.load();
                var scene = new Scene(root, 1180, 760);
                scene.getStylesheets().add(DesktopApplication.class.getResource("desktop.css").toExternalForm());
                assertTrue(root.lookup("#login").isVisible());
                ((TextField) root.lookup("#username")).setText("test-admin");
                ((TextField) root.lookup("#password")).setText("fixture-password");
                button(root, "Đăng nhập").fire();
                assertEquals("", ((TextField) root.lookup("#password")).getText());
                return root;
            });
            await(() -> view.lookup("#workspace").isVisible() && !view.lookup("#workspace").isDisabled());
            assertEquals("Tổng quan", fx(() -> ((Label) view.lookup("#title")).getText()));
            assertTrue(fx(() -> view.lookup("#dashboard").isVisible()));
            fx(() -> { button(view, "Máy trạm").fire(); return null; });
            await(() -> !view.lookup("#workspace").isDisabled());
            assertEquals(1, fx(() -> ((TableView<?>) view.lookup("#table")).getItems().size()));
            assertFalse(fx(() -> view.lookup("#next").isDisabled()));
            fx(() -> { button(view, "Trang sau").fire(); return null; });
            await(() -> !view.lookup("#workspace").isDisabled());
            assertTrue(fx(() -> ((Label) view.lookup("#pageLabel")).getText().startsWith("Trang 2")));
            fx(() -> {
                ((TextField) view.lookup("#query")).setText("máy khác");
                button(view, "Tải lại").fire();
                return null;
            });
            await(() -> !view.lookup("#workspace").isDisabled());
            assertTrue(fx(() -> ((Label) view.lookup("#pageLabel")).getText().startsWith("Trang 1")));
            fx(() -> { button(view, "Sự kiện USB").fire(); return null; });
            await(() -> !view.lookup("#workspace").isDisabled());
            assertEquals("Sự kiện USB", fx(() -> ((Label) view.lookup("#title")).getText()));
            assertEquals(java.util.List.of("Tất cả", "ALLOWED", "BLOCKED"),
                    fx(() -> ((ComboBox<?>) view.lookup("#stateFilter")).getItems()));
            assertEquals(1, fx(() -> ((TableView<?>) view.lookup("#table")).getItems().size()));
            var day = LocalDate.of(2026, 10, 16);
            var zone = ZoneId.systemDefault();
            var expectedFrom = day.atStartOfDay(zone).toInstant();
            var expectedTo = day.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1_000);
            fx(() -> {
                ((DatePicker) view.lookup("#fromDate")).setValue(day);
                ((DatePicker) view.lookup("#toDate")).setValue(day);
                button(view, "Áp dụng").fire();
                return null;
            });
            await(() -> !view.lookup("#workspace").isDisabled());
            assertTrue(requests.stream().filter(request -> request.startsWith("GET /api/admin/events?"))
                    .map(request -> URI.create(request.substring(4)).getQuery())
                    .map(query -> java.util.List.of(query.split("&")))
                    .anyMatch(parameters -> parameters.contains("from=" + expectedFrom) && parameters.contains("to=" + expectedTo)),
                    "Applying the dates must send the local start and inclusive microsecond end in the same request");
            fx(() -> { button(view, "Đăng xuất").fire(); return null; });
            await(() -> view.lookup("#login").isVisible() && !view.lookup("#login").isDisabled());
            assertFalse(fx(() -> view.lookup("#workspace").isVisible()));
            assertEquals(0, fx(() -> ((TableView<?>) view.lookup("#table")).getItems().size()));
        }
    }

    private static Button button(Parent root, String label) {
        return root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> label.equals(b.getText())).findFirst().orElseThrow();
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long until = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        do {
            if (fx(condition)) return;
            Thread.sleep(25);
        } while (System.nanoTime() < until);
        fail("Desktop action did not complete");
    }

    private static <T> T fx(Callable<T> call) throws Exception {
        var task = new FutureTask<>(call);
        Platform.runLater(task);
        try {
            return task.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            for (var stack : Thread.getAllStackTraces().entrySet()) {
                if (stack.getKey().getName().equals("JavaFX Application Thread")) {
                    timeout.setStackTrace(stack.getValue());
                    break;
                }
            }
            throw timeout;
        }
    }
}
