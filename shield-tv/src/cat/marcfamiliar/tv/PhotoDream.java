package cat.marcfamiliar.tv;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.service.dreams.DreamService;
import android.view.*;
import android.view.animation.AlphaAnimation;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class PhotoDream extends DreamService {
    private ImageSwitcher slideshow;
    private Player active;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);
        setScreenBright(true);
        setInteractive(false);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(Color.BLACK);
        slideshow = new ImageSwitcher(this);
        slideshow.setFactory(() -> {
            ImageView view = new ImageView(this);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            view.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
            return view;
        });
        AlphaAnimation fadeIn = new AlphaAnimation(0, 1), fadeOut = new AlphaAnimation(1, 0);
        fadeIn.setDuration(600); fadeOut.setDuration(600);
        slideshow.setInAnimation(fadeIn); slideshow.setOutAnimation(fadeOut);
        stage.addView(slideshow, new FrameLayout.LayoutParams(-1, -1));
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("HH:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(27);
        float dp = getResources().getDisplayMetrics().density;
        clock.setShadowLayer(3 * dp, 0, dp, Color.BLACK);
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
        corner.setMargins(0, 0, Math.round(32 * dp), Math.round(22 * dp));
        stage.addView(clock, corner);
        setContentView(stage);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        stopPlayer();
        SharedPreferences prefs = getSharedPreferences(SetupActivity.PREFS, MODE_PRIVATE);
        try {
            PhotoSource source = new PhotoSource(prefs.getString("url", ""), prefs.getString("key", ""));
            PhotoStore cache = new PhotoStore(new File(getFilesDir(), "collections"), prefs.getString("collection", ""));
            android.util.DisplayMetrics display = getResources().getDisplayMetrics();
            active = new Player(source, cache, Math.min(3840, display.widthPixels), Math.min(2160, display.heightPixels));
            active.worker.execute(active);
        } catch (IOException e) { finish(); }
    }

    @Override public void onDreamingStopped() { stopPlayer(); super.onDreamingStopped(); }
    @Override public void onDetachedFromWindow() { stopPlayer(); super.onDetachedFromWindow(); }

    private void stopPlayer() {
        if (active != null) { active.worker.shutdownNow(); active = null; }
    }

    private final class Player implements Runnable {
        final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        final PhotoSource source;
        final PhotoStore cache;
        final int width, height;
        List<PhotoSource.Photo> remote = new ArrayList<>(), deck = new ArrayList<>();
        List<File> localDeck = new ArrayList<>();
        String last = "";
        long nextCheck;
        boolean first = true;

        Player(PhotoSource source, PhotoStore cache, int width, int height) {
            this.source = source; this.cache = cache; this.width = width; this.height = height;
        }

        private void display(Bitmap bitmap, String filename) {
            last = filename;
            slideshow.post(() -> {
                if (active != this) { bitmap.recycle(); return; }
                ((ImageView) slideshow.getNextView()).setImageBitmap(bitmap);
                slideshow.showNext();
            });
        }

        private boolean showLocal() {
            if (localDeck.isEmpty()) { localDeck = cache.cached(); Collections.shuffle(localDeck); }
            while (!localDeck.isEmpty()) {
                int index = localDeck.size() - 1;
                if (index > 0 && localDeck.get(index).getName().equals(last)) index = 0;
                File file = localDeck.remove(index);
                try {
                    if (file.getName().equals(last)) continue;
                    display(cache.decode(file, width, height), file.getName()); return true;
                } catch (IOException e) { file.delete(); }
            }
            return false;
        }

        private void refresh() {
            long now = SystemClock.elapsedRealtime();
            if (now < nextCheck) return;
            try {
                remote = source.list();
                cache.reconcile(remote);
                deck.clear(); localDeck.clear();
                nextCheck = now + TimeUnit.MINUTES.toMillis(30);
                if (remote.isEmpty()) {
                    last = "";
                    slideshow.post(() -> {
                        if (active != this) return;
                        for (int i = 0; i < slideshow.getChildCount(); i++)
                            ((ImageView) slideshow.getChildAt(i)).setImageDrawable(null);
                    });
                }
            } catch (Exception e) {
                remote.clear(); deck.clear();
                nextCheck = now + TimeUnit.MINUTES.toMillis(1);
            }
        }

        @Override public void run() {
            int delay = 15;
            try {
                if (first) {
                    first = false;
                    if (showLocal()) return;
                }
                refresh();
                if (remote.isEmpty()) { showLocal(); return; }
                if (deck.isEmpty()) { deck = new ArrayList<>(remote); Collections.shuffle(deck); }
                int index = deck.size() - 1;
                if (index > 0 && deck.get(index).filename.equals(last)) index = 0;
                PhotoSource.Photo p = deck.remove(index);
                if (!p.filename.equals(last)) display(cache.load(source, p, width, height), p.filename);
            } catch (Exception e) {
                showLocal();
                delay = 5;
            } finally {
                if (!worker.isShutdown()) {
                    try { worker.schedule(this, delay, TimeUnit.SECONDS); }
                    catch (RejectedExecutionException stopped) { /* The dream just ended. */ }
                }
            }
        }
    }
}
