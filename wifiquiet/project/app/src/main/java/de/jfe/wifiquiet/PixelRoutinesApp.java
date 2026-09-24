package de.jfe.wifiquiet;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

public class PixelRoutinesApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
