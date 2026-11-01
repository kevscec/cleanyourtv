package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class EditorialTest {
    @Test
    public void titleAndCreditSplitAtTheSeparator() {
        assertArrayEquals(new String[] {"Aurora over Abisko, Lapland", "Pavel Shyshkouski, CC BY-SA 4.0"},
                Editorial.split("Aurora over Abisko, Lapland · Pavel Shyshkouski, CC BY-SA 4.0"));
    }

    @Test
    public void aCaptionWithoutACreditIsAllTitle() {
        assertArrayEquals(new String[] {"Dusk over the peaks", ""}, Editorial.split("Dusk over the peaks "));
    }

    @Test
    public void onlyTheFirstSeparatorSplits() {
        assertArrayEquals(new String[] {"Skeppsbron", "Arild Vågen · CC BY-SA 2.0"},
                Editorial.split("Skeppsbron · Arild Vågen · CC BY-SA 2.0"));
    }

    @Test
    public void aMiddleDotInsideAWordDoesNotSplit() {
        assertArrayEquals(new String[] {"Cal·la", ""}, Editorial.split("Cal·la"));
    }
}
