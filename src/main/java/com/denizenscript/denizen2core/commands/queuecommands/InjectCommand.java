package com.denizenscript.denizen2core.commands.queuecommands;

import com.denizenscript.denizen2core.Denizen2Core;
import com.denizenscript.denizen2core.commands.*;
import com.denizenscript.denizen2core.scripts.commontypes.TaskScript;
import com.denizenscript.denizen2core.tags.AbstractTagObject;
import com.denizenscript.denizen2core.utilities.debugging.ColorSet;
import com.denizenscript.denizen2core.scripts.CommandScript;
import com.denizenscript.denizen2core.utilities.CoreUtilities;

import java.util.List;

public class InjectCommand extends AbstractCommand {

    // <--[command]
    // @Since 0.3.0
    // @Name inject
    // @Arguments <script>
    // @Short injects a script into the current queue.
    // @Updated 2016/08/11
    // @Group Queue
    // @Procedural true
    // @Minimum 1
    // @Maximum 1
    // @Description
    // Injects a script into the current queue.
    // TODO: Explain more!
    // @Example
    // # This example injects the script "test" in the current queue.
    // - inject test
    // -->

    @Override
    public String getName() {
        return "inject";
    }

    @Override
    public String getArguments() {
        return "<script>";
    }

    @Override
    public int getMinimumArguments() {
        return 1;
    }

    @Override
    public int getMaximumArguments() {
        return 1;
    }

    @Override
    public boolean isProcedural() {
        return true;
    }

    @Override
    public void execute(CommandQueue queue, CommandEntry entry) {
        AbstractTagObject scriptobj = entry.getArgumentObject(queue, 0);
        String scriptName = scriptobj.toString();
        List<String> bits = CoreUtilities.split(scriptName, '.', 2);
        CommandScript script = Denizen2Core.currentScripts.get(CoreUtilities.toLowerCase(bits.get(0)));
        if (script == null) {
            queue.handleError(entry, "Invalid script name!");
            return;
        }
        if (!(script instanceof TaskScript)) {
            queue.handleError(entry, "Trying to inject a non-task typed script!");
            return;
        }
        TaskScript task = (TaskScript) script;
        CommandScriptSection section = task.getSection(bits.size() > 1 ? bits.get(1) : null);
        if (section == null) {
            queue.handleError(entry, "Invalid script section!");
            return;
        }
        if (queue.shouldShowGood()) {
            queue.outGood("Injecting script: " + ColorSet.emphasis + script.title);
        }
        CommandStackEntry cse = section.toCSE();
        cse.definitions = queue.commandStack.peek().definitions;
        queue.commandStack.push(cse);
    }
}
