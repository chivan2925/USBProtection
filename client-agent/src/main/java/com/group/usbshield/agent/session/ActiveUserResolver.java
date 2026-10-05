package com.group.usbshield.agent.session;

/**
 * Interface phân giải người dùng đang hoạt động (do Member 2 phụ trách hiện thực qua loginctl).
 * Member 3 gọi interface này khi cần gắn Active User vào sự kiện cắm USB.
 */
public interface ActiveUserResolver {
    ActiveUserInfo resolveCurrentActiveUser();
}
