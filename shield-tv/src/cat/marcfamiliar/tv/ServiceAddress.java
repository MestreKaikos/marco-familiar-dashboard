package cat.marcfamiliar.tv;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ServiceAddress {
    private static final String MOBILE = "https://marc-familiar-pwa.vercel.app";
    static final String RELAY = MOBILE + "/api/relay";
    private static final String SCRIPT = "https://script.google.com/macros/s/";
    private static final Pattern DEPLOYMENT = Pattern.compile(
            "(?i:(?:https://)?script\\.google\\.com)(?:/a/(?:macros/)?[A-Za-z0-9.-]+)?"
            + "/(?:macros/)?(?:u/[0-9]+/)?s/([A-Za-z0-9_-]+)/exec/?(?:[?#].*)?");

    static String clean(String input) {
        return input.replaceAll("[\\s\\p{Z}\\p{Cf}]+", "");
    }

    static String parse(String raw) throws IOException {
        String address = clean(raw);
        if (address.isEmpty() || address.equals(MOBILE) || address.equals(MOBILE + "/") || address.equals(RELAY))
            return RELAY;
        Matcher deployment = DEPLOYMENT.matcher(address);
        if (deployment.matches()) return SCRIPT + deployment.group(1) + "/exec";
        if (address.matches("AKfy[A-Za-z0-9_-]{12,}")) return SCRIPT + address + "/exec";
        throw new IOException("Deixa l’adreça buida per fer servir Calendari Familiar o enganxa la URL del desplegament de Google Apps Script.");
    }
}
