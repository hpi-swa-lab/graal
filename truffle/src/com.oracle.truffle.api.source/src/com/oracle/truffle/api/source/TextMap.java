/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.source;

import java.util.ArrayList;

/**
 * A utility for converting between coordinate systems in a string of text interspersed with newline
 * characters. The coordinate systems are:
 * <ul>
 * <li>0-based character offset from the beginning of the text, where newline characters count as a
 * single character and the first character in the text occupies position 0.</li>
 * <li>1-based position in the 2D space of lines and columns, in which the first position in the
 * text is at (1,1).</li>
 * </ul>
 * <p>
 * This utility is based on positions occupied by characters, not text stream positions as in a text
 * editor. The distinction shows up in editors where you can put the cursor just past the last
 * character in a buffer; this is necessary, among other reasons, so that you can put the edit
 * cursor in a new (empty) buffer. For the purposes of this utility, however, there are no character
 * positions in an empty text string and there are no lines in an empty text string.
 * <p>
 * A newline character designates the end of a line and occupies a column position.
 * <p>
 * If the text ends with a character other than a newline, then the characters following the final
 * newline character count as a line, even though not newline-terminated.
 * <p>
 * <strong>Limitations:</strong>
 * <ul>
 * <li>Does not handle multiple character encodings correctly.</li>
 * <li>Treats tabs as occupying 1 column.</li>
 * <li>Does not handle multiple-character line termination sequences correctly.</li>
 * </ul>
 */
final class TextMap {

    // 0-based offsets of newline characters in the text, with sentinel
    private final int[] nlOffsets;
    // The number of characters in the text, including newlines (which count as 1).
    private final int textLength;
    // Is the final text character a newline?
    final boolean finalNL;

    TextMap(int[] nlOffsets, int textLength, boolean finalNL) {
        this.nlOffsets = nlOffsets;
        this.textLength = textLength;
        this.finalNL = finalNL;
    }

    /**
     * Constructs map permitting translation between 0-based character offsets and 1-based
     * lines/columns.
     */
    public static TextMap fromCharSequence(CharSequence text) {
        final int textLength = text.length();
        final ArrayList<Integer> lines = new ArrayList<>();
        lines.add(0);
        int offset = 0;
        while (offset < textLength) {
            final int nlIndex = indexOf(text, '\n', offset);
            if (nlIndex >= 0) {
                offset = nlIndex + 1;
                lines.add(offset);
            } else {
                break;
            }
        }
        lines.add(Integer.MAX_VALUE);
        final int[] nlOffsets = new int[lines.size()];
        for (int line = 0; line < lines.size(); line++) {
            nlOffsets[line] = lines.get(line);
        }
        final boolean finalNL = textLength > 0 && (textLength == nlOffsets[nlOffsets.length - 2]);
        return new TextMap(nlOffsets, textLength, finalNL);
    }

    private static int indexOf(CharSequence seq, int ch, int fromIndex) {
        if (seq instanceof String) {
            return ((String) seq).indexOf(ch, fromIndex);
        }
        final int max = seq.length();
        int localFromIndex = fromIndex;
        if (localFromIndex < 0) {
            localFromIndex = 0;
        } else if (fromIndex >= max) {
            // Note: fromIndex might be near -1>>>1.
            return -1;
        }

        if (ch >= Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            // not needed here
            throw new UnsupportedOperationException();
        }

        // handle most cases here (ch is a BMP code point or a
        // negative value (invalid code point))
        for (int i = localFromIndex; i < max; i++) {
            if (seq.charAt(i) == ch) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Converts 0-based character offset to 1-based number of the line containing the character.
     *
     * @throws IllegalArgumentException if the offset is outside the string.
     */
    public int offsetToLine(int offset) throws IllegalArgumentException {
        if (offset < 0 || offset >= textLength) {
            if (offset == 0 && textLength == 0) {
                return 1;
            }
            throw new IllegalArgumentException("offset out of bounds");
        }
        return binarySearchLine(nlOffsets, offset) + 1;
    }

    private static int binarySearchLine(int[] a, int key) {
        int low = 0;
        int high = a.length - 1;

        int mid = 0;
        int midVal;
        while (low <= high) {
            mid = (low + high) >>> 1;
            midVal = a[mid];

            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                high = mid;
                break; // direct hit
            }
        }
        return high;  // return high index
    }

    /**
     * Converts 0-based character offset to 1-based number of the column occupied by the character.
     * <p>
     * Tabs are not expanded; they occupy 1 column.
     *
     * @throws IllegalArgumentException if the offset is outside the string.
     */
    public int offsetToCol(int offset) throws IllegalArgumentException {
        return 1 + offset - nlOffsets[offsetToLine(offset) - 1];
    }

    /**
     * The number of characters in the mapped text.
     */
    public int length() {
        return textLength;
    }

    /**
     * The number of lines in the text; if characters appear after the final newline, then they also
     * count as a line, even though not newline-terminated.
     */
    public int lineCount() {
        if (textLength == 0) {
            return 0;
        }
        return finalNL ? nlOffsets.length - 2 : nlOffsets.length - 1;
    }

    /**
     * Converts 1-based line number to the 0-based offset of the line's first character; this would
     * be the offset of a newline if the line is empty.
     *
     * @throws IllegalArgumentException if there is no such line in the text.
     */
    public int lineStartOffset(int line) throws IllegalArgumentException {
        if (textLength == 0) {
            return 0;
        }
        if (lineOutOfRange(line)) {
            throw new IllegalArgumentException("line out of bounds");
        }
        return nlOffsets[line - 1];
    }

    /**
     * Gets the number of characters in a line, identified by 1-based line number; <em>does not</em>
     * include the final newline, if any.
     *
     * @throws IllegalArgumentException if there is no such line in the text.
     */
    public int lineLength(int line) throws IllegalArgumentException {
        if (textLength == 0) {
            return 0;
        }
        if (lineOutOfRange(line)) {
            throw new IllegalArgumentException("line out of bounds");
        }
        if (line == nlOffsets.length - 1 && !finalNL) {
            return textLength - nlOffsets[line - 1];
        }
        return (nlOffsets[line] - nlOffsets[line - 1]) - 1;
    }

    /**
     * Is the line number out of range.
     */
    private boolean lineOutOfRange(int line) {
        return line <= 0 || line >= nlOffsets.length || (line == nlOffsets.length - 1 && finalNL);
    }

}
