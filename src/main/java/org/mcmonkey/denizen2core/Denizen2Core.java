package org.mcmonkey.denizen2core;

import org.mcmonkey.denizen2core.addons.AddonInfo;
import org.mcmonkey.denizen2core.addons.AddonLoader;
import org.mcmonkey.denizen2core.addons.DenizenAddon;
import org.mcmonkey.denizen2core.arguments.Argument;
import org.mcmonkey.denizen2core.arguments.TagArgumentBit;
import org.mcmonkey.denizen2core.arguments.TagBit;
import org.mcmonkey.denizen2core.arguments.TextArgumentBit;
import org.mcmonkey.denizen2core.commands.AbstractCommand;
import org.mcmonkey.denizen2core.commands.CommandQueue;
import org.mcmonkey.denizen2core.commands.CommandScriptSection;
import org.mcmonkey.denizen2core.commands.commoncommands.EchoCommand;
import org.mcmonkey.denizen2core.commands.commoncommands.ReloadCommand;
import org.mcmonkey.denizen2core.commands.queuecommands.*;
import org.mcmonkey.denizen2core.events.ScriptEvent;
import org.mcmonkey.denizen2core.events.commonevents.ScriptReloadEvent;
import org.mcmonkey.denizen2core.scripts.CommandScript;
import org.mcmonkey.denizen2core.scripts.ScriptHelper;
import org.mcmonkey.denizen2core.scripts.commontypes.ProcedureScript;
import org.mcmonkey.denizen2core.scripts.commontypes.TaskScript;
import org.mcmonkey.denizen2core.scripts.commontypes.WorldScript;
import org.mcmonkey.denizen2core.tags.AbstractTagBase;
import org.mcmonkey.denizen2core.tags.handlers.*;
import org.mcmonkey.denizen2core.utilities.CoreUtilities;
import org.mcmonkey.denizen2core.utilities.Function2;
import org.mcmonkey.denizen2core.utilities.debugging.ColorSet;
import org.mcmonkey.denizen2core.utilities.debugging.Debug;
import org.mcmonkey.denizen2core.utilities.yaml.StringHolder;
import org.mcmonkey.denizen2core.utilities.yaml.YAMLConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The main entry class for Denizen2's core engine.
 */
public class Denizen2Core {

    public final static String version;

    static {
        YAMLConfiguration config = null;
        try {
            InputStream is = Denizen2Core.class.getResourceAsStream("/denizen2.yml");
            config = YAMLConfiguration.load(CoreUtilities.streamToString(is));
            is.close();
        }
        catch (Exception ex) {
            Debug.exception(ex);
        }
        if (config == null) {
            version = "UNKNOWN (Error reading version file!)";
        }
        else {
            version = config.getString("VERSION", "UNKNOWN") + " (build " + config.getString("BUILD_NUMBER", "UNKNOWN") + ")";
        }
    }

    private static Denizen2Implementation implementation;

    public final static HashMap<String, CommandScript> currentScripts = new HashMap<>();

    public final static HashMap<String, AbstractCommand> commands = new HashMap<>();

    public final static HashMap<String, AbstractTagBase> tagBases = new HashMap<>();

    public final static List<ScriptEvent> events = new ArrayList<>();

    public final static HashMap<String, Function2<String, YAMLConfiguration, CommandScript>> scriptTypeGetters = new HashMap<>();

    public static Denizen2Implementation getImplementation() {
        return implementation;
    }

    public static void register(ScriptEvent evt) {
        events.add(evt);
    }

    public static void register(AbstractCommand command) {
        commands.put(command.getName(), command);
    }

    public static void register(AbstractTagBase tagbase) {
        tagBases.put(tagbase.getName(), tagbase);
    }

    public static void register(String type, Function2<String, YAMLConfiguration, CommandScript> func) {
        scriptTypeGetters.put(type, func);
    }

    public static List<CommandQueue> queues = new ArrayList<>();

    public static long cqID = 0;

    public static void tick(double delta) {
        Iterator<CommandQueue> qi = queues.iterator();
        while (qi.hasNext()) {
            CommandQueue queue = qi.next();
            if (queue.run(delta)) {
                qi.remove();
            }
        }
    }

    private static List<DenizenAddon> addons = new ArrayList<>();

