package com.android.documentsui.util;

/***
 * @Jake
 *
 */
public class NoFastClickUtils {
    private static long lastClickTime = 0;
    private static int spaceTime = 800;

    public static boolean isFastClick() {

        long currentTime = System.currentTimeMillis();
        boolean isAllowClick;

        if (currentTime - lastClickTime > spaceTime) {
            isAllowClick = false;
        } else {
            isAllowClick = true;
        }
        lastClickTime = currentTime;
        return isAllowClick;
    }

}