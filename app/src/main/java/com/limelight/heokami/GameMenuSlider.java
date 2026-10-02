package com.limelight.heokami;

final class GameMenuSlider {
    final int titleRes;
    final int min;
    final int max;
    final int step;
    final int defaultValue;
    final int currentValue;
    final GameMenuSliderCallback applyCallback;

    GameMenuSlider(int titleRes, int min, int max, int step, int defaultValue,
               int currentValue, GameMenuSliderCallback applyCallback) {
        this.titleRes = titleRes;
        this.min = min;
        this.max = max;
        this.step = step;
        this.defaultValue = defaultValue;
        this.currentValue = currentValue;
        this.applyCallback = applyCallback;
    }
}
