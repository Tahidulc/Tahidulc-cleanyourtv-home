package com.gathin.tvhomephone.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.R;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** Settings' App updates section draws in the app's own theme, saying what the TVs have. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UpdateSettingsTest {
    public static class Host extends Fragment {
        @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
            return UpdateSettings.build(this);
        }
    }

    private static List<String> texts(View v, List<String> out) {
        if (v instanceof TextView) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) texts(((ViewGroup) v).getChildAt(i), out);
        return out;
    }

    private static View shown() {
        org.robolectric.android.controller.ActivityController<AppCompatActivity> ctl = Robolectric.buildActivity(AppCompatActivity.class);
        ctl.get().setTheme(R.style.Theme_TvHome);
        AppCompatActivity a = ctl.setup().get();
        Host h = new Host();
        a.getSupportFragmentManager().beginTransaction().add(android.R.id.content, h).commitNow();
        View v = h.getView();
        assertNotNull(v);
        return v;
    }

    @Test public void saysUpdatesComeFromTheTvsBeforeAnyIsPaired() {
        String all = String.join(" | ", texts(shown(), new ArrayList<>()));
        assertTrue(all, all.contains("APP UPDATES"));
        assertTrue(all, all.contains("Updates come from your TVs"));
        assertTrue(all, all.contains("Check for updates"));
    }

    @Test public void aNewerVersionOnATvIsOfferedWithItsSize() throws Exception {
        android.content.Context c = org.robolectric.RuntimeEnvironment.getApplication();
        JSONObject o = new JSONObject().put("code", Integer.MAX_VALUE).put("name", "9.99").put("notes", "Something new")
                .put("file", "Homefield-9.99.apk").put("size", 139040928L)
                .put("sha256", "eea09be0a7b4f0beb15bd86909d5a0ab87406df5a1aa1ba2d1af58de381bba6c")
                .put("tv", "Bedroom TV").put("host", "192.168.1.50").put("port", 47820);
        AppUpdate.prefs(c).edit().putString("offer", o.toString()).commit();
        try {
            assertNotNull(AppUpdate.ready(c));
            String all = String.join(" | ", texts(shown(), new ArrayList<>()));
            assertTrue(all, all.contains("Homefield 9.99 is ready on Bedroom TV: Something new · 133 MB"));
            assertTrue(all, all.contains("Update to 9.99"));
            assertEquals(AppUpdate.Stage.IDLE, AppUpdate.stage());
        } finally {
            AppUpdate.prefs(c).edit().clear().commit();
        }
    }
}
