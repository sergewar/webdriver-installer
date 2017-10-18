package com.sss.testing.utils.webdriversinstaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sss.testing.utils.webdriversinstaller.newversion.FileExtractor;
import com.sss.testing.utils.webdriversinstaller.newversion.FileExtractorImpl;

import java.nio.file.Files;
import java.nio.file.Path;

class DriverExtractor {
    private static final Logger logger = LoggerFactory.getLogger(DriverExtractor.class);
    private final InstallWebDrivers iwd;

    DriverExtractor(InstallWebDrivers iwd) {
        this.iwd = iwd;
    }

    Path extractDriver(Driver driver, Path downloadedFile) throws InstallWebDriversException {
        FileExtractor fileExtractor = new FileExtractorImpl(driver.getFileMatchInside());

        try {
            if (fileExtractor.isExtractable(downloadedFile)) {
                logger.info("  Extracting " + Utils.quote(downloadedFile) + " to temp folder");
                fileExtractor.extractFile(downloadedFile, iwd.tempDirectory);
            } else {
                logger.info("  Copying " + Utils.quote(downloadedFile) + " to temp folder");
                Files.createDirectories(iwd.tempDirectory);
                Files.copy(downloadedFile, iwd.tempDirectory.resolve(downloadedFile.getFileName()));
            }
            if (!iwd.keepDownloadedWebdrivers) {
                Files.delete(downloadedFile);
            }
            return iwd.tempDirectory;
        } catch (Exception e) {
            throw new InstallWebDriversException("Failed to extract driver from " +
                    Utils.quote(downloadedFile) + " cause of " + e.getMessage(), e, iwd, driver);
        }
    }
}
