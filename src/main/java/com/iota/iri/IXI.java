package com.iota.iri;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.service.CallableRequest;
import com.iota.iri.service.dto.AbstractResponse;
import com.iota.iri.service.dto.ErrorResponse;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;

import static com.sun.jmx.mbeanserver.Util.cast;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.*;

public class IXI {
    private static final Logger log = LoggerFactory.getLogger(IXI.class);

    private final Gson gson = new GsonBuilder().create();
    private final ScriptEngine scriptEngine = (new ScriptEngineManager()).getEngineByName("JavaScript");
    /*
    private static final ScriptEngine scriptEngine = (new NashornScriptEngineFactory()).getScriptEngine((classname) ->
            !"com.iota.iri.IXI".equals(classname));
    */
    private final Map<String, Map<String, CallableRequest<AbstractResponse>>> ixiAPI = new HashMap<>();
    private final Map<String, Map<String, Runnable>> ixiLifetime = new HashMap<>();
    private final Map<WatchKey, Path> watchKeys = new HashMap<>();
    private final Map<Path, List<Path>> extensions = new HashMap<>();
    private final Set<Path> visitedPaths = new TreeSet<>();
    private WatchService watcher;
    private Thread dirWatchThread;
    private Path extensionDirectory;

    /*
    TODO: get configuration variable for directory to watch
    TODO: initialize directory listener
    TODO: create events for target added/changed/removed
     */
    public void init(String extensionDirName) throws Exception {
        if(extensionDirName.length() > 0) {
            watcher = FileSystems.getDefault().newWatchService();
            extensionDirectory = Paths.get(extensionDirName);
            String s = extensionDirectory.toAbsolutePath().toString();
            final File ixiDir = new File(s);
            if(!ixiDir.exists()) ixiDir.mkdir();
            register(extensionDirectory);
            dirWatchThread = (new Thread(this::processEvents));
            dirWatchThread.start();
        }
    }

    public void shutdown() throws InterruptedException {
        if(dirWatchThread != null) {
            dirWatchThread.interrupt();
            dirWatchThread.join(6000);
            Object[] keys = ixiAPI.keySet().toArray();
            for (Object key : keys) {
                detach((String)key);
            }
        }
    }

    private  void register (Path path) throws IOException, ScriptException {
        log.info("Path: " + path.toString());
        if(Files.isDirectory(path, NOFOLLOW_LINKS)) {
            log.info("Searching: "+ path);
            WatchKey key = path.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            watchKeys.put(key, path);
            // TODO: Add existing files
            addFiles(path);
        } else if(path.getFileName().toString().equals("package.json")){
            log.info("ss: " + path.toString());
            loadExtension(path);
        } else {
            List<Path> extensionPaths = extensions.get(getExtensionPath(path));
            if(extensionPaths != null && extensionPaths.contains(path)) {
                log.info("ss: " + path.toString());
                loadExtension(extensionPaths.get(0));
            }
        }
    }

    private void addFiles (Path dir) throws IOException {
        Files.list(dir).forEach((Path filePath) -> {
            if(!(filePath.equals(dir) || filePath.toFile().isHidden()))
                try {
                    register(filePath);
                } catch (IOException e) {
                    log.error("Error registering path: ", e);
                } catch (ScriptException e) {
                    log.debug("Script exception: ", e);
                }
        });
    }

    private void unloadExtension(Path packageJson) throws FileNotFoundException, ScriptException {

    }
    private void loadExtension(Path packageJson) throws FileNotFoundException, ScriptException {
        final Map request = gson.fromJson(new FileReader(packageJson.toFile()), Map.class);
        final Path pathToExtension = getExtensionPath(packageJson);
        Path pathToMain = Paths.get(extensionDirectory.toString(), pathToExtension.toString(), (String) request.get("main"));
        String name = pathToExtension.toString().length() != 0? pathToExtension.toString() : pathToMain.getFileName().toString().replaceFirst("[.][^.]+$", "");
        extensions.put(pathToExtension, Arrays.asList(packageJson, pathToMain));
        extensions.get(pathToExtension).forEach(visitedPaths::add);
        //String[] split= relativePathToMain.getFileName().toString().split("[.]+(?=[^.]+$)");
        attach(new FileReader(pathToMain.toFile()), name);
    }
    private Path getExtensionPath(Path path) {
        return extensionDirectory.relativize(path.getParent());
    }

