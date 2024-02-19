package com.cleanroommc.bouncepad.debug;


public enum DebugOption {

    DO_NOT_PROCESS_ARGUMENTS("bouncepad.doNotProcessArguments"),
    EXPLICIT_LOGGING("bouncepad.explicitLogging"),
    SAVE_CLASS_BEFORE_ALL_TRANSFORMATIONS("bouncepad.saveClassBeforeAllTransformations"),
    SAVE_CLASS_AFTER_EACH_TRANSFORMATION("bouncepad.saveClassAfterEachTransformation"),
    SAVE_CLASS_AFTER_ALL_TRANSFORMATIONS("bouncepad.saveClassAfterAllTransformations");

    private final String propertyString;

    DebugOption(String propertyString) {
        this.propertyString = propertyString;
    }

    public boolean isOn() {
        return Boolean.parseBoolean(System.getProperty(this.propertyString));
    }

    public boolean isOff() {
        return !isOn();
    }

    @Override
    public String toString() {
        return this.propertyString;
    }

}
