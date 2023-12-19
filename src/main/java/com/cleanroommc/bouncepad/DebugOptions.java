package com.cleanroommc.bouncepad;


public enum DebugOptions {

    EXPLICIT_LOGGING("bouncepad.debug.explicit_logging"),
    SAVE_CLASS_BEFORE_ALL_TRANSFORMATIONS("bouncepad.debug.save_class_before_all_transformations"),
    SAVE_CLASS_AFTER_EACH_TRANSFORMATION("bouncepad.debug.save_class_after_each_transformation"),
    SAVE_CLASS_AFTER_ALL_TRANSFORMATIONS("bouncepad.debug.save_class_after_all_transformations");

    /**
     * Call this to refresh debug properties, should be used after programmatically setting these values on or off
     */
    public static void refreshValues() {
        for (DebugOptions debugOptions : DebugOptions.values()) {
            debugOptions.refreshValue();
        }
    }

    private final String propertyString;

    private boolean value;

    DebugOptions(String propertyString) {
        this.propertyString = propertyString;
        this.refreshValue();
    }

    public boolean isOn() {
        return value;
    }

    /**
     * Call this to refresh debug property, should be used after programmatically setting the value on or off
     */
    public void refreshValue() {
        this.value = Boolean.parseBoolean(System.getProperty(this.propertyString));
    }

    @Override
    public String toString() {
        return this.propertyString;
    }

}
