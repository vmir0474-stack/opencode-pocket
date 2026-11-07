use std::io::{self, Read};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use ignore::{WalkBuilder, WalkState};
use nucleo_matcher::{Config, Matcher, Utf32Str};
use serde::Serialize;
use serde_json::{json, Value};
use similar::{ChangeTag, TextDiff};
use syntect::easy::HighlightLines;
use syntect::highlighting::FontStyle;
use syntect::util::LinesWithEndings;

const MAX_FILES: usize = 200_000;
const MAX_DIFF_LINES: usize = 6_000;
const MAX_DIFF_INPUT: usize = 2 * 1024 * 1024;
const MAX_HIGHLIGHT_LINES: usize = 4_000;
const MAX_HIGHLIGHT_BYTES: usize = 256 * 1024;

#[derive(Serialize)]
struct Hit {
    path: String,
    name: String,
    score: u32,
}

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let command = args.first().map(String::as_str).unwrap_or("");
    let result = match command {
        "version" | "--version" => {
            println!(
                "pocket-rs {} (syntect {})",
                env!("CARGO_PKG_VERSION"),
                "5"
            );
            return;
        }
        "find" => cmd_find(&args[1..]),
        "diff" => cmd_diff(),
        "highlight" => cmd_highlight(&args[1..]),
        "-h" | "--help" | "" => {
            println!("{}", usage());
            return;
        }
        other => Err(format!("unknown command: {other}")),
    };

    match result {
        Ok(value) => println!("{}", serde_json::to_string(&value).unwrap_or_else(|_| "{}".into())),
        Err(error) => {
            eprintln!("pocket-rs: {error}");
            println!(
                "{}",
                serde_json::to_string(&json!({ "error": error })).unwrap_or_else(|_| "{}".into())
            );
            std::process::exit(1);
        }
    }
}

fn usage() -> &'static str {
    "pocket-rs commands:\n\
     \x20 find --root DIR [--query Q] [--limit N]\n\
     \x20 diff < {\"old\":\"...\",\"new\":\"...\"}\n\
     \x20 highlight --lang LANG < code\n\
     \x20 version"
}

fn arg_value(args: &[String], name: &str) -> Option<String> {
    args.iter()
        .position(|arg| arg == name)
        .and_then(|index| args.get(index + 1))
        .cloned()
}

fn cmd_find(args: &[String]) -> Result<Value, String> {
    let root = arg_value(args, "--root").unwrap_or_else(|| ".".to_string());
    let query = arg_value(args, "--query")
        .or_else(|| arg_value(args, "--q"))
        .unwrap_or_default();
    let limit = arg_value(args, "--limit")
        .and_then(|value| value.parse::<usize>().ok())
        .unwrap_or(20)
        .clamp(1, 100);
    Ok(build_find(&root, &query, limit))
}

fn build_find(root: &str, query: &str, limit: usize) -> Value {
    let root_path = canonical_root(root);
    let files = collect_files(&root_path);
    let needle = query.trim();
    let hits = if needle.is_empty() {
        shallow_files(&files, limit)
    } else {
        match_files(&files, needle, limit)
    };
    json!({
        "root": root_path.display().to_string(),
        "total": files.len(),
        "files": hits,
    })
}

fn canonical_root(root: &str) -> PathBuf {
    let path = Path::new(root);
    std::fs::canonicalize(path).unwrap_or_else(|_| path.to_path_buf())
}

fn collect_files(root: &Path) -> Vec<String> {
    let files = Arc::new(Mutex::new(Vec::<String>::new()));
    let walker = WalkBuilder::new(root)
        .hidden(true)
        .git_ignore(true)
        .git_global(false)
        .git_exclude(true)
        .follow_links(false)
        .build_parallel();

    walker.run(|| {
        let files = Arc::clone(&files);
        let root = root.to_path_buf();
        Box::new(move |entry| {
            if let Ok(entry) = entry {
                if entry.file_type().map(|kind| kind.is_file()).unwrap_or(false) {
                    let mut guard = files.lock().unwrap();
                    if guard.len() < MAX_FILES {
                        if let Ok(relative) = entry.path().strip_prefix(&root) {
                            let path = relative.to_string_lossy().replace('\\', "/");
                            if !path.is_empty() {
                                guard.push(path);
                            }
                        }
                    }
                }
            }
            WalkState::Continue
        })
    });

    let guard = files.lock().unwrap();
    guard.clone()
}

