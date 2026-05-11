package com.github.rzo1.bloodfields.ai;

import com.github.rzo1.bloodfields.engine.StructureField;
import com.github.rzo1.bloodfields.engine.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public final class Pathfinding {

    private static final double SQRT2 = Math.sqrt(2.0);
    public static final int MAX_EXPANSIONS = 800;

    private Pathfinding() {}

    public static List<double[]> findPath(World w, double sx, double sy, double tx, double ty) {
        return findPath(w, null, sx, sy, tx, ty);
    }

    public static List<double[]> findPath(World w, StructureField structures,
                                          double sx, double sy, double tx, double ty) {
        if (w == null) {
            return null;
        }
        int cols = w.cols();
        int rows = w.rows();
        if (cols == 0 || rows == 0) {
            return null;
        }
        int sCol = clamp((int) (sx / w.tileSize), 0, cols - 1);
        int sRow = clamp((int) (sy / w.tileSize), 0, rows - 1);
        int tCol = clamp((int) (tx / w.tileSize), 0, cols - 1);
        int tRow = clamp((int) (ty / w.tileSize), 0, rows - 1);

        if (sCol == tCol && sRow == tRow) {
            return Collections.emptyList();
        }
        if (!tilePassable(w, structures, tCol, tRow)) {
            return null;
        }
        if (!tilePassable(w, structures, sCol, sRow)) {
            int[] nearest = findNearestPassable(w, structures, sCol, sRow);
            if (nearest == null) {
                return null;
            }
            sCol = nearest[0];
            sRow = nearest[1];
            if (sCol == tCol && sRow == tRow) {
                return Collections.emptyList();
            }
        }

        HashMap<Long, Node> nodes = new HashMap<>();
        MinHeap open = new MinHeap();

        Node start = new Node(sCol, sRow);
        start.g = 0.0;
        start.f = octile(sCol, sRow, tCol, tRow);
        nodes.put(packKey(sCol, sRow), start);
        open.push(start);

        int[][] dirs = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        int expansions = 0;
        while (!open.isEmpty()) {
            Node cur = open.pop();
            if (cur.closed) continue;
            cur.closed = true;
            if (++expansions > MAX_EXPANSIONS) {
                return null;
            }

            if (cur.col == tCol && cur.row == tRow) {
                return reconstruct(cur, w);
            }

            for (int[] d : dirs) {
                int nc = cur.col + d[0];
                int nr = cur.row + d[1];
                if (nc < 0 || nr < 0 || nc >= cols || nr >= rows) continue;
                if (!tilePassable(w, structures, nc, nr)) continue;
                if (d[0] != 0 && d[1] != 0) {
                    if (!tilePassable(w, structures, cur.col + d[0], cur.row) ||
                            !tilePassable(w, structures, cur.col, cur.row + d[1])) {
                        continue;
                    }
                }
                double step = (d[0] != 0 && d[1] != 0) ? SQRT2 : 1.0;
                double tentativeG = cur.g + step;
                long nk = packKey(nc, nr);
                Node neighbor = nodes.get(nk);
                if (neighbor == null) {
                    neighbor = new Node(nc, nr);
                    nodes.put(nk, neighbor);
                }
                if (neighbor.closed) continue;
                if (tentativeG < neighbor.g) {
                    neighbor.parent = cur;
                    neighbor.g = tentativeG;
                    neighbor.f = tentativeG + octile(nc, nr, tCol, tRow);
                    if (neighbor.heapIdx >= 0) {
                        open.decreaseKey(neighbor);
                    } else {
                        open.push(neighbor);
                    }
                }
            }
        }
        return null;
    }

    private static List<double[]> reconstruct(Node end, World w) {
        ArrayList<double[]> path = new ArrayList<>();
        Node cur = end;
        while (cur != null) {
            double cx = (cur.col + 0.5) * w.tileSize;
            double cy = (cur.row + 0.5) * w.tileSize;
            path.add(new double[]{cx, cy});
            cur = cur.parent;
        }
        Collections.reverse(path);
        if (!path.isEmpty()) {
            path.remove(0);
        }
        return path;
    }

    public static int[] findNearestPassable(World w, int col, int row) {
        return findNearestPassable(w, null, col, row);
    }

    public static int[] findNearestPassable(World w, StructureField structures, int col, int row) {
        if (w == null) return null;
        int cols = w.cols();
        int rows = w.rows();
        if (col < 0 || row < 0 || col >= cols || row >= rows) return null;
        if (tilePassable(w, structures, col, row)) return new int[]{col, row};
        int maxRing = Math.max(cols, rows);
        for (int ring = 1; ring <= maxRing; ring++) {
            int minC = col - ring;
            int maxC = col + ring;
            int minR = row - ring;
            int maxR = row + ring;
            for (int c = minC; c <= maxC; c++) {
                for (int r = minR; r <= maxR; r++) {
                    if (c != minC && c != maxC && r != minR && r != maxR) continue;
                    if (c < 0 || r < 0 || c >= cols || r >= rows) continue;
                    if (tilePassable(w, structures, c, r)) {
                        return new int[]{c, r};
                    }
                }
            }
        }
        return null;
    }

    private static boolean tilePassable(World w, int col, int row) {
        return tilePassable(w, null, col, row);
    }

    private static boolean tilePassable(World w, StructureField structures, int col, int row) {
        if (!w.terrain[col][row].passable()) return false;
        if (structures == null) return true;
        double cx = (col + 0.5) * w.tileSize;
        double cy = (row + 0.5) * w.tileSize;
        return !structures.blocksMovement(cx, cy);
    }

    private static double octile(int ax, int ay, int bx, int by) {
        int dx = Math.abs(ax - bx);
        int dy = Math.abs(ay - by);
        int min = Math.min(dx, dy);
        int max = Math.max(dx, dy);
        return (max - min) + SQRT2 * min;
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static long packKey(int col, int row) {
        return (((long) col) << 32) | (row & 0xffffffffL);
    }

    private static final class Node {
        final int col;
        final int row;
        Node parent;
        double g = Double.POSITIVE_INFINITY;
        double f = Double.POSITIVE_INFINITY;
        int heapIdx = -1;
        boolean closed;

        Node(int col, int row) {
            this.col = col;
            this.row = row;
        }
    }

    private static final class MinHeap {
        private Node[] heap = new Node[64];
        private int size;

        boolean isEmpty() {
            return size == 0;
        }

        void push(Node n) {
            if (size == heap.length) {
                Node[] grown = new Node[heap.length * 2];
                System.arraycopy(heap, 0, grown, 0, size);
                heap = grown;
            }
            int i = size++;
            heap[i] = n;
            n.heapIdx = i;
            siftUp(i);
        }

        Node pop() {
            Node top = heap[0];
            top.heapIdx = -1;
            int last = --size;
            if (last == 0) {
                heap[0] = null;
                return top;
            }
            Node moved = heap[last];
            heap[last] = null;
            heap[0] = moved;
            moved.heapIdx = 0;
            siftDown(0);
            return top;
        }

        void decreaseKey(Node n) {
            siftUp(n.heapIdx);
        }

        private void siftUp(int i) {
            Node x = heap[i];
            while (i > 0) {
                int parent = (i - 1) >>> 1;
                Node p = heap[parent];
                if (x.f >= p.f) break;
                heap[i] = p;
                p.heapIdx = i;
                i = parent;
            }
            heap[i] = x;
            x.heapIdx = i;
        }

        private void siftDown(int i) {
            Node x = heap[i];
            int half = size >>> 1;
            while (i < half) {
                int child = (i << 1) + 1;
                int right = child + 1;
                Node c = heap[child];
                if (right < size && heap[right].f < c.f) {
                    child = right;
                    c = heap[right];
                }
                if (x.f <= c.f) break;
                heap[i] = c;
                c.heapIdx = i;
                i = child;
            }
            heap[i] = x;
            x.heapIdx = i;
        }
    }
}
