package com.iota.iri;

import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.service.CallableRequest;
import com.iota.iri.service.dto.AbstractResponse;
import com.iota.iri.service.dto.ErrorResponse;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.*;
import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static com.sun.jmx.mbeanserver.Util.cast;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.*;

public class IXI {

    private static final ScriptEngine scriptEngine = (new ScriptEngineManager()).getEngineByName("JavaScript");
    /*
    private static final ScriptEngine scriptEngine = (new NashornScriptEngineFactory()).getScriptEngine((classname) ->
            !"com.iota.iri.IXI".equals(classname));
    */
    private static final Map<String, Map<String, CallableRequest<AbstractResponse>>> ixiAPI = new HashMap<>();
    private static final Map<String, Map<String, Runnable>> ixiLifetime = new HashMap<>();
    private static final Map<WatchKey, Path> watchKeys = new HashMap<>();
    private static WatchService watcher;
    private static Thread dirWatchThread;

    /*
    TODO: get configuration variable for directory to watch
    TODO: initialize directory listener
    TODO: create events for target added/changed/removed
     */
    public static void init() throws Exception {
        if(Configuration.string(DefaultConfSettings.IXI_DIR).length() > 0) {
            watcher = FileSystems.getDefault().newWatchService();
            Path path = Paths.get(Configuration.string(DefaultConfSettings.IXI_DIR));
            String s = path.toAbsolutePath().toString();
            final File ixiDir = new File(s);
            if(!ixiDir.exists()) ixiDir.mkdir();
            register(path);
            dirWatchThread = (new Thread(IXI::processEvents));
            dirWatchThread.start();
        }
    }

    public static void shutdown() {
        if(dirWatchThread != null) {
            try {
                dirWatchThread.interrupt();
                dirWatchThread.join();
            } catch(InterruptedException e) {
                e.printStackTrace();
            }
            Object[] keys = ixiAPI.keySet().toArray();
            for (Object key : keys) {
                detach((String)key);
            }
        }
    }

    private  static void register (Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        watchKeys.put(key, dir);
        // TODO: Add existing files
        addFiles(dir);
    }

    private static void addFiles (Path dir) throws IOException {
        Files.walk(dir).forEach(filePath -> {
            if(!filePath.equals(dir))
                if(Files.isDirectory(filePath, NOFOLLOW_LINKS)) {
                    try {
                        System.out.format("Searching: %s \n", filePath.toString());
                        addFiles(filePath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.format("File: %s\n", filePath.toString());
                    try {
                        attach(new FileReader(filePath.toFile()), filePath.getFileName().toString().replaceFirst("[.][^.]+$", ""));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
        });
    }

    public static AbstractResponse processCommand(final String command, Map<String, Object> request) {
        try {
            Map<String, CallableRequest<AbstractResponse>> ixiMap;
            AbstractResponse res;
            for (String key :
                    ixiAPI.keySet()) {
                if(command.substring(0, key.length()).equals(key)) {
                    String subCmd = command.substring(key.length()+1);
                    ixiMap = ixiAPI.get(key);
                    res = ((CallableRequest<AbstractResponse>)ixiMap.get(subCmd)).call(request);
                    if(res != null) return res;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void processEvents() {
        while(!Thread.interrupted()) {
            synchronized(instance) {
                WatchKey key;
                try {
                    key = watcher.take();
                } catch (InterruptedException ex) {
                    return;
                }

                pollEvents(key, watchKeys.get(key));
            }
        }
    }

    private static void pollEvents(WatchKey key, Path dir) {
        for (WatchEvent<?> event: key.pollEvents()) {
            WatchEvent.Kind kind = event.kind();

            if(kind == OVERFLOW) {
                continue;
            }

            WatchEvent<Path> ev = cast(event);
            Path name = ev.context();
            Path child = dir.resolve(name);

            executeEvents(kind, child);

            if (!key.reset()) {
                watchKeys.remove(key);
                if (watchKeys.isEmpty()) {
                    break;
                }
            }
        }
    }

    private static void executeEvents(WatchEvent.Kind kind, Path child) {
        if (kind == ENTRY_MODIFY || kind == ENTRY_DELETE) {

            System.out.format("detach child: %s \n", child);
            detach(child.toString().replaceFirst("[.][^.]+$", ""));
        }
        if (kind == ENTRY_CREATE || kind == ENTRY_MODIFY) {
            try {
                if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                    //registerAll(child);
                    System.out.format("child: %s \n", child);
                } else {
                    //Files.isRegularFile(child)
                    System.out.format("load child: %s \n", child);
                    attach(new FileReader(child.toFile()), child.getFileName().toString().replaceFirst("[.][^.]+$", ""));
                }
            } catch (Exception x) {
                // ignore to keep sample readable
            }
        }
    }

    private static void attach(final Reader ixi, final String filename) {
        try {
            Map<String, CallableRequest<AbstractResponse>> ixiMap = new HashMap<>();
            Map<String, Runnable> startStop = new HashMap<>();
            Bindings bindings = scriptEngine.createBindings();

            bindings.put("API", ixiMap);
            bindings.put("IXICycle", startStop);
            ixiAPI.put(filename, ixiMap);
            ixiLifetime.put(filename, startStop);
            scriptEngine.eval(ixi, bindings);

        } catch (final ScriptException e) {
            throw new RuntimeException(e);
        }
    }

    private static void detach(String fileName) {
        Map<String, Runnable> ixiMap = ixiLifetime.get(fileName);
        if(ixiMap != null) {
            Runnable stop = ixiMap.get("shutdown");
            if (stop != null) stop.run();
        }
        ixiAPI.remove(fileName);
        ixiLifetime.remove(fileName);
    }

    private static final IXI instance = new IXI();

    public static IXI instance() {
        return instance;
    }

}
