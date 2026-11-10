package managers;

import dataProviders.ConfigFileReader;

/**
 * Singleton holder so configuration.properties is read only once per test run.
 * The file is read on first use, not when the class loads: if reading fails, every caller gets
 * the real error instead of a NoClassDefFoundError after the first one.
 */
public final class FileReaderManager {
    private static final FileReaderManager INSTANCE = new FileReaderManager();
    private ConfigFileReader configFileReader;

    private FileReaderManager() {
    }

    public static FileReaderManager getInstance() {
        return INSTANCE;
    }

    public synchronized ConfigFileReader getConfigReader() {
        if (configFileReader == null) {
            configFileReader = new ConfigFileReader();
        }
        return configFileReader;
    }
}
