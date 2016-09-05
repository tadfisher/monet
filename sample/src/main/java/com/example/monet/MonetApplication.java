package com.example.monet;

import android.app.Application;
import android.os.StrictMode;

/**
 * Created by tad on 9/4/16.
 */

public class MonetApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        StrictMode.enableDefaults();
    }
}
