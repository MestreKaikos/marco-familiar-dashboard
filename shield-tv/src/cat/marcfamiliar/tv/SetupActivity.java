package cat.marcfamiliar.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class SetupActivity extends Activity {
    static final String PREFS = "shield_photos";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private EditText url, key;
    private TextView status;
    private Button connect;
    private SharedPreferences prefs;

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_VERTICAL);
        form.setPadding(dp(48), dp(24), dp(48), dp(24));
        scroll.addView(form);
        TextView title = new TextView(this);
        title.setText("Marc Familiar TV");
        title.setTextSize(28);
        form.addView(title);
        form.addView(label("Connecta la mateixa carpeta de fotos que fa servir el marc."));
        form.addView(label("Adreça del servei familiar"));
        url = field(form, "https://script.google.com/macros/s/…/exec", InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(prefs.getString("url", ""));
        form.addView(label("Clau familiar"));
        key = field(form, "Clau de 64 caràcters", InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(prefs.getString("key", ""));
        connect = new Button(this);
        connect.setText("Comprova i desa");
        form.addView(connect);
        connect.setOnClickListener(v -> save());
        status = label(prefs.contains("collection") ? "Connexió desada." : "");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        form.addView(status);
        Button settings = new Button(this);
        settings.setText("Tria el salvapantalles");
        settings.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS)); }
            catch (ActivityNotFoundException e) { status.setText("Obre Configuració → Preferències del dispositiu → Salvapantalles."); }
        });
        form.addView(settings);
        setContentView(scroll);
        if (prefs.contains("collection")) settings.requestFocus(); else url.requestFocus();
    }

    private TextView label(String caption) {
        TextView text = new TextView(this);
        text.setText(caption);
        text.setTextColor(Color.LTGRAY);
        text.setTextSize(17);
        return text;
    }

    private EditText field(LinearLayout parent, String hint, int variation) {
        EditText input = new EditText(this);
        input.setSingleLine();
        input.setSaveEnabled(false);
        input.setTextSize(16);
        input.setInputType(InputType.TYPE_CLASS_TEXT | variation | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(hint);
        parent.addView(input);
        return input;
    }

    private void save() {
        String address = url.getText().toString().trim(), secret = key.getText().toString().trim();
        String previous = prefs.getString("collection", "");
        boolean same = !previous.isEmpty() && address.equals(prefs.getString("url", "")) && secret.equals(prefs.getString("key", ""));
        String collection = same ? previous : UUID.randomUUID().toString();
        setBusy(true);
        status.setText("Comprovant la carpeta de fotos…");
        worker.execute(() -> {
            String message;
            File root = new File(getFilesDir(), "collections");
            try {
                PhotoSource source = new PhotoSource(address, secret);
                List<PhotoSource.Photo> photos = source.list();
                PhotoStore cache = new PhotoStore(root, collection);
                cache.reconcile(photos);
                if (!photos.isEmpty() && cache.cached().isEmpty()) {
                    // Prime the offline slideshow on first setup and verify the second API call.
                    Exception last = null;
                    for (int i = 0; i < Math.min(3, photos.size()); i++) {
                        try { cache.load(source, photos.get(i), 1280, 720).recycle(); last = null; break; }
                        catch (Exception e) { last = e; }
                    }
                    if (last != null) throw last;
                }
                if (!prefs.edit().putString("url", address).putString("key", secret)
                        .putString("collection", collection).commit()) throw new IOException("No s’ha pogut desar la connexió.");
                if (!same && !previous.isEmpty()) clearCollection(new File(root, previous));
                message = photos.isEmpty() ? "Connexió desada. La carpeta és buida."
                        : "Connexió desada · " + photos.size() + " fotos. Ja pots triar Marc Familiar TV.";
            } catch (Exception e) {
                if (!same) clearCollection(new File(root, collection));
                message = e instanceof IOException && e.getClass() == IOException.class ? e.getMessage()
                        : "No s’han pogut llegir les fotos. Comprova la xarxa, l’adreça i la clau.";
            }
            String result = message;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                status.setText(result);
                setBusy(false);
            });
        });
    }

    private void setBusy(boolean busy) {
        connect.setEnabled(!busy); url.setEnabled(!busy); key.setEnabled(!busy);
    }

    private void clearCollection(File folder) {
        File[] files = folder.listFiles();
        if (files != null) for (File f : files) f.delete();
        folder.delete();
    }

    @Override public void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
