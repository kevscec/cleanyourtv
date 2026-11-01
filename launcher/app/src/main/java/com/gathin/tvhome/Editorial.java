package com.gathin.tvhome;

/** editorial_home: a wallpaper caption split into the picture's title and its credit. */
final class Editorial {
    private Editorial() {}

    /** What captions.txt puts between a picture's name and its credit. */
    static final String SEPARATOR = " · ";

    /**
     * "Aurora over Abisko, Lapland · Pavel Shyshkouski, CC BY-SA 4.0" is {title, credit}; a caption without the
     * separator is all title, with an empty credit. Only the first separator splits: a credit may contain more.
     */
    static String[] split(String caption) {
        int at = caption.indexOf(SEPARATOR);
        if (at < 0) return new String[] {caption.trim(), ""};
        return new String[] {caption.substring(0, at).trim(), caption.substring(at + SEPARATOR.length()).trim()};
    }
}