fn shallow_files(files: &[String], limit: usize) -> Vec<Hit> {
    let mut sorted: Vec<&String> = files.iter().collect();
    sorted.sort_by(|a, b| {
        depth(a)
            .cmp(&depth(b))
            .then_with(|| a.len().cmp(&b.len()))
            .then_with(|| a.cmp(b))
    });
    sorted
        .into_iter()
        .take(limit)
        .map(|path| Hit {
            name: basename(path),
            path: path.clone(),
            score: 0,
        })
        .collect()
}

fn depth(path: &str) -> usize {
    path.bytes().filter(|byte| *byte == b'/').count()
}

fn basename(path: &str) -> String {
    path.rsplit('/').next().unwrap_or(path).to_string()
}

fn match_files(files: &[String], query: &str, limit: usize) -> Vec<Hit> {
    let mut matcher = Matcher::new(Config::DEFAULT.match_paths());
    let mut haystack = Vec::new();
    let mut needle = Vec::new();
    let mut hits = Vec::new();
    for path in files {
        let name = basename(path);
        let path_score = matcher
            .fuzzy_match(
                Utf32Str::new(path, &mut haystack),
                Utf32Str::new(query, &mut needle),
            )
            .unwrap_or(0) as u32;
        let name_score = matcher
            .fuzzy_match(
                Utf32Str::new(&name, &mut haystack),
                Utf32Str::new(query, &mut needle),
            )
            .map(|score| (score as u32).saturating_add(40))
            .unwrap_or(0);
        let score = path_score.max(name_score);
        if score > 0 {
            hits.push(Hit {
                path: path.clone(),
                name,
                score,
            });
        }
    }
    hits.sort_by(|a, b| {
        b.score
            .cmp(&a.score)
            .then_with(|| a.path.len().cmp(&b.path.len()))
            .then_with(|| a.path.cmp(&b.path))
    });
    hits.truncate(limit);
    hits
}

fn cmd_diff() -> Result<Value, String> {
    let mut input = String::new();
    io::stdin()
        .read_to_string(&mut input)
        .map_err(|error| format!("stdin: {error}"))?;
    if input.len() > MAX_DIFF_INPUT {
        return Err("diff input is too large".into());
    }
    let value: Value =
        serde_json::from_str(&input).map_err(|error| format!("bad json: {error}"))?;
    let old = value.get("old").and_then(Value::as_str).unwrap_or("");
    let new = value.get("new").and_then(Value::as_str).unwrap_or("");
    Ok(build_diff(old, new))
}

#[derive(Serialize)]
struct DiffLine {
    k: &'static str,
    o: u32,
    n: u32,
    t: String,
}

fn build_diff(old: &str, new: &str) -> Value {
    let diff = TextDiff::from_lines(old, new);
    let mut lines: Vec<DiffLine> = Vec::new();
    let mut additions = 0u64;
    let mut deletions = 0u64;
    let mut truncated = false;

    'groups: for group in diff.grouped_ops(3) {
        let old_start = group
            .first()
            .map(|op| op.old_range().start)
            .unwrap_or(0);
        let new_start = group
            .first()
            .map(|op| op.new_range().start)
            .unwrap_or(0);
        let old_len: usize = group.iter().map(|op| op.old_range().len()).sum();
        let new_len: usize = group.iter().map(|op| op.new_range().len()).sum();

        if lines.len() >= MAX_DIFF_LINES {
            truncated = true;
            break;
        }
        lines.push(DiffLine {
            k: "h",
            o: 0,
            n: 0,
            t: format!(
                "@@ -{},{} +{},{} @@",
                old_start + 1,
                old_len,
                new_start + 1,
                new_len
            ),
        });

        let mut old_line = old_start as u32 + 1;
        let mut new_line = new_start as u32 + 1;

        for op in &group {
            for change in diff.iter_changes(op) {
                if lines.len() >= MAX_DIFF_LINES {
                    truncated = true;
                    break 'groups;
                }
                let text = change
                    .value()
                    .trim_end_matches(|character| character == '\n' || character == '\r')
                    .to_string();
                match change.tag() {
                    ChangeTag::Equal => {
                        lines.push(DiffLine {
                            k: "c",
                            o: old_line,
                            n: new_line,
                            t: text,
                        });
                        old_line += 1;
                        new_line += 1;
                    }
                    ChangeTag::Delete => {
                        deletions += 1;
                        lines.push(DiffLine {
                            k: "d",
                            o: old_line,
                            n: 0,
                            t: text,
                        });
                        old_line += 1;
                    }
                    ChangeTag::Insert => {
                        additions += 1;
                        lines.push(DiffLine {
                            k: "a",
                            o: 0,
                            n: new_line,
                            t: text,
                        });
                        new_line += 1;
                    }
                }
            }
        }
    }

    json!({
        "additions": additions,
        "deletions": deletions,
        "truncated": truncated,
        "lines": lines,
    })
}

