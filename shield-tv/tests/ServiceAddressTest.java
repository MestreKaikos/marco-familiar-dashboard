package cat.marcfamiliar.tv;

import java.io.IOException;

public final class ServiceAddressTest {
    public static void main(String[] args) throws Exception {
        String base = "script.google.com", id = "AKfycb_Example-1234";
        String expected = "https://" + base + "/macros/s/" + id + "/exec";
        for (String raw : new String[]{expected, id, expected + "/", expected + "?usp=sharing",
                expected + "#fragment", expected + "\u200b\u00a0", base + "/macros/u/0/s/" + id + "/exec",
                "https://" + base + "/a/macros/example.org/s/" + id + "/exec"})
            check(expected.equals(ServiceAddress.parse(raw)));
        for (String raw : new String[]{"", "  ", ServiceAddress.RELAY, "https://marc-familiar-pwa.vercel.app/"})
            check(ServiceAddress.RELAY.equals(ServiceAddress.parse(raw)));
        for (String raw : new String[]{"https://wrong.test/macros/s/" + id + "/exec",
                "https://" + base + "@wrong.test/macros/s/" + id + "/exec",
                expected.replace("/exec", "/dev"), "https://" + base + "/home/projects/example/edit"})
            try { ServiceAddress.parse(raw); throw new AssertionError(raw); } catch (IOException expectedError) {}
        System.out.println("OK: adreces de fotos i servei de Calendari Familiar");
    }
    private static void check(boolean ok) { if (!ok) throw new AssertionError(); }
}
