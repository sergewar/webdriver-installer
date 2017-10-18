package com.sss.testing.utils.webdriversinstaller;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.sss.testing.utils.webdriversinstaller.Utils.quote;
import static org.apache.commons.io.FileUtils.copyInputStreamToFile;

public class DriverDownloader {

    private final Logger logger = LoggerFactory.getLogger(DriverDownloader.class);

    public static final int FILE_DOWNLOAD_READ_TIMEOUT = 30 * 60 * 1000; // 30 min
    public static final int FILE_DOWNLOAD_CONNECT_TIMEOUT = 30 * 1000; // 30 seconds
    public static final int FILE_DOWNLOAD_RETRY_ATTEMPTS = 3;
    private final InstallWebDrivers iwd;

    public DriverDownloader(InstallWebDrivers iwd) throws InstallWebDriversException {
        this.iwd = iwd;
    }

    public Path downloadFile(Driver driver, Path downloadDirectory) throws InstallWebDriversException {
        String url = driver.getUrl();
        Path downloadFilePath = downloadDirectory.resolve(driver.getFilenameFromUrl());

        if (downloadFilePath.toFile().exists() && !downloadCompletedFileExists(downloadDirectory)) {
            logger.info("  Removing downloaded driver " + quote(downloadFilePath) + " since it may be corrupt");
            cleanupDriverDownloadDirectory(downloadDirectory);
        } else if (!iwd.keepDownloadedWebdrivers) {
            cleanupDriverDownloadDirectory(downloadDirectory);
        }

        if (downloadFilePath.toFile().exists()) {
            logger.info("  Using cached driver from " + quote(downloadFilePath));
        } else {
            logger.info("  Downloading " + quote(url) + " to " + quote(downloadFilePath));
            HttpClientBuilder httpClientBuilder = prepareHttpClientBuilderWithTimeouts();
            httpClientBuilder.setRetryHandler(new DefaultHttpRequestRetryHandler(FILE_DOWNLOAD_RETRY_ATTEMPTS, true));
            try (CloseableHttpClient httpClient = httpClientBuilder.build()) {
                try (CloseableHttpResponse fileDownloadResponse = httpClient.execute(new HttpGet(url))) {
                    HttpEntity remoteFileStream = fileDownloadResponse.getEntity();
                    copyInputStreamToFile(remoteFileStream.getContent(), downloadFilePath.toFile());
                    if (driverFileIsCorrupt(downloadFilePath)) {
                        printXmlFileContetIfPresentInDonwloadedFile(downloadFilePath);
                        cleanupDriverDownloadDirectory(downloadDirectory);
                        throw new InstallWebDriversException("Failed to download a non corrupt driver", iwd, driver);
                    }
                }
            } catch (InstallWebDriversException e) {
                throw e;
            } catch (Exception e) {
                throw new InstallWebDriversException("Failed to download driver from " + quote(url) + " to " + quote(downloadFilePath) + " cause of " + e.getCause(), e, iwd, driver);
            }
            createDownloadCompletedFile(downloadDirectory);
        }
        return downloadFilePath;
    }

    private void printXmlFileContetIfPresentInDonwloadedFile(Path downloadFilePath) {
        try {
            List<String> fileContent = Files.readAllLines(downloadFilePath, StandardCharsets.UTF_8);
            if (fileContent.get(0).startsWith("<?xml")) {
                logger.info("  Downloaded driver file contains the following error message");
                for (String line : fileContent) {
                    logger.info("  " + line);
                }
            }
        } catch (Exception e) {
            // no file  or file content to read
        }
    }

    private HttpClientBuilder prepareHttpClientBuilderWithTimeouts() {
        SocketConfig socketConfig = SocketConfig.custom().setSoTimeout(FILE_DOWNLOAD_READ_TIMEOUT).build();
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(FILE_DOWNLOAD_CONNECT_TIMEOUT)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        httpClientBuilder
                .setDefaultSocketConfig(socketConfig)
                .setDefaultRequestConfig(requestConfig)
                .disableContentCompression();
        return httpClientBuilder;
    }

    private boolean driverFileIsCorrupt(Path downloadFilePath) {
        if (Utils.hasExtension(downloadFilePath, "zip")) {
            return !Utils.validateZipFile(downloadFilePath);
        } else if (Utils.hasExtension(downloadFilePath, "bz2")) {
            if (!Utils.validateBz2File(downloadFilePath)) {
                return true;
            } else {
                return !Utils.validateFileIsLargerThanBytes(downloadFilePath, 1000);
            }
        } else {
            return false;
        }
    }

    public void cleanupDriverDownloadDirectory(Path downloadDirectory) throws InstallWebDriversException {
        try {
            FileUtils.deleteDirectory(downloadDirectory.toFile());
        } catch (IOException e) {
            throw new InstallWebDriversException("Failed to delete driver cache directory:" + System.lineSeparator()
                    + Utils.directoryToString(downloadDirectory), e);
        }
    }

    private boolean downloadCompletedFileExists(Path downloadDirectory) {
        Path downloadCompletedFile = downloadDirectory.resolve("download.completed");
        return downloadCompletedFile.toFile().exists();
    }

    private void createDownloadCompletedFile(Path downloadDirectory) throws InstallWebDriversException {
        Path downloadCompletedFile = downloadDirectory.resolve("download.completed");
        try {
            Files.createFile(downloadCompletedFile);
        } catch (IOException e) {
            throw new InstallWebDriversException("Failed to create download.completed file at " + downloadCompletedFile, e);

        }
    }
}
