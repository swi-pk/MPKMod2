package io.github.kurrycat.mpkmod.ticks;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.kurrycat.mpkmod.compatibility.API;
import io.github.kurrycat.mpkmod.save.Serializer;
import io.github.kurrycat.mpkmod.settings.Settings;
import io.github.kurrycat.mpkmod.util.FileUtil;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TimingStorage {
    private final static String defaultStratFileName = "/assets/mpkmod/strats/strats.json";
    private final static String extraStratFileName = "/assets/mpkmod/strats/strats-extra.json";
    private final static String stratFileName = "config/mpk/config/strats.json";
    private final static TypeReference<HashMap<String, Timing>> STRATS_TYPE = new TypeReference<HashMap<String, Timing>>() {};
    public static Map<String, Timing> patterns = new HashMap<>();

    /** Loads the built-in strats, the extra ones if enabled, then the user's. Called again when the setting changes. */
    public static void init() {
        HashMap<String, Timing> loaded = Serializer.deserializeAny(FileUtil.getResource(defaultStratFileName), STRATS_TYPE);
        if (loaded == null) return;

        if (Settings.moreTimings.getValue()) {
            InputStream extraFile = FileUtil.getResource(extraStratFileName);
            HashMap<String, Timing> extra = extraFile == null ? null : Serializer.deserializeAny(extraFile, STRATS_TYPE);
            // Also replaces the base mark and markrun
            if (extra != null) loaded.putAll(extra);
        }

        File file = new File(stratFileName);
        if (!file.exists()) {
            //Make an empty json file if it doesn't exist
            FileUtil.createFile(stratFileName, "{}");
        }

        HashMap<String, Timing> timingMap = Serializer.deserializeAny(file, STRATS_TYPE);
        if (timingMap != null)
            loaded.putAll(timingMap);

        patterns = loaded;
        API.LOGGER.info(API.CONFIG_MARKER, "{} Timings loaded from {}", patterns.size(), stratFileName);
    }

    public static String match(List<TimingInput> inputList) {
        List<Timing.Match> matches = new ArrayList<>();
        for (Map.Entry<String, Timing> entry : patterns.entrySet()) {
            Timing.Match match = entry.getValue().match(inputList);
            if (match != null)
                matches.add(match);
        }

        if (matches.isEmpty())
            return null;

        matches.sort(null);

        return matches.get(0).displayString;
    }
}
