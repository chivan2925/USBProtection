package com.group.usbshield.desktop;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import javax.net.ssl.SSLException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.group.usbshield.desktop.CentralData.*;

final class DesktopController implements AutoCloseable {
    private static final String ALL_STATES = "Tất cả";
    private enum View {
        DASHBOARD("Tổng quan"), ENDPOINTS("Máy trạm"), DEVICES("Thiết bị USB"),
        WHITELIST("Danh sách cho phép"), EVENTS("Sự kiện USB"), AUDIT("Nhật ký quản trị");
        final String title;
        View(String title) { this.title = title; }
    }
    private record Row(Object source, List<String> cells) {}
    private record Filters(String search, String endpoint, String device, String user,
                           String state, String eventType, LocalDate from, LocalDate to) {}

    private final CentralClient client;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "usbshield-desktop-io");
        thread.setDaemon(true);
        return thread;
    });
    private Task<?> active;
    private View view = View.DASHBOARD;
    private int pageIndex;
    private boolean hasNext;
    private Filters appliedFilters;

    @FXML private VBox login;
    @FXML private BorderPane workspace;
    @FXML private TextField username;
    @FXML private PasswordField password;
    @FXML private Label identity;
    @FXML private Label title;
    @FXML private Label notice;
    @FXML private ProgressIndicator progress;
    @FXML private Button cancel;
    @FXML private HBox searchBar;
    @FXML private TextField query;
    @FXML private TextField endpointFilter;
    @FXML private TextField deviceFilter;
    @FXML private TextField userFilter;
    @FXML private ComboBox<String> stateFilter;
    @FXML private ComboBox<String> eventType;
    @FXML private HBox dateBar;
    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;
    @FXML private Button clearDates;
    @FXML private FlowPane dashboard;
    @FXML private HBox dashboardCaption;
    @FXML private Label endpointCounts;
    @FXML private TableView<Row> table;
    @FXML private HBox paging;
    @FXML private Label pageLabel;
    @FXML private Button previous;
    @FXML private Button next;
    @FXML private HBox actions;

    DesktopController(CentralClient client) { this.client = client; }

    @FXML private void initialize() {
        table.setPlaceholder(new Label("Không có kết quả."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((ignored, before, selected) -> showActions(selected));
        showLogin();
    }

    @FXML private void signIn() {
        String account = username.getText().strip();
        String secret = password.getText();
        password.clear();
        if (account.isEmpty() || secret.isBlank()) {
            message("Nhập tên tài khoản và mật khẩu.", true);
            return;
        }
        run(() -> client.login(account, secret), session -> {
            identity.setText(session.username());
            show(login, false);
            show(workspace, true);
            navigate(View.DASHBOARD, "");
        }, false);
    }

    @FXML private void signOut() {
        run(() -> { client.logout(); return null; }, ignored -> showLogin(), false, problem -> {
            showLogin();
            message("Đã xóa phiên cục bộ. Central chưa xác nhận đăng xuất.", true);
        });
    }

    private void showLogin() {
        show(workspace, false);
        show(login, true);
        table.getItems().clear();
        dashboard.getChildren().clear();
        identity.setText("");
        appliedFilters = null;
        query.clear();
        endpointFilter.clear();
        deviceFilter.clear();
        userFilter.clear();
        stateFilter.setValue(null);
        password.clear();
        message("", false);
    }

    @FXML private void openDashboard() { navigate(View.DASHBOARD, ""); }
    @FXML private void openEndpoints() { navigate(View.ENDPOINTS, ""); }
    @FXML private void openDevices() { navigate(View.DEVICES, ""); }
    @FXML private void openWhitelist() { navigate(View.WHITELIST, ""); }
    @FXML private void openEvents() { navigate(View.EVENTS, ""); }
    @FXML private void openAudit() { navigate(View.AUDIT, ""); }

    private void navigate(View target, String endpoint) {
        view = target;
        title.setText(target.title);
        query.clear();
        endpointFilter.setText(endpoint);
        endpointFilter.setPromptText(target == View.EVENTS ? "Máy trạm / endpoint" : "Endpoint ID");
        deviceFilter.clear();
        userFilter.clear();
        var states = switch (target) {
            case ENDPOINTS -> List.of("online", "offline");
            case DEVICES -> List.of("allowed", "blocked", "detected", "disconnected");
            case EVENTS -> List.of("ALLOWED", "BLOCKED");
            default -> List.<String>of();
        };
        stateFilter.getItems().setAll(states);
        stateFilter.getItems().addFirst(ALL_STATES);
        stateFilter.setValue(ALL_STATES);
        show(stateFilter, !states.isEmpty());
        show(query, target == View.ENDPOINTS || target == View.DEVICES || target == View.WHITELIST);
        show(endpointFilter, target == View.DEVICES || target == View.WHITELIST || target == View.EVENTS);
        show(deviceFilter, target == View.EVENTS);
        show(userFilter, target == View.EVENTS);
        eventType.getItems().setAll(ALL_STATES, "CONNECTED", "DISCONNECTED", "POLICY_APPLIED");
        eventType.setValue(ALL_STATES);
        show(eventType, target == View.EVENTS);
        show(clearDates, target == View.EVENTS);
        show(searchBar, target != View.DASHBOARD && target != View.AUDIT);
        show(dateBar, target == View.DASHBOARD || target == View.EVENTS);
        var today = LocalDate.now();
        fromDate.setValue(target == View.DASHBOARD ? today : null);
        toDate.setValue(target == View.DASHBOARD ? today : null);
        show(paging, target != View.DASHBOARD);
        show(dashboard, target == View.DASHBOARD);
        show(dashboardCaption, target == View.DASHBOARD);
        reload(0);
    }

    @FXML private void search() { reload(0); }
    @FXML private void refresh() { reload(pageIndex); }
    @FXML private void previousPage() { if (pageIndex > 0) reload(pageIndex - 1); }
    @FXML private void nextPage() { if (hasNext) reload(pageIndex + 1); }
    @FXML private void cancelRead() { if (active != null) active.cancel(true); }
    @FXML private void clearEventDates() { fromDate.setValue(null); toDate.setValue(null); reload(0); }

    private void reload(int index) {
        var filters = new Filters(query.getText(), endpointFilter.getText(), deviceFilter.getText(), userFilter.getText(),
                selected(stateFilter), selected(eventType), fromDate.getValue(), toDate.getValue());
        int requestedPage = filters.equals(appliedFilters) ? index : 0;
        appliedFilters = filters;
        pageIndex = requestedPage;
        table.getItems().clear();
        dashboard.getChildren().clear();
        endpointCounts.setText("");
        showActions(null);
        pageLabel.setText("");
        setPageButtons(0, false);
        switch (view) {
            case DASHBOARD -> loadDashboard();
            case ENDPOINTS -> run(() -> client.endpoints(requestedPage, filters.search(), filters.state()), page -> present(page,
                    List.of("Máy trạm", "Kết nối", "Người dùng", "USBGuard", "Mong muốn", "Đã ACK", "Liên lạc cuối"),
                    e -> cells(e.hostname(), e.status(), e.currentUser(), health(e), e.policyVersion(),
                            e.appliedPolicyVersion(), e.lastSeen())), true);
            case DEVICES -> run(() -> client.devices(requestedPage, filters.search(), filters.endpoint(), filters.state()), page -> present(page,
                    List.of("Thiết bị", "Máy trạm", "Trạng thái ghi nhận", "HASH", "VID:PID", "Serial", "Phát hiện"),
                    d -> cells(d.name(), d.endpointName(), d.status(), d.hash(), d.vendorId() + ":" + d.productId(),
                            d.serialNumber(), d.detectedAt())), true);
            case WHITELIST -> run(() -> client.whitelist(requestedPage, filters.search(), filters.endpoint()), page -> present(page,
                    List.of("Thiết bị", "Máy trạm", "HASH", "Serial", "Thời điểm cấp"),
                    w -> cells(w.name(), w.endpointId(), w.fingerprintValue(), w.serial(), w.createdAt())), true);
            case EVENTS -> loadEvents(requestedPage, filters);
            case AUDIT -> run(() -> client.audit(requestedPage), page -> present(page,
                    List.of("Thời điểm", "Tài khoản", "Thao tác", "Đối tượng"),
                    a -> cells(a.occurredAt(), a.actorUsername(), a.action(), a.targetId())), true);
        }
    }

    private void loadDashboard() {
        var from = fromDate.getValue();
        var to = toDate.getValue();
        if (from == null || to == null || to.isBefore(from)) {
            message("Chọn khoảng ngày hợp lệ.", true);
            return;
        }
        var zone = ZoneId.systemDefault();
        run(() -> client.dashboard(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant()),
                snapshot -> {
                    var totals = snapshot.summary();
                    dashboard.getChildren().setAll(metric("Thiết bị ghi nhận", totals.totalDevices()),
                            metric("Bị chặn theo ghi nhận gần nhất", totals.blockedDevices()),
                            metric("Được phép theo ghi nhận gần nhất", totals.allowedDevices()),
                            metric("Sự kiện lưu trữ", totals.recentEvents()),
                            metric("Ghi nhận gần nhất là chặn, trong kỳ", totals.blockedToday()));
                    var counts = snapshot.endpoints();
                    endpointCounts.setText(counts.onlineEndpoints() + " online  ·  " + counts.offlineEndpoints() + " offline");
                    displayRows(snapshot.recent(), eventHeadings(), DesktopController::eventCells);
                }, true);
    }

    private void loadEvents(int index, Filters draft) {
        var from = draft.from();
        var to = draft.to();
        if (from != null && to != null && to.isBefore(from)) {
            message("Chọn khoảng ngày hợp lệ.", true);
            return;
        }
        var zone = ZoneId.systemDefault();
        var filters = Map.of("endpoint", draft.endpoint(), "device", draft.device(), "username", draft.user(),
                "decision", draft.state(), "eventType", draft.eventType(), "from", from == null ? "" : from.atStartOfDay(zone).toInstant().toString(),
                "to", to == null ? "" : inclusiveEventEnd(to, zone).toString());
        run(() -> client.events(index, filters),
                page -> present(page, eventHeadings(), DesktopController::eventCells), true);
    }

    static Instant inclusiveEventEnd(LocalDate date, ZoneId zone) {
        return date.plusDays(1).atStartOfDay(zone).toInstant().minus(1, ChronoUnit.MICROS);
    }

    private static String selected(ComboBox<String> combo) {
        String value = combo.getValue();
        return ALL_STATES.equals(value) ? "" : Objects.toString(value, "");
    }

    private static List<String> eventHeadings() {
        return List.of("Thời điểm", "Máy trạm", "Người dùng Ubuntu", "Thiết bị", "Sự kiện", "Quyết định");
    }

    private static List<String> eventCells(Event event) {
        return cells(event.occurredAt(), event.hostname(), event.linuxUsername(),
                event.device() == null ? null : event.device().name(), event.eventType(), event.decision());
    }

    private <T> void present(Page<T> page, List<String> headings, Function<T, List<String>> adapter) {
        displayRows(page.content(), headings, adapter);
        pageIndex = page.page();
        pageLabel.setText("Trang " + (pageIndex + 1) + "  ·  " + page.totalElements() + " kết quả");
        setPageButtons(pageIndex, page.hasNext());
    }

    private <T> void displayRows(List<T> items, List<String> headings, Function<T, List<String>> adapter) {
        table.getColumns().clear();
        for (int index = 0; index < headings.size(); index++) {
            int field = index;
            var column = new TableColumn<Row, String>(headings.get(index));
            column.setCellValueFactory(row -> new ReadOnlyStringWrapper(row.getValue().cells().get(field)));
            column.setSortable(false);
            table.getColumns().add(column);
        }
        table.getItems().setAll(items.stream().map(item -> new Row(item, adapter.apply(item))).toList());
    }

    private void setPageButtons(int index, boolean more) {
        hasNext = more;
        previous.setDisable(index == 0);
        next.setDisable(!more);
    }

    private void showActions(Row row) {
        actions.getChildren().clear();
        if (row == null) return;
        switch (row.source()) {
            case Endpoint value -> {
                action("Chi tiết", () -> run(() -> client.endpoint(value.endpointId()), this::endpointDetail, true));
                action("Thiết bị đã thấy", () -> navigate(View.DEVICES, value.endpointId()));
                action("Quyền cho phép", () -> navigate(View.WHITELIST, value.endpointId()));
                action("Cấp vé", () -> issueTicket(value.endpointId()));
                action("Thu hồi credential", () -> revokeCredential(value));
            }
            case Device value -> {
                action("Chi tiết USB", () -> detail(text(value.name()), lines("Máy trạm", value.endpointName(),
                        "Endpoint", value.endpointId(), "HASH", value.hash(), "VID:PID", value.vendorId() + ":" + value.productId(),
                        "Serial", value.serialNumber(), "Trạng thái ghi nhận", value.status(), "Phát hiện", value.detectedAt())));
                if (value.usbDeviceId() != null && value.hash() != null && !value.hash().isBlank()) {
                    action("Cho phép tại máy này", () -> allowDevice(value));
                }
            }
            case Rule value -> {
                action("Chi tiết quy tắc", () -> detail(text(value.name()), lines("Endpoint", value.endpointId(),
                        "HASH", value.fingerprintValue(), "VID:PID", value.vendorId() + ":" + value.productId(),
                        "Serial", value.serial(), "Thời điểm cấp", value.createdAt())));
                action("Thu hồi quyền cho phép", () -> revokeRule(value));
            }
            case Event value -> action("Chi tiết sự kiện", () -> run(() -> client.event(value.id()), this::eventDetail, true));
            default -> { }
        }
    }

    private void action(String label, Runnable handler) {
        var button = new Button(label);
        button.setOnAction(event -> handler.run());
        actions.getChildren().add(button);
    }

    private void endpointDetail(Endpoint e) {
        detail(e.hostname(), lines("Endpoint", e.endpointId(), "Hệ điều hành", e.operatingSystem(),
                "Địa chỉ", e.ipAddress(), "Người dùng Ubuntu", e.currentUser(), "Kết nối", e.status(),
                "Liên lạc cuối", e.lastSeen(), "USBGuard", health(e), "Kiểm tra cuối", e.usbGuardCheckedAt(),
                "Policy mong muốn", e.policyVersion(), "Policy Agent đã ACK", e.appliedPolicyVersion()));
    }

    private void eventDetail(Event e) {
        var d = e.device();
        detail("Sự kiện USB", lines("Máy trạm", e.hostname(), "Endpoint", e.endpointId(),
                "Thiết bị", d == null ? null : d.name(), "HASH", d == null ? null : d.hash(),
                "Sự kiện", e.eventType(), "Quyết định", e.decision(), "Thời điểm", e.occurredAt(),
                "Central nhận", e.receivedAt(), "Người dùng", e.linuxUsername(), "UID", e.linuxUid(),
                "Phiên", e.sessionId(), "Loại phiên", e.sessionType(), "Seat", e.seat()));
    }

    private void allowDevice(Device value) {
        String description = deviceIdentity(value);
        if (confirm("Cho phép USB", description + "\nÁp dụng sau khi Agent đồng bộ và USB được rút/cắm lại.")) {
            change(() -> client.allow(value.endpointId(), value.usbDeviceId()));
        }
    }

    private void revokeRule(Rule value) {
        if (confirm("Thu hồi quyền cho phép", ruleIdentity(value)
                + "\nThay đổi chính sách mong muốn. Kiểm tra thiết bị sau đồng bộ và rút/cắm lại.")) {
            change(() -> client.revoke(value.endpointId(), value.id()));
        }
    }

    static String deviceIdentity(Device device) {
        return lines("Thiết bị", device.name(), "Endpoint", device.endpointId(),
                "USB ID", device.usbDeviceId(), "HASH", device.hash()).stripTrailing();
    }

    static String ruleIdentity(Rule rule) {
        return lines("Thiết bị", rule.name(), "Endpoint", rule.endpointId(),
                "Rule ID", rule.id(), "HASH", rule.fingerprintValue()).stripTrailing();
    }

    private void revokeCredential(Endpoint value) {
        if (confirm("Thu hồi credential Agent", value.endpointId()
                + "\nAgent sẽ cần vé mới để kết nối lại. Quy tắc USBGuard trên máy vẫn có hiệu lực cục bộ.")) {
            change(() -> client.revokeCredential(value.endpointId()));
        }
    }

    private void change(CheckedAction command) {
        run(() -> { command.run(); return null; }, ignored -> reload(pageIndex), false);
    }

    @FXML private void createSlot() {
        var installation = new TextField();
        installation.setPromptText("Installation UUID đã xác minh");
        var name = new TextField();
        name.setPromptText("Tên máy trạm");
        var form = new VBox(9, new Label("Installation UUID"), installation, new Label("Tên máy trạm"), name);
        var dialog = dialog("Tạo slot đăng ký");
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            String raw = installation.getText().strip();
            UUID id = UUID.fromString(raw);
            String hostname = name.getText().strip();
            if (!id.toString().equalsIgnoreCase(raw) || hostname.isBlank() || hostname.length() > 150) {
                throw new IllegalArgumentException();
            }
            run(() -> client.allocateSlot(id, hostname), slot -> {
                navigate(View.ENDPOINTS, "");
                detail("Slot đã tạo", slot.endpointId() + "\nChọn máy trạm và dùng Cấp vé để đăng ký.");
            }, false);
        } catch (IllegalArgumentException invalid) {
            message("Nhập UUID đầy đủ và tên máy trạm hợp lệ.", true);
        }
    }

    private void issueTicket(String endpoint) {
        if (!confirm("Cấp vé đăng ký", endpoint + "\nVé trước chưa dùng sẽ bị thay thế.")) return;
        run(() -> client.issueTicket(endpoint), ticket -> {
            var value = new TextField(ticket.enrollmentToken());
            value.setEditable(false);
            var dialog = dialog("Vé đăng ký Agent");
            dialog.getDialogPane().setContent(new VBox(9, new Label("Hết hạn: " + ticket.expiresAt()),
                    new Label("Chuyển vé qua kênh IT được bảo vệ."), value));
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
            dialog.showAndWait();
            value.clear();
        }, false);
    }

    private Dialog<ButtonType> dialog(String heading) {
        var dialog = new Dialog<ButtonType>();
        dialog.setTitle("USBShield");
        dialog.setHeaderText(heading);
        dialog.initOwner(workspace.getScene().getWindow());
        dialog.getDialogPane().setPrefWidth(530);
        dialog.getDialogPane().getStylesheets().addAll(workspace.getScene().getStylesheets());
        return dialog;
    }

    private boolean confirm(String heading, String content) {
        var dialog = dialog(heading);
        var label = new Label(content);
        label.setWrapText(true);
        dialog.getDialogPane().setContent(label);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        return dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void detail(String heading, String content) {
        var field = new TextArea(content);
        field.setWrapText(true);
        field.setEditable(false);
        var dialog = dialog(heading);
        dialog.getDialogPane().setContent(field);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private <T> void run(Callable<T> request, Consumer<T> success, boolean cancellable) {
        run(request, success, cancellable, this::showFailure);
    }

    private <T> void run(Callable<T> request, Consumer<T> success, boolean cancellable, Consumer<Throwable> failure) {
        if (active != null) return;
        var task = new Task<T>() {
            @Override protected T call() throws Exception { return request.call(); }
        };
        task.setOnSucceeded(ignored -> { finish(); success.accept(task.getValue()); });
        task.setOnFailed(ignored -> { finish(); failure.accept(task.getException()); });
        task.setOnCancelled(ignored -> { finish(); message("Đã hủy tải dữ liệu.", false); });
        active = task;
        login.setDisable(true);
        workspace.setDisable(true);
        show(progress, true);
        show(cancel, cancellable);
        message("Đang kết nối Central…", false);
        worker.execute(task);
    }

    private void finish() {
        active = null;
        login.setDisable(false);
        workspace.setDisable(false);
        show(progress, false);
        show(cancel, false);
        message("", false);
    }

    private void showFailure(Throwable error) {
        if (error instanceof CentralClient.RequestFailure rejection) {
            if (rejection.status() == 401 && workspace.isVisible()) {
                showLogin();
                message("Phiên đã hết hiệu lực. Hãy đăng nhập lại.", true);
                return;
            }
            message(switch (rejection.status()) {
                case 400 -> "Central từ chối dữ liệu nhập. Kiểm tra các trường và bộ lọc.";
                case 401 -> "Tên tài khoản hoặc mật khẩu không hợp lệ.";
                case 403 -> "Yêu cầu bị từ chối. Kiểm tra quyền hoặc đăng nhập lại.";
                case 404 -> "Tài nguyên không còn khả dụng trong workspace.";
                case 409 -> "Trạng thái tại Central đã thay đổi hoặc điều kiện thao tác chưa đạt. Tải lại để kiểm tra.";
                default -> "Central trả HTTP " + rejection.status() + ". Tải lại trước khi thử lại thao tác.";
            }, true);
        } else if (error instanceof SSLException) {
            message("Xác minh TLS thất bại. Kiểm tra CA, SAN 127.0.0.1 và giờ hệ thống.", true);
        } else if (error instanceof HttpTimeoutException) {
            message("Central phản hồi quá thời gian. Tải lại để kiểm tra trước khi thử lại thao tác.", true);
        } else {
            message("Không đọc được phản hồi từ Central. Kiểm tra dịch vụ và HTTPS cục bộ.", true);
        }
    }

    private void message(String value, boolean error) {
        notice.setText(value);
        notice.getStyleClass().remove("error");
        if (error) notice.getStyleClass().add("error");
    }

    private static VBox metric(String name, long value) {
        var heading = new Label(name);
        var number = new Label(Long.toString(value));
        number.getStyleClass().add("metric-number");
        var card = new VBox(13, heading, number);
        card.getStyleClass().add("metric");
        card.setMinWidth(185);
        card.setPadding(new Insets(19));
        return card;
    }

    private static String health(Endpoint e) { return text(e.usbGuardStatus()) + (e.usbGuardStale() ? " · cũ" : ""); }
    private static List<String> cells(Object... values) { return Arrays.stream(values).map(DesktopController::text).toList(); }
    private static String text(Object value) { return value == null || value.toString().isBlank() ? "—" : value.toString(); }

    private static String lines(Object... pairs) {
        var result = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            result.append(pairs[i]).append(": ").append(text(pairs[i + 1])).append('\n');
        }
        return result.toString();
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    @Override public void close() {
        if (active != null) active.cancel(true);
        worker.shutdownNow();
        client.close();
    }

    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
}