    public AbstractResponse processCommand(final String command, Map<String, Object> request) {
        Map<String, CallableRequest<AbstractResponse>> ixiMap;
        AbstractResponse res;
        String substring;
        for (String key :
                ixiAPI.keySet()) {
            substring = command.substring(0, key.length());
            if(substring.equals(key)) {
                String subCmd = command.substring(key.length()+1);
                ixiMap = ixiAPI.get(key);
                res = ixiMap.get(subCmd).call(request);
                if(res != null) return res;
            }
        }
        return null;
    }

    private void processEvents() {
        int i = 0;
        while(!Thread.interrupted()) {
            synchronized(instance) {
                WatchKey key;
                try {
                    key = watcher.take();
                    //log.info("poll #" + ++i);
                    pollEvents(key, watchKeys.get(key));
                } catch (InterruptedException e) {
                    log.error("Watcher interrupted: ", e);
                }
            }
        }
    }

    private void pollEvents(WatchKey key, Path dir) {
        Map<Path, List<WatchEvent.Kind>> visitedEvents = new HashMap<>();
        for (WatchEvent<?> event: key.pollEvents()) {
            WatchEvent.Kind kind = event.kind();

            if(kind == OVERFLOW) {
                continue;
            }

            WatchEvent<Path> ev = cast(event);
            Path name = ev.context();
            Path child = dir.resolve(name);

            if(visitedEvents.containsKey(child)) {
                visitedEvents.get(child).add(kind);
            } else {
                visitedEvents.put(child, new ArrayList<>(Collections.singleton(kind)));
            }

            if (!key.reset()) {
                watchKeys.remove(key);
                if (watchKeys.isEmpty()) {
                    break;
                }
            }
        }
        visitedEvents.entrySet().forEach(this::executeEvents);
    }

    //private void executeEvents(WatchEvent.Kind kind, Path child) throws IOException, ScriptException {
    private void executeEvents(Map.Entry<Path, List<WatchEvent.Kind>> pathListEntry) {
        if(pathListEntry.getValue().contains(ENTRY_DELETE) || pathListEntry.getValue().contains(ENTRY_MODIFY)) {
            log.debug("detach child: "+ pathListEntry.getKey());
            detach(pathListEntry.getKey().toString().replaceFirst("[.][^.]+$", ""));
        }
        if(pathListEntry.getValue().contains(ENTRY_CREATE) && Files.isDirectory(pathListEntry.getKey(), NOFOLLOW_LINKS)) {
            log.info("Attempting to load directory: "+ pathListEntry.getKey());
            try {
                register(pathListEntry.getKey());
            } catch (IOException e) {
                log.error("Error registering path for IXI.");
            } catch (ScriptException e) {
                log.error("Script Error. ", e);
            }
        } else if((pathListEntry.getValue().contains(ENTRY_CREATE) && visitedPaths.add(pathListEntry.getKey())) || pathListEntry.getValue().contains(ENTRY_MODIFY)) {
            if (!Files.isDirectory(pathListEntry.getKey(), NOFOLLOW_LINKS)) {
                //Files.isRegularFile(child)
                log.info("Attempting to load "+ pathListEntry.getKey());
                try {
                    register(pathListEntry.getKey());
                } catch (IOException e) {
                    log.error("Error registering path for IXI.");
                } catch (ScriptException e) {
                    log.error("Script Error. ", e);
                }
                log.debug("Done.");
            }
        }
    }

    private void attach(final Reader ixi, String name) throws ScriptException {
            Map<String, CallableRequest<AbstractResponse>> ixiMap = new HashMap<>();
            Map<String, Runnable> startStop = new HashMap<>();
            Bindings bindings = scriptEngine.createBindings();

            bindings.put("API", ixiMap);
            bindings.put("IXICycle", startStop);
            ixiAPI.put(name, ixiMap);
            ixiLifetime.put(name, startStop);
            scriptEngine.eval(ixi, bindings);
    }

    private void detach(String extensionName) {
        Map<String, Runnable> ixiMap = ixiLifetime.get(extensionName);
        if(ixiMap != null) {
            Runnable stop = ixiMap.get("shutdown");
            if (stop != null) stop.run();
        }
        ixiAPI.remove(extensionName);
        ixiLifetime.remove(extensionName);
    }

    private static final IXI instance = new IXI();

    public static IXI instance() {
        return instance;
    }

}
