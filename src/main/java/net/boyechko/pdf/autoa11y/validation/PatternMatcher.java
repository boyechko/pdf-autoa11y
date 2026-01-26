/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.boyechko.pdf.autoa11y.validation;

import java.util.ArrayList;
import java.util.List;

public final class PatternMatcher {
    private static sealed interface Node
            permits PatternMatcher.Seq,
                    PatternMatcher.Atom,
                    PatternMatcher.Opt,
                    PatternMatcher.Star,
                    PatternMatcher.Plus,
                    PatternMatcher.Group {
        boolean match(List<String> input, int[] idx);
    }

    private static final class Seq implements Node {
        final List<Node> parts;

        Seq(List<Node> parts) {
            this.parts = parts;
        }

        public boolean match(List<String> in, int[] i) {
            int start = i[0];
            for (Node n : parts)
                if (!n.match(in, i)) {
                    i[0] = start;
                    return false;
                }
            return true;
        }
    }

    private static final class Atom implements Node {
        final String sym;

        Atom(String s) {
            this.sym = s;
        }

        public boolean match(List<String> in, int[] i) {
            if (i[0] < in.size() && in.get(i[0]).equals(sym)) {
                i[0]++;
                return true;
            }
            return false;
        }
    }

    private static final class Opt implements Node {
        final Node inner;

        Opt(Node n) {
            this.inner = n;
        }

        public boolean match(List<String> in, int[] i) {
            int save = i[0];
            if (!inner.match(in, i)) i[0] = save;
            return true;
        }
    }

    private static final class Star implements Node {
        final Node inner;

        Star(Node n) {
            this.inner = n;
        }

        public boolean match(List<String> in, int[] i) {
            while (inner.match(in, i)) {}
            return true;
        }
    }

    private static final class Plus implements Node {
        final Node inner;

        Plus(Node n) {
            this.inner = n;
        }

        public boolean match(List<String> in, int[] i) {
            if (!inner.match(in, i)) return false;
            while (inner.match(in, i)) {}
            return true;
        }
    }

    private static final class Group implements Node {
        final Node inner;

        Group(Node n) {
            this.inner = n;
        }

        public boolean match(List<String> in, int[] i) {
            return inner.match(in, i);
        }
    }

    private final Node root;

    private PatternMatcher(Node root) {
        this.root = root;
    }

    public static PatternMatcher compile(String pattern) {
        if (pattern == null || pattern.isBlank()) return null;
        return new PatternMatcher(new Parser(pattern).parse());
    }

    public boolean fullMatch(List<String> seq) {
        int[] i = {0};
        boolean ok = root.match(seq, i);
        return ok && i[0] == seq.size();
    }

    private static final class Parser {
        final List<String> toks;
        int p = 0;

        Parser(String s) {
            this.toks = lex(s);
        }

        Node parse() {
            List<Node> seq = new ArrayList<>();
            while (p < toks.size()) {
                String t = toks.get(p);
                if (")".equals(t)) break;
                Node n = parseAtom();
                while (p < toks.size()) {
                    String op = toks.get(p);
                    if ("?".equals(op)) {
                        p++;
                        n = new Opt(n);
                    } else if ("*".equals(op)) {
                        p++;
                        n = new Star(n);
                    } else if ("+".equals(op)) {
                        p++;
                        n = new Plus(n);
                    } else break;
                }
                seq.add(n);
            }
            return seq.size() == 1 ? seq.get(0) : new Seq(seq);
        }

        private Node parseAtom() {
            String t = toks.get(p++);
            if ("(".equals(t)) {
                Node inner = parse();
                expect(")");
                return new Group(inner);
            }
            return new Atom(t);
        }

        private void expect(String s) {
            if (p >= toks.size() || !toks.get(p).equals(s))
                throw new IllegalArgumentException("Expected '" + s + "'");
            p++;
        }

        private static List<String> lex(String s) {
            List<String> out = new ArrayList<>();
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isWhitespace(c)) {
                    flush(buf, out);
                    continue;
                }
                if ("()?*+".indexOf(c) >= 0) {
                    flush(buf, out);
                    out.add(String.valueOf(c));
                    continue;
                }
                buf.append(c);
            }
            flush(buf, out);
            return out;
        }

        private static void flush(StringBuilder b, List<String> o) {
            if (b.length() > 0) {
                o.add(b.toString());
                b.setLength(0);
            }
        }
    }
}
