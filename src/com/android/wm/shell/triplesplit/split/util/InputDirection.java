package com.android.wm.shell.triplesplit.split.util;

public enum InputDirection {
    Min(-1),
    Max(1);

    private final int sign;

    InputDirection(int sign) {
        this.sign = sign;
    }

    public int getSign() {
        return sign;
    }
}
