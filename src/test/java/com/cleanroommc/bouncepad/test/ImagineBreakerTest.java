package com.cleanroommc.bouncepad.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import zone.rong.imaginebreaker.NativeImagineBreaker;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InaccessibleObjectException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class ImagineBreakerTest {

    private static void runImagineBreaker() {
        String imagineBreakerLibraryName = System.mapLibraryName("imaginebreaker");
        URL imagineBreakerLibraryUrl = NativeImagineBreaker.class.getClassLoader().getResource(imagineBreakerLibraryName);
        if (imagineBreakerLibraryUrl == null) {
            System.err.println("Unable to launch, " + imagineBreakerLibraryName + " cannot be found.");
            System.exit(1);
        } else {
            try {
                if ("jar".equals(imagineBreakerLibraryUrl.getProtocol())) {
                    // Extract the native to a temporary file if it resides in a jar (non-dev)
                    Path tempDir = Files.createTempDirectory("bouncepad");
                    tempDir.toFile().deleteOnExit();
                    Path tempFile = tempDir.resolve(imagineBreakerLibraryName);
                    try (InputStream is = NativeImagineBreaker.class.getClassLoader().getResourceAsStream(imagineBreakerLibraryName)) {
                        Files.copy(is, tempFile);
                    }
                    tempFile.toFile().deleteOnExit();
                    System.load(tempFile.toAbsolutePath().toString());
                } else {
                    // Load as-is if it is outside a jar (dev)
                    System.load(new File(imagineBreakerLibraryUrl.toURI()).getAbsolutePath());
                }
                // NativeImagineBreaker.openBaseModules();
                // NativeImagineBreaker.removeAllReflectionFilters();
            } catch (Throwable t) {
                System.err.println("Unable to launch, error loading natives: " + t);
                System.exit(1);
            }
        }
    }

    @Test
    public void runNative() {
        System.out.println("Running with Java " + System.getProperty("java.version"));
        Assertions.assertThrows(RuntimeException.class, () -> File.class.getDeclaredField("status").setAccessible(true));
        runImagineBreaker();
        NativeImagineBreaker.openBaseModules();
        Assertions.assertDoesNotThrow(() -> File.class.getDeclaredField("status").setAccessible(true));
        Assertions.assertAll(NativeImagineBreaker::removeAllReflectionFilters);
    }

}
