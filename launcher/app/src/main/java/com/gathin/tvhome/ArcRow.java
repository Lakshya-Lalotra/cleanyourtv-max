package com.gathin.tvhome;

/**
 * arc_rows (config.xml): the rows under the dock spread their tiles from edge to edge and lift the middle ones on a
 * shallow arc. A row keeps its tiles in order; only where each one is drawn changes (a translation), so the grid's
 * layout, focus order and edge stops are the plain grid's. Up and Down go to the tile drawn nearest above or below.
 */
final class ArcRow {
    private ArcRow() {
    }

    /** Tiles in the row that holds this position. */
    static int rowSize(int position, int count, int columns) {
        int start = position / columns * columns;
        return Math.max(0, Math.min(columns, count - start));
    }

    /**
     * The column, 0 to columns - 1 and possibly fractional, where the tile at this position is drawn. The dock row is
     * as laid out; a lone tile sits in the middle; two or more run from the first column to the last.
     */
    static float visualColumn(int position, int count, int columns) {
        int index = position % columns;
        if (position < columns) return index;
        int k = rowSize(position, count, columns);
        if (k <= 1) return (columns - 1) / 2f;
        return index * (columns - 1) / (float) (k - 1);
    }

    /** How far up the arc lifts a tile drawn at this column: 1 in the middle, 0 at both ends. */
    static float lift(float visualColumn, int columns) {
        if (columns <= 1) return 0f;
        float half = (columns - 1) / 2f;
        float u = (visualColumn - half) / half;
        return Math.max(0f, 1f - u * u);
    }

    /** The position in that row drawn nearest to the column; -1 when the row has no tiles. */
    static int nearestInRow(int row, float visualColumn, int count, int columns) {
        int start = row * columns;
        int k = rowSize(start, count, columns);
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < k; i++) {
            float d = Math.abs(visualColumn(start + i, count, columns) - visualColumn);
            if (d < bestDistance) {
                bestDistance = d;
                best = start + i;
            }
        }
        return best;
    }
}