    public static void init(Denizen2Implementation impl) {
        // Track this implementation value
        implementation = impl;
        // Enforce a reasonable locale on the machine - to prevent format errors
        if (implementation.enforceLocale()) {
            Locale.setDefault(Locale.US);
        }
        // Clear any old data
        commands.clear();
        tagBases.clear();
        currentScripts.clear();
        scriptTypeGetters.clear();
        // Common Commands
        register(new EchoCommand());
        register(new ReloadCommand());
        // Queue Commands
        register(new AssertCommand());
        register(new DefineCommand());
        register(new DetermineCommand());
        register(new ElseCommand());
        register(new ForeachCommand());
        register(new GotoCommand());
        register(new IfCommand());
        register(new InjectCommand());
        register(new MarkCommand());
        register(new PauseCommand());
        register(new RepeatCommand());
        register(new RequireCommand());
        register(new ResumeCommand());
        register(new RunCommand());
        register(new StopCommand());
        register(new WaitCommand());
        register(new WhileCommand());
        // Common Tag Handlers
        register(new BooleanTagBase());
        register(new DefTagBase());
        register(new DurationTagBase());
        register(new EscapeTagBase());
        register(new IntegerTagBase());
        register(new ListTagBase());
        register(new MapTagBase());
        register(new NumberTagBase());
        register(new ProcedureTagBase());
        register(new QueueTagBase());
        register(new ScriptTagBase());
        register(new TextTagBase());
        register(new SystemTagBase());
        register(new UnescapeTagBase());
        // Common script types
        register("procedure", ProcedureScript::new);
        register("task", TaskScript::new);
        register("world", WorldScript::new);
        // Common script events
        scriptReload = new ScriptReloadEvent();
        register(scriptReload);
        File addonsFolder = impl.getAddonsFolder();
        if (!addonsFolder.exists()) {
            Debug.error("Addons folder non-existent!");
        }
        else {
            addons.addAll(AddonLoader.loadAddons(addonsFolder));
        }
    }

    private static ScriptReloadEvent scriptReload = new ScriptReloadEvent();

    public static void reload() {
        currentScripts.clear();
        ScriptEvent.currentWorldScripts.clear();
        load();
        addons.forEach(DenizenAddon::reload);
        scriptReload.call();
    }

    private static void loadSection(String scriptName, YAMLConfiguration section) {
        String type = CoreUtilities.toLowerCase(section.getString("type", "_not set_"));
        Function2<String, YAMLConfiguration, CommandScript> getter = scriptTypeGetters.get(type);
        if (getter == null) {
            Debug.error("Unknown type '" + type + "' for script " + scriptName);
            return;
        }
        CommandScript script = getter.apply(scriptName, section);
        if (script.init()) {
            getImplementation().outputGood("Loaded script '" + ColorSet.emphasis + scriptName + ColorSet.good + "'");
            currentScripts.put(scriptName, script);
        }
        else {
            Debug.error("Failed to load script '" + ColorSet.emphasis + scriptName + ColorSet.warning + "'!");
        }
    }

    private static void loadFile(String fname, String file) {
        try {
            YAMLConfiguration config = YAMLConfiguration.load(ScriptHelper.ClearComments(file));
            if (config == null) {
                Debug.error("Invalid YAML for script " + ColorSet.emphasis + fname);
                return;
            }
            Set<StringHolder> strs = config.getKeys(false);
            for (StringHolder strh : strs) {
                loadSection(strh.low, config.getConfigurationSection(strh.str));
            }
        }
        catch (Exception ex) {
            Debug.error("Failed to load script: " + ColorSet.emphasis + fname);
            Debug.exception(ex);
        }
    }

    public static void load() {
        File folder = getImplementation().getScriptsFolder();
        try {
            if (!folder.exists()) {
                Debug.error("Scripts folder non-existent!");
                return;
            }
            Stream<Path> paths = Files.walk(folder.toPath(), FileVisitOption.FOLLOW_LINKS);
            Iterator<Path> pathi = paths.iterator();
            while (pathi.hasNext()) {
                Path p = pathi.next();
                if (!CoreUtilities.toLowerCase(p.toString()).endsWith(".yml")) {
                    continue;
                }
                File f = p.toFile();
                if (f.exists() && !f.isDirectory()) {
                    FileInputStream fis = new FileInputStream(f);
                    loadFile(f.getName(), CoreUtilities.streamToString(fis));
                    fis.close();
                }
            }
        }
        catch (IOException ex) {
            Debug.exception(ex);
        }
        for (ScriptEvent event : events) {
            event.init();
        }
    }

    public static void unload() {
        // TODO: unload other things???
        disableAddons();
    }

    public static void disableAddons() {
        for (DenizenAddon addon : addons) {
            AddonInfo addonInfo = addon.getAddonInfo();
            try {
                Debug.info("Disabling addon " + addonInfo.getName() + " " + addonInfo.getVersion());
                addon.disable();
                Debug.good("Successfully disabled " + addonInfo.getName() + " " + addonInfo.getVersion());
            }
            catch (Exception e) {
                Debug.error("Failed to disable addon " + addonInfo.getName() + " " + addonInfo.getVersion());
            }
        }
        addons.clear();
    }

