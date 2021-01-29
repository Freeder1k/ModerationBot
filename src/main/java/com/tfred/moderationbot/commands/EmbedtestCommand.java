package com.tfred.moderationbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

public class EmbedtestCommand extends Command {
    public EmbedtestCommand() {
        super(
                "embedtest",
                new String[]{},
                "!embedtest <string>",
                "Test embed colors with a specified string.",
                new Permission[]{},
                true,
                false,
                false
        );
    }

    @Override
    protected void execute(CommandEvent event) {
        String text = event.message.substring(6);
        String[] langs = {"1c", "abnf", "accesslog", "actionscript", "ada", "angelscript", "apache", "applescript", "arcade", "arduino", "armasm", "asciidoc", "aspectj", "autohotkey", "autoit", "avrasm", "awk", "axapta", "bash", "basic", "bnf", "brainfuck", "c-like", "c", "cal", "capnproto", "ceylon", "clean", "clojure-repl", "clojure", "cmake", "coffeescript", "coq", "cos", "cpp", "crmsh", "crystal", "csharp", "csp", "css", "d", "dart", "delphi", "diff", "django", "dns", "dockerfile", "dos", "dsconfig", "dts", "dust", "ebnf", "elixir", "elm", "erb", "erlang-repl", "erlang", "excel", "fix", "flix", "fortran", "fsharp", "gams", "gauss", "gcode", "gherkin", "glsl", "gml", "go", "golo", "gradle", "groovy", "haml", "handlebars", "haskell", "haxe", "hsp", "htmlbars", "http", "hy", "inform7", "ini", "irpf90", "isbl", "java", "javascript", "jboss-cli", "json", "julia-repl", "julia", "kotlin", "lasso", "latex", "ldif", "leaf", "less", "lisp", "livecodeserver", "livescript", "llvm", "lsl", "lua", "makefile", "markdown", "mathematica", "matlab", "maxima", "mel", "mercury", "mipsasm", "mizar", "mojolicious", "monkey", "moonscript", "n1ql", "nginx", "nim", "nix", "nsis", "objectivec", "ocaml", "openscad", "oxygene", "parser3", "perl", "pf", "pgsql", "php-template", "php", "plaintext", "pony", "powershell", "processing", "profile", "prolog", "properties", "protobuf", "puppet", "purebasic", "python-repl", "python", "q", "qml", "r", "reasonml", "rib", "roboconf", "routeros", "rsl", "ruby", "ruleslanguage", "rust", "sas", "scala", "scheme", "scilab", "scss", "shell", "smali", "smalltalk", "sml", "sqf", "sql", "stan", "stata", "step21", "stylus", "subunit", "swift", "taggerscript", "tap", "tcl", "thrift", "tp", "twig", "typescript", "vala", "vbnet", "vbscript-html", "vbscript", "verilog", "vhdl", "vim", "x86asm", "xl", "xml", "xquery", "yaml", "zephir.js"};

        String[] testMessage = {"", "", "", "", "", "", "", "", "", "", "", ""};
        int i = 0;

        for (String lang : langs) {
            String current = lang + "```" + lang + '\n' + text + "```";
            if (testMessage[i].length() + current.length() > 999)
                i++;
            if (i > 11) {
                i--;
                break;
            }
            testMessage[i] += current;
        }
        EmbedBuilder response = new EmbedBuilder();

        response.setTitle("Test:");
        for (int c = 0; c <= i && c < 6; c++)
            response.addField("", testMessage[c], true);
        event.channel.sendMessage(response.build()).queue();
        if (i > 5) {
            EmbedBuilder response2 = new EmbedBuilder().setTitle("Test:");
            for (int c = 6; c <= i; c++)
                response2.addField("", testMessage[c], true);
            event.channel.sendMessage(response2.build()).queue();
        }
    }
}
