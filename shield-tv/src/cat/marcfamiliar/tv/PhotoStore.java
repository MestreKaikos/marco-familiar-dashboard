package cat.marcfamiliar.tv;

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import java.io.*;
import java.util.*;

final class PhotoStore {
    private static final long BUDGET = 64L * 1024 * 1024;
    final File folder;

    PhotoStore(File root, String collection) throws IOException {
        folder = new File(root, collection);
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("No es poden desar les fotos a la SHIELD.");
    }

    List<File> cached() {
        File[] photos = folder.listFiles((dir, name) -> name.endsWith(".img"));
        return photos == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(photos));
    }

    void reconcile(List<PhotoSource.Photo> photos) {
        Set<String> keep = new HashSet<>();
        for (PhotoSource.Photo p : photos) keep.add(p.filename);
        for (File f : cached()) if (!keep.contains(f.getName())) f.delete();
    }

    Bitmap decode(File file, int width, int height) throws IOException {
        Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file), (decoder, info, src) -> {
            int w = info.getSize().getWidth(), h = info.getSize().getHeight();
            double scale = Math.min(1d, Math.min((double) width / w, (double) height / h));
            decoder.setTargetSize(Math.max(1, (int)(w * scale)), Math.max(1, (int)(h * scale)));
        });
        file.setLastModified(System.currentTimeMillis());
        return bitmap;
    }

    Bitmap load(PhotoSource source, PhotoSource.Photo photo, int width, int height) throws Exception {
        File dest = new File(folder, photo.filename);
        if (dest.isFile()) {
            try { return decode(dest, width, height); }
            catch (IOException broken) { dest.delete(); }
        }
        byte[] bytes = source.fetch(photo);
        File part = File.createTempFile("download-", ".part", folder);
        try {
            try (FileOutputStream out = new FileOutputStream(part)) { out.write(bytes); }
            Bitmap bitmap = decode(part, width, height);
            if (!part.renameTo(dest)) throw new IOException("No s’ha pogut guardar la foto.");
            List<File> files = cached();
            files.sort(Comparator.comparingLong(File::lastModified));
            long total = 0;
            for (File f : files) total += f.length();
            for (File f : files) {
                if (total <= BUDGET) break;
                long n = f.length();
                if (!f.equals(dest) && f.delete()) total -= n;
            }
            return bitmap;
        } finally { part.delete(); }
    }
}
