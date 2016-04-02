package org.mcmonkey.denizen2core.commands;

/**
 * Represents a section of a script.
 */
public class CommandScriptSection {

    public static CommandScriptSection forLine(String line) {
        CommandEntry[] cmds = new CommandEntry[1];
        cmds[0] = CommandEntry.forLine(line);
        return new CommandScriptSection(new CommandStackEntry(cmds));
    }

    public final CommandStackEntry created;

    public CommandScriptSection(CommandStackEntry entry) {
        created = entry;
    }

    public CommandQueue toQueue() {
        CommandQueue queue = new CommandQueue();
        CommandStackEntry stackEntry = created.clone();
        queue.commandStack.push(stackEntry);
        return queue;
    }
}