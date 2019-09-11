package com.iota.iri.utils;

/**
 * Utility class used to create ASCII progress bars.
 */
public class ASCIIProgressBar {

    private final static String filledBlock = "▓";
    private final static String emptyBlock = "░";

    /**
     * Computes an ASCII progress bar given the start, end and current value.
     * <p>
     * Example:
     * </p>
     * <p>
     * [▓▓▓▓▓▓▓▓▓▓▓▓░░░(40%)░░░░░░░░░░░░░░░]
     * </p>
     *
     * @param start   the start value of the range
     * @param end     the end value of the range
     * @param current the current value
     * @return an ASCII progress bar representing the current value
     */
    public static String getProgressBarString(int start, int end, int current) {
        int range = end - start;

        // compute actual percentage of current value
        double percentage = Math.floor(((double) (current - start) / (double) range) * 1000) / 1000;

        // how many progress blocks to print in the progress bar
        int progressBlocks = (int) (10 * percentage);
        StringBuilder progressBarSB = new StringBuilder(progressBlocks);
        progressBarSB.append("[");
        for (int i = 0; i < 10; i++) {
            boolean blockPrinted = false;
            if (progressBlocks > 0) {
                progressBarSB.append(filledBlock);
                blockPrinted = true;
            }
            progressBlocks--;
            if (i < 5) {
                if (!blockPrinted) {
                    progressBarSB.append(emptyBlock);
                }
                // print progress percentage in the middle of the progress bar
                if (i == 4) {
                    progressBarSB.append("%s");
                }
                continue;
            }
            if (!blockPrinted) {
                progressBarSB.append(emptyBlock);
            }
        }
        progressBarSB.append("]");

        return String.format(progressBarSB.toString(), "(" + ((int) (percentage * 100)) + "%)");
    }

}