    public static void runString(String cmd) {
        CommandScriptSection sec = CommandScriptSection.forLine(cmd);
        if (sec != null) {
            sec.toQueue().start();
        }
    }

    public static String escape(String input) {
        return input.replace("<", "\0TAGSTART").replace(">", "\0TAGEND");
    }

    public static String unescape(String input) {
        return input.replace("\0TAGSTART", "<").replace("\0TAGEND", ">");
    }

    public static Argument splitToArgument(String input, boolean wasQuoted) {
        if (input.length() == 0) {
            return new Argument();
        }
        if (input.indexOf('<') < 0) {
            Argument arg = new Argument();
            arg.addBit(new TextArgumentBit(input, wasQuoted));
            return arg;
        }
        Argument arg = new Argument();
        arg.setQuoted(wasQuoted);
        int len = input.length();
        int blocks = 0;
        int brackets = 0;
        StringBuilder blockbuilder = new StringBuilder();
        StringBuilder tbuilder = new StringBuilder();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (c == '<') {
                blocks++;
                if (blocks == 1) {
                    continue;
                }
            }
            else if (c == '>') {
                blocks--;
                if (blocks == 0) {
                    if (tbuilder.length() > 0) {
                        arg.addBit(new TextArgumentBit(tbuilder.toString(), wasQuoted));
                        tbuilder = new StringBuilder();
                    }
                    String value = blockbuilder.toString();
                    String fallback = null;
                    int brack = 0;
                    for (int fb = 1; fb < value.length(); fb++) {
                        if (value.charAt(fb) == '[') {
                            brack++;
                        }
                        if (value.charAt(fb) == ']') {
                            brack--;
                        }
                        if (brack == 0 && value.charAt(fb) == '|' && value.charAt(fb - 1) == '|') {
                            fallback = value.substring(fb + 1);
                            value = value.substring(0, fb - 1);
                            break;
                        }
                    }
                    List<String> split = CoreUtilities.split(value, '.');
                    for (int s = 0; s < split.size(); s++) {
                        split.set(s, split.get(s).replace("&dot", ".").replace("&amp", "&"));
                    }
                    List<TagBit> bits = new ArrayList<>();
                    for (int x = 0; x < split.size(); x++) {
                        String key;
                        Argument variable;
                        if (split.get(x).length() > 1 && split.get(x).contains("[") && split.get(x).charAt(split.get(x).length() - 1) == ']') {
                            int index = split.get(x).indexOf('[');
                            variable = splitToArgument(split.get(x).substring(index + 1, split.get(x).length() - 1), wasQuoted);
                            split.set(x, CoreUtilities.toLowerCase(split.get(x).substring(0, index)));
                            if (split.get(x).length() == 0) {
                                if (x == 0) {
                                    split.set(x, "def");
                                }
                                else {
                                    split.set(x, "get");
                                }
                            }
                        }
                        else {
                            split.set(x, CoreUtilities.toLowerCase(split.get(x)));
                            variable = new Argument();
                        }
                        key = split.get(x);
                        bits.add(new TagBit(key, variable));
                    }
                    TagBit[] tbits = new TagBit[bits.size()];
                    TagArgumentBit tab = new TagArgumentBit(bits.toArray(tbits));
                    if (tab.bits.length > 0) {
                        AbstractTagBase start;
                        tab.setStart(tagBases.get(CoreUtilities.toLowerCase(tab.bits[0].key)));
                    }
                    tab.setFallback(fallback == null ? null : splitToArgument(fallback, false));
                    arg.addBit(tab);
                    blockbuilder = new StringBuilder();
                    continue;
                }
            }
            else if (blocks == 1 && c == '[') {
                brackets++;
            }
            else if (blocks == 1 && c == ']') {
                brackets--;
            }
            if (blocks > 0) {
                switch (c) {
                    case '.':
                        if (blocks > 1 || brackets > 0)
                        {
                            blockbuilder.append("&dot");
                        }
                        else
                        {
                            blockbuilder.append(".");
                        }
                        break;
                    case '&':
                        blockbuilder.append("&amp");
                        break;
                    default:
                        blockbuilder.append(c);
                        break;
                }
            }
            else {
                tbuilder.append(c);
            }
        }
        if (tbuilder.length() > 0) {
            arg.addBit(new TextArgumentBit(tbuilder.toString(), wasQuoted));
        }
        return arg;
    }
}
