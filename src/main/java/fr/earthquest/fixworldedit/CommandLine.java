package fr.earthquest.fixworldedit;

final class CommandLine {
    private static final int MAX_DIGITS_IN_INT = 9;

    private final String label;
    private final String arguments;

    private CommandLine(String label, String arguments) {
        this.label = label;
        this.arguments = arguments;
    }

    static CommandLine of(String message) {
        String line = message.trim();
        while (line.startsWith("/")) {
            line = line.substring(1);
        }
        int space = line.indexOf(' ');
        String label = space < 0 ? line : line.substring(0, space);
        String arguments = space < 0 ? "" : line.substring(space + 1).trim();

        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        while (label.startsWith("/")) {
            label = label.substring(1);
        }
        return new CommandLine(label.toLowerCase(), arguments);
    }

    boolean is(String name) {
        return label.equals(name);
    }

    String arguments() {
        return arguments;
    }

    boolean hasArguments() {
        return !arguments.isEmpty();
    }

    boolean mentionsExtendedId() {
        int index = 0;
        while (index < arguments.length()) {
            if (!Character.isDigit(arguments.charAt(index))) {
                index++;
                continue;
            }
            int end = index;
            while (end < arguments.length() && Character.isDigit(arguments.charAt(end))) {
                end++;
            }
            String digits = arguments.substring(index, end);
            if (digits.length() > MAX_DIGITS_IN_INT
                    || Integer.parseInt(digits) > ServerBlockRegistry.VANILLA_MAX_ID) {
                return true;
            }
            index = end;
        }
        return false;
    }

    int[] parseIdAndData() {
        if (arguments.isEmpty() || arguments.indexOf(' ') >= 0) {
            return null;
        }
        String idPart = arguments;
        String dataPart = null;
        int colon = arguments.indexOf(':');
        if (colon >= 0) {
            idPart = arguments.substring(0, colon);
            dataPart = arguments.substring(colon + 1);
        }
        int id = positiveInt(idPart);
        if (id < 0) {
            return null;
        }
        int data = 0;
        if (dataPart != null) {
            data = positiveInt(dataPart);
            if (data < 0 || data > 15) {
                return null;
            }
        }
        return new int[] {id, data};
    }

    private static int positiveInt(String text) {
        if (text.isEmpty() || text.length() > MAX_DIGITS_IN_INT) {
            return -1;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return -1;
            }
        }
        return Integer.parseInt(text);
    }
}
