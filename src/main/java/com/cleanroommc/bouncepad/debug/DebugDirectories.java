package com.cleanroommc.bouncepad.debug;

import com.cleanroommc.bouncepad.Bouncepad;

import java.nio.file.Path;

public enum DebugDirectories {

    BEFORE_ALL_TRANSFORMATIONS("transformations/before_all"),
    AFTER_EACH_TRANSFORMATION("transformations/after_each"),
    AFTER_ALL_TRANSFORMATIONS("transformations/after_all");

    private final String directory;

    private Path path;

    DebugDirectories(String directory) {
        this.directory = directory;
    }

    public Path path() {
        this.init();
        return this.path;
    }

    public Path path(String... paths) {
        this.init();
        var path = this.path;
        for (var nextPath : paths) {
            path = path.resolve(nextPath);
        }
        return path;
    }

    private void init() {
        if (this.path == null) {
            this.path = Bouncepad.minecraftHome().resolve(directory);
        }
    }

}
