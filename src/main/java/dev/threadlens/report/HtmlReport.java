package dev.threadlens.report;

import dev.threadlens.analyze.Analyzer;
import dev.threadlens.model.DumpAnalysis;
import dev.threadlens.model.LockInfo;
import dev.threadlens.model.ThreadInfo;

import java.util.List;
import java.util.Map;

/**
 * Self-contained HTML report: no external assets, no network calls, no
 * absolute URLs — safe to serve from GitHub Pages or open from disk.
 */
public final class HtmlReport {
    private HtmlReport() {
    }

    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static String render(DumpAnalysis a, String sourceLabel) {
        String verdictClass = switch (a.verdict) {
            case "DEADLOCKED" -> "bad";
            case "CONTENTION", "BLOCKED" -> "warn";
            default -> "good";
        };
        StringBuilder out = new StringBuilder();
        out.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">");
        out.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        out.append("<title>ThreadLens report — ").append(escape(sourceLabel)).append("</title>");
        out.append("<style>");
        out.append("body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#0b1020;color:#e6e9f2;margin:0;padding:24px;line-height:1.5}");
        out.append("h1{font-size:26px;margin:0 0 4px}h2{font-size:18px;margin:28px 0 10px;color:#c7d0ea}");
        out.append(".sub{color:#9aa6c4;margin-bottom:20px}.cards{display:flex;gap:12px;flex-wrap:wrap}");
        out.append(".card{background:#141b33;border:1px solid #263154;border-radius:12px;padding:14px 18px;min-width:130px}");
        out.append(".card b{font-size:24px;display:block}.card span{color:#9aa6c4;font-size:13px}");
        out.append(".verdict{border-radius:12px;padding:14px 18px;font-weight:700;margin:16px 0}.verdict.bad{background:#3d1620;border:1px solid #e5484d;color:#ffb3b8}");
        out.append(".verdict.warn{background:#3a2a12;border:1px solid #e8a13c;color:#ffd9a0}.verdict.good{background:#123324;border:1px solid #46a758;color:#b6f0c4}");
        out.append("table{width:100%;border-collapse:collapse;background:#121830;border:1px solid #263154;border-radius:12px;overflow:hidden}");
        out.append("th,td{text-align:left;padding:8px 10px;border-bottom:1px solid #232c4d;font-size:14px;vertical-align:top}");
        out.append("th{color:#9aa6c4}code{font-size:13px;color:#ffd479;word-break:break-all}");
        out.append(".pill{display:inline-block;padding:1px 8px;border-radius:99px;font-size:12px;font-weight:600}");
        out.append(".BLOCKED{background:#3d1620;color:#ffb3b8}.RUNNABLE{background:#123324;color:#b6f0c4}");
        out.append(".WAITING,.TIMED_WAITING{background:#1d2a52;color:#b7c6ff}.UNKNOWN{background:#2a2f45;color:#c7cddf}");
        out.append("input,button{font:inherit}input{background:#0f1730;color:#e6e9f2;border:1px solid #263154;border-radius:8px;padding:8px 12px;width:100%;max-width:420px}");
        out.append(".filters{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}.filters button{background:#1a2347;color:#e6e9f2;border:1px solid #263154;border-radius:99px;padding:5px 14px;cursor:pointer}");
        out.append(".filters button.active{background:#2e5bff;border-color:#2e5bff}.frame{color:#9aa6c4;font-size:13px}");
        out.append(".footer{color:#9aa6c4;font-size:13px;margin-top:28px}");
        out.append("</style></head><body>");
        out.append("<h1>ThreadLens report</h1><div class=\"sub\">Source: ")
                .append(escape(sourceLabel)).append(" &middot; generated offline, no data leaves this page.</div>");

        out.append("<div class=\"cards\">");
        card(out, String.valueOf(a.total()), "threads");
        card(out, String.valueOf(a.daemonCount), "daemon");
        card(out, String.valueOf(a.blockedCount()), "blocked");
        card(out, String.valueOf(a.deadlockCycles.size()), "deadlock cycles");
        card(out, String.valueOf(Analyzer.longestChain(a)), "longest wait chain");
        out.append("</div>");

        out.append("<div class=\"verdict ").append(verdictClass).append("\">Verdict: ")
                .append(escape(a.verdict)).append("</div>");

