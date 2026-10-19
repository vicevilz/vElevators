package net.velevators.menu;

import java.util.List;
import java.util.Map;

public final class DeluxeMenusConfigEditor {
    private DeluxeMenusConfigEditor() {
    }

    public static String upsertMenus(String original, Map<String, String> requestedMenus) {
        String normalized = original == null ? "" : original.replace("\r\n", "\n");
        List<String> lines = new java.util.ArrayList<>(List.of(normalized.split("\n", -1)));
        if (lines.size() == 1 && lines.get(0).isEmpty()) {
            lines.clear();
        }
        int guiIndex = findGuiMenus(lines);
        if (guiIndex < 0) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) {
                lines.add("");
            }
            lines.add("gui_menus:");
            guiIndex = lines.size() - 1;
        }

        for (Map.Entry<String, String> requested : requestedMenus.entrySet()) {
            int sectionEnd = findSectionEnd(lines, guiIndex);
            int menuIndex = findMenu(lines, guiIndex, sectionEnd, requested.getKey());
            if (menuIndex < 0) {
                lines.add(sectionEnd, "  " + requested.getKey() + ":");
                lines.add(sectionEnd + 1, "    file: " + requested.getValue());
                continue;
            }

            int menuEnd = findMenuEnd(lines, menuIndex, findSectionEnd(lines, guiIndex));
            int fileIndex = -1;
            for (int index = menuIndex + 1; index < menuEnd; index++) {
                if (lines.get(index).matches("^\\s{4}file\\s*:.*$")) {
                    fileIndex = index;
                    break;
                }
            }
            String expected = "    file: " + requested.getValue();
            if (fileIndex < 0) {
                lines.add(menuIndex + 1, expected);
            } else if (!lines.get(fileIndex).equals(expected)) {
                lines.set(fileIndex, expected);
            }
        }
        return String.join("\n", lines);
    }

    private static int findGuiMenus(List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).matches("^gui_menus\\s*:\\s*(?:#.*)?$")) {
                return index;
            }
        }
        return -1;
    }

    private static int findSectionEnd(List<String> lines, int guiIndex) {
        for (int index = guiIndex + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!line.isBlank() && !line.stripLeading().startsWith("#")
                    && !Character.isWhitespace(line.charAt(0))) {
                return index;
            }
        }
        return lines.size();
    }

    private static int findMenu(List<String> lines, int guiIndex, int sectionEnd, String menuId) {
        String pattern = "^\\s{2}" + java.util.regex.Pattern.quote(menuId) + "\\s*:\\s*(?:#.*)?$";
        for (int index = guiIndex + 1; index < sectionEnd; index++) {
            if (lines.get(index).matches(pattern)) {
                return index;
            }
        }
        return -1;
    }

    private static int findMenuEnd(List<String> lines, int menuIndex, int sectionEnd) {
        for (int index = menuIndex + 1; index < sectionEnd; index++) {
            if (lines.get(index).matches("^\\s{2}\\S.*$")) {
                return index;
            }
        }
        return sectionEnd;
    }
}
