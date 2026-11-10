package managers;

import dataProviders.ConfigFileReader;

/** Singleton holder so configuration.properties is read only once per test run. */
public final class FileReaderManager {
    private static final FileReaderManager INSTANCE = new FileReaderManager();
    private final ConfigFileReader configFileReader = new ConfigFileReader();

    private FileReaderManager() {
    }

    public static FileReaderManager getInstance() {
        return INSTANCE;
    }

    public ConfigFileReader getConfigReader() {
        return configFileReader;
    }
}
