package cat.marcfamiliar.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

final class PhotoSource {
    final String endpoint, key;
    PhotoSource(String endpoint, String key) throws IOException {
        this.endpoint = endpoint.trim();
        this.key = key.trim();
        if (!this.endpoint.matches("https://script\\.google\\.com/macros/s/[A-Za-z0-9_-]+/exec"))
            throw new IOException("Cal l’adreça de Google Apps Script acabada en /exec.");
        if (!this.key.matches("[A-Fa-f0-9]{64}"))
            throw new IOException("La clau familiar ha de tenir 64 caràcters.");
    }

    static final class Photo {
        final String id, filename;
        Photo(String id, String revision) throws Exception {
            this.id = id;
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((id + ":" + revision).getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte b : hash) name.append(String.format(Locale.ROOT, "%02x", b & 255));
            filename = name + ".img";
        }
    }

    List<Photo> list() throws Exception {
        JSONArray rows = call("photo_list", new JSONObject(), 512 * 1024).getJSONArray("photos");
        List<Photo> result = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String id = row.getString("id");
            if (id.matches("[A-Za-z0-9_-]{1,100}"))
                result.add(new Photo(id, row.optString("updatedAt", row.optString("createdAt"))));
        }
        return result;
    }

    byte[] fetch(Photo p) throws Exception {
        JSONObject photo = call("photo_get", new JSONObject().put("id", p.id), 7200000).getJSONObject("photo");
        if (!p.id.equals(photo.getString("id"))) throw new IOException("La resposta no correspon a la foto.");
        byte[] bytes = Base64.getDecoder().decode(photo.getString("base64"));
        if (bytes.length < 6 || bytes.length > 5 * 1024 * 1024) throw new IOException("Mida de foto invàlida.");
        return bytes;
    }

    private JSONObject call(String action, JSONObject payload, int limit) throws Exception {
        URL url = new URL(endpoint);
        byte[] request = new JSONObject().put("key", key).put("action", action).put("payload", payload)
                .toString().getBytes(StandardCharsets.UTF_8);
        for (int step = 0; step < 4; step++) {
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            try {
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(18000);
                if (step == 0) {
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    try (OutputStream out = conn.getOutputStream()) { out.write(request); }
                }
                int status = conn.getResponseCode();
                if (status == 301 || status == 302 || status == 303) {
                    // Apps Script returns its result via a GET to Google ContentService.
                    String location = conn.getHeaderField("Location");
                    if (location == null) throw new IOException("La redirecció del servei és incompleta.");
                    url = new URL(url, location);
                    if (!"https".equals(url.getProtocol()) || !"script.googleusercontent.com".equals(url.getHost())
                            || url.getUserInfo() != null || (url.getPort() != -1 && url.getPort() != 443))
                        throw new IOException("Comprova que la URL sigui la del desplegament familiar.");
                    continue;
                }
                if (status != 200) throw new IOException("El servei de fotos ha respost amb HTTP " + status + ".");
                byte[] block = new byte[16384];
                try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    int count;
                    while ((count = in.read(block)) != -1) {
                        if (out.size() + count > limit) throw new IOException("La resposta de fotos és massa gran.");
                        out.write(block, 0, count);
                    }
                    JSONObject response = new JSONObject(out.toString("UTF-8"));
                    if (!response.optBoolean("ok"))
                        throw new IOException("El servei no ha acceptat la petició. Comprova la clau familiar.");
                    return response;
                }
            } finally { conn.disconnect(); }
        }
        throw new IOException("El servei de fotos ha retornat massa redireccions.");
    }
}
