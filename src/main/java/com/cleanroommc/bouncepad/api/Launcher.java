package com.cleanroommc.bouncepad.api;

import java.util.List;

@FunctionalInterface
public interface Launcher {

    void launch(List<String> arguments) throws Exception;

}
