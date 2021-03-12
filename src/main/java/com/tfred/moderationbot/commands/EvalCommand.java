package com.tfred.moderationbot.commands;

import net.dv8tion.jda.api.Permission;

import javax.annotation.Nonnull;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class EvalCommand extends Command {
    public EvalCommand() {
        super(
                "eval",
                new String[]{},
                "!eval <javascript>",
                "Evaluates the given javascript.",
                new Permission[]{},
                true,
                false,
                false
        );
    }

    @Override
    protected void execute(@Nonnull CommandEvent event) {
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("Nashorn");

            engine.put("event", event);

            Object result = engine.eval(event.message.substring(6));
            if (result == null)
                event.channel.sendMessage("null").queue();
            else
                event.channel.sendMessage(result.toString()).queue();
        } catch (Exception e) {
            event.channel.sendMessage(e.getMessage()).queue();
        }
    }
}
