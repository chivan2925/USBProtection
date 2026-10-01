import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UsbGuardParserPoc {

    private static final Pattern HEADER =
            Pattern.compile("^(\\d+):\\s+(allow|block|reject)\\s+.*$");

    private static final Pattern VID_PID =
            Pattern.compile("\\bid\\s+([0-9a-fA-F]{4}):([0-9a-fA-F]{4})");

    private static final Pattern NAME =
            Pattern.compile("\\bname\\s+\"([^\"]*)\"");

    private static final Pattern SERIAL =
            Pattern.compile("\\bserial\\s+\"([^\"]*)\"");

    private static final Pattern HASH =
            Pattern.compile("\\bhash\\s+\"([^\"]*)\"");

    private static final Pattern MASS_STORAGE =
            Pattern.compile(
                "(?i)(?:^|[\\s{])08:[0-9a-f*]{2}:[0-9a-f*]{2}(?:[\\s}]|$)"
            );

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println(
                "Usage: java UsbGuardParserPoc <list-devices.txt>"
            );
            System.exit(1);
        }

        for (String line : Files.readAllLines(Path.of(args[0]))) {
            if (line.isBlank()) continue;

            Matcher header = HEADER.matcher(line);

            if (!header.matches()) {
                System.out.println("SKIP: " + line);
                continue;
            }

            String runtimeId = header.group(1);
            String state = header.group(2);
            String vid = find(VID_PID, line, 1);
            String pid = find(VID_PID, line, 2);
            String name = find(NAME, line, 1);
            String serial = find(SERIAL, line, 1);
            String hash = find(HASH, line, 1);
            boolean massStorage = MASS_STORAGE.matcher(line).find();

            System.out.println("--------------------------------");
            System.out.println("runtimeId   = " + runtimeId);
            System.out.println("state       = " + state);
            System.out.println("vid         = " + vid);
            System.out.println("pid         = " + pid);
            System.out.println("name        = " + name);
            System.out.println("serial      = " + serial);
            System.out.println("hash        = " + hash);
            System.out.println("massStorage = " + massStorage);
            System.out.println("raw         = " + line);
        }
    }

    private static String find(
            Pattern pattern,
            String text,
            int group
    ) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(group) : "";
    }
}
