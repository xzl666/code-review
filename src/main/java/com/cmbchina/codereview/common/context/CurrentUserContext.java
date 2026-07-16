package com.cmbchina.codereview.common.context;

public final class CurrentUserContext {

    public static final String ADMIN_USER_ID = "00000000000000000000000000000001";

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(String userId) { USER_ID.set(userId); }

    public static String get() { return USER_ID.get(); }

    public static boolean isAdmin() { return ADMIN_USER_ID.equals(USER_ID.get()); }

    public static void clear() { USER_ID.remove(); }
}
