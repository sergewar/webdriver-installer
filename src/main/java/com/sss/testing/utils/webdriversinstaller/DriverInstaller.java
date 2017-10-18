package com.sss.testing.utils.webdriversinstaller;

import org.codehaus.plexus.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class DriverInstaller {
    private static final Logger logger = LoggerFactory.getLogger(DriverInstaller.class);
    private final InstallWebDrivers iwd;
    private final DriverVersionHandler versionHandler;

    public DriverInstaller(InstallWebDrivers iwd) {
        this.iwd = iwd;
        this.versionHandler = new DriverVersionHandler(iwd.installationDirectory.toPath());
    }

    public boolean needInstallation(Driver driver) throws InstallWebDriversException {
        return !isInstalled(driver) || !versionHandler.isSameVersion(driver);
    }

    public void install(Driver driver, Path extractLocation) throws InstallWebDriversException {
        if (extractLocation.toFile().isDirectory() && directoryIsEmpty(extractLocation)) {
            throw new InstallWebDriversException("Failed to install driver since no files found to install", iwd, driver);
        }

        try {
            Files.createDirectories(iwd.installationDirectory.toPath());
            if (directoryContainsSingleDirectory(extractLocation)) {
                Path singleDirectory = extractLocation.toFile().listFiles()[0].toPath();
                moveAllFilesInDirectory(singleDirectory, iwd.installationDirectory.toPath().resolve(driver.getId()));
            } else if (directoryContainsSingleFile(extractLocation)) {
                String newFileName = driver.getFileName();
                moveFileInDirectory(extractLocation, iwd.installationDirectory.toPath(), newFileName);
                makeExecutable(iwd.installationDirectory.toPath().resolve(newFileName));
            } else {
                moveAllFilesInDirectory(extractLocation, iwd.installationDirectory.toPath().resolve(driver.getId()));
            }

            versionHandler.writeVersionFile(driver);
        } catch (Exception e) {
            throw new InstallWebDriversException("Failed to install driver cause of " + e.getMessage(), e, iwd, driver);
        }

    }

    private boolean isInstalled(Driver driver) {
        Path path = iwd.installationDirectory.toPath().resolve(driver.getFileName());
        return path.toFile().exists();
    }

    private boolean directoryIsEmpty(Path directory) {
        return directory.toFile().listFiles().length == 0;
    }

    private boolean directoryContainsSingleFile(Path directory) throws InstallWebDriversException {
        File[] files = directory.toFile().listFiles();
        return files != null && files.length == 1 && files[0].isFile();
    }

    private boolean directoryContainsSingleDirectory(Path directory) {
        File[] files = directory.toFile().listFiles();
        return files != null && files.length == 1 && files[0].isDirectory();
    }

    private void moveFileInDirectory(Path from, Path to, String newFileName) throws InstallWebDriversException {
        assert directoryContainsSingleFile(from);
        try {
            List<String> files = FileUtils.getFileNames(from.toFile(), null, null, true);
            Path singleFile = Paths.get(files.get(0));
            logger.info("  Moving " + Utils.quote(singleFile) + " to " + Utils.quote(to.resolve(newFileName)));
            FileUtils.forceDelete(to.resolve(newFileName).toFile());
            Files.move(singleFile, to.resolve(newFileName));
        } catch (IOException e) {
            throw new RuntimeException("Failed to move file in directory " + Utils.quote(from) + " to " + Utils.quote(to.resolve(newFileName)), e);
        }
    }

    private void moveAllFilesInDirectory(Path from, Path to) throws InstallWebDriversException {
        try {
            Files.createDirectories(to);
            for (File file : from.toFile().listFiles()) {
                logger.info("  Moving " + file + " to " + to.resolve(file.toPath().getFileName()));
                FileUtils.forceDelete(to.resolve(file.toPath().getFileName()).toFile());
                Files.move(file.toPath(), to.resolve(file.toPath().getFileName()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to move directory " + Utils.quote(from) + " to " + Utils.quote(to), e);
        }
    }

    private void makeExecutable(Path path) {
        File file = path.toFile();
        if (file.exists() && !file.canExecute()) {
            file.setExecutable(true);
        }
    }
}