fn cmd_highlight(args: &[String]) -> Result<Value, String> {
    let language = arg_value(args, "--lang").unwrap_or_default();
    let mut code = String::new();
    io::stdin()
        .read_to_string(&mut code)
        .map_err(|error| format!("stdin: {error}"))?;
    if code.len() > MAX_HIGHLIGHT_BYTES {
        let mut boundary = MAX_HIGHLIGHT_BYTES;
        while boundary > 0 && !code.is_char_boundary(boundary) {
            boundary -= 1;
        }
        code.truncate(boundary);
    }
    Ok(highlight_code(&code, &language))
}

fn highlight_code(code: &str, language: &str) -> Value {
    let syntaxes = two_face::syntax::extra_newlines();
    let themes = two_face::theme::extra();
    let theme = &themes[two_face::theme::EmbeddedThemeName::CatppuccinMocha];

    let syntax = if language.is_empty() {
        syntaxes.find_syntax_plain_text()
    } else {
        syntaxes
            .find_syntax_by_token(language)
            .or_else(|| syntaxes.find_syntax_by_extension(language))
            .unwrap_or_else(|| syntaxes.find_syntax_plain_text())
    };

    let mut highlighter = HighlightLines::new(syntax, theme);
    let mut output: Vec<Vec<Value>> = Vec::new();
    let mut truncated = false;

    for line in LinesWithEndings::from(code) {
        if output.len() >= MAX_HIGHLIGHT_LINES {
            truncated = true;
            break;
        }
        let ranges = match highlighter.highlight_line(line, &syntaxes) {
            Ok(ranges) => ranges,
            Err(_) => break,
        };

        let mut spans: Vec<Value> = Vec::new();
        let mut cursor: u32 = 0;
        for (style, text) in ranges {
            let clean = text
                .trim_end_matches('\n')
                .trim_end_matches('\r');
            let length = clean.chars().count() as u32;
            if length == 0 {
                continue;
            }
            let color = style.foreground;
            spans.push(json!({
                "s": cursor,
                "e": cursor + length,
                "c": format!("#{:02x}{:02x}{:02x}", color.r, color.g, color.b),
                "b": style.font_style.contains(FontStyle::BOLD),
                "i": style.font_style.contains(FontStyle::ITALIC),
            }));
            cursor += length;
        }
        output.push(spans);
    }

    json!({
        "lines": output,
        "truncated": truncated,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn diff_reports_stats_and_lines() {
        let result = build_diff("one\ntwo\nthree\n", "one\nTWO\nthree\nfour\n");
        assert_eq!(result["additions"], 2);
        assert_eq!(result["deletions"], 1);
        let lines = result["lines"].as_array().unwrap();
        assert!(lines.iter().any(|line| line["k"] == "a" && line["t"] == "TWO"));
        assert!(lines.iter().any(|line| line["k"] == "d" && line["t"] == "two"));
        assert!(lines.iter().any(|line| line["k"] == "h"));
    }

    #[test]
    fn diff_handles_empty_sides() {
        let added = build_diff("", "a\nb\n");
        assert_eq!(added["additions"], 2);
        assert_eq!(added["deletions"], 0);
        let removed = build_diff("a\nb\n", "");
        assert_eq!(removed["additions"], 0);
        assert_eq!(removed["deletions"], 2);
    }

    #[test]
    fn find_ranks_matching_file() {
        let dir = std::env::temp_dir().join(format!("pocket-rs-find-{}", std::process::id()));
        std::fs::create_dir_all(dir.join("src")).unwrap();
        std::fs::write(dir.join("src/main.kt"), "fun main() {}\n").unwrap();
        std::fs::write(dir.join("src/other.kt"), "class Other\n").unwrap();
        std::fs::write(dir.join("README.md"), "# hi\n").unwrap();

        let result = build_find(dir.to_str().unwrap(), "main", 10);
        let files = result["files"].as_array().unwrap();
        assert!(files.iter().any(|file| file["path"] == "src/main.kt"));

        let empty = build_find(dir.to_str().unwrap(), "", 10);
        assert!(!empty["files"].as_array().unwrap().is_empty());

        std::fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn highlight_returns_spans() {
        let result = highlight_code("fun main() {\n    println(\"hi\")\n}\n", "kt");
        let lines = result["lines"].as_array().unwrap();
        assert_eq!(lines.len(), 3);
        let first = lines[0].as_array().unwrap();
        assert!(first.len() >= 3, "kotlin syntax should produce several spans");
        assert!(first.iter().any(|span| span["c"] != "#c0c5ce"));
    }
}