        out.append("<h2>Thread states</h2><table><tr><th>State</th><th>Count</th></tr>");
        for (Map.Entry<String, Integer> e : a.stateCounts.entrySet()) {
            out.append("<tr><td><span class=\"pill ").append(escape(e.getKey())).append("\">")
                    .append(escape(e.getKey())).append("</span></td><td>").append(e.getValue()).append("</td></tr>");
        }
        out.append("</table>");

        if (a.hasDeadlock()) {
            out.append("<h2>Deadlock cycles</h2>");
            int i = 1;
            for (List<String> cycle : a.deadlockCycles) {
                out.append("<p><b>Cycle ").append(i++).append(":</b> ")
                        .append(escape(String.join(" &rarr; ", cycle)))
                        .append(" &rarr; ").append(escape(cycle.get(0))).append("</p>");
            }
        }

        if (!a.contendedLocks.isEmpty()) {
            out.append("<h2>Most contended locks</h2><table><tr><th>Lock</th><th>Class</th><th>Held by</th><th>Waiters</th></tr>");
            for (LockInfo lock : a.contendedLocks) {
                if (lock.waiters.isEmpty()) {
                    continue;
                }
                out.append("<tr><td><code>").append(escape(lock.id)).append("</code></td><td>")
                        .append(escape(lock.className)).append("</td><td>")
                        .append(escape(lock.holder == null ? "—" : lock.holder)).append("</td><td>")
                        .append(lock.waiters.size()).append(" — ")
                        .append(escape(String.join(", ", lock.waiters))).append("</td></tr>");
            }
            out.append("</table>");
        }

        if (!a.hotFrames.isEmpty()) {
            out.append("<h2>Hottest top frames</h2><table><tr><th>Threads</th><th>Top frame</th></tr>");
            for (Map.Entry<String, Integer> e : a.hotFrames) {
                out.append("<tr><td>").append(e.getValue()).append("</td><td><code>")
                        .append(escape(e.getKey())).append("</code></td></tr>");
            }
            out.append("</table>");
        }

        out.append("<h2>Threads</h2>");
        out.append("<input id=\"q\" placeholder=\"Search thread name or frame…\" oninput=\"filter()\">");
        out.append("<div class=\"filters\" id=\"filters\">");
        out.append("<button class=\"active\" data-state=\"ALL\" onclick=\"setState(this)\">All</button>");
        for (String state : a.stateCounts.keySet()) {
            out.append("<button data-state=\"").append(escape(state))
                    .append("\" onclick=\"setState(this)\">").append(escape(state)).append("</button>");
        }
        out.append("</div>");
        out.append("<table id=\"threads\"><tr><th>Thread</th><th>State</th><th>Waiting for</th><th>Top frame</th></tr>");
        for (ThreadInfo t : a.threads) {
            String holder = a.waitFor.get(t.name);
            out.append("<tr data-state=\"").append(escape(t.state)).append("\"><td>")
                    .append(escape(t.name)).append(t.daemon ? " <span class=\"pill UNKNOWN\">daemon</span>" : "")
                    .append("</td><td><span class=\"pill ").append(escape(t.state)).append("\">")
                    .append(escape(t.state)).append("</span></td><td>");
            if (t.waitingFor != null) {
                out.append("<code>").append(escape(t.waitingFor)).append("</code>");
                if (holder != null) {
                    out.append("<br>held by ").append(escape(holder));
                }
            } else {
                out.append("—");
            }
            out.append("</td><td class=\"frame\">").append(escape(t.topFrame())).append("</td></tr>");
        }
        out.append("</table>");
        out.append("<script>var current='ALL';function setState(b){current=b.dataset.state;"
                + "document.querySelectorAll('#filters button').forEach(function(x){x.classList.toggle('active',x===b)});filter();}"
                + "function filter(){var q=document.getElementById('q').value.toLowerCase();"
                + "document.querySelectorAll('#threads tr[data-state]').forEach(function(r){"
                + "var ok=(current==='ALL'||r.dataset.state===current)&&r.textContent.toLowerCase().includes(q);"
                + "r.style.display=ok?'':'none';});}</script>");
        out.append("<p class=\"footer\">ThreadLens — zero-dependency Java CLI. This report is a single self-contained file.</p>");
        out.append("</body></html>");
        return out.toString();
    }

    private static void card(StringBuilder out, String value, String label) {
        out.append("<div class=\"card\"><b>").append(escape(value)).append("</b><span>")
                .append(escape(label)).append("</span></div>");
    }
}
