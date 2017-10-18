package com.sss.testing.utils.webdriversinstaller;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class InstallWebDrivers {
    private static final Logger logger = LoggerFactory.getLogger(InstallWebDrivers.class);

    /**
     * URL to where the repository file is located. The repository file is a
     * json file containing information of available drivers and their
     * locations, checksums, etc.
     * main repository URL is
     * "https://raw.githubusercontent.com/webdriverextensions/webdriverextensions-maven-plugin-repository/master/repository-3.0.json"
     * but we use private repository
     */
    URL repositoryUrl;

    /**
     * The path to the directory where the drivers are going to be installed.
     */
    File installationDirectory;

    /**
     * List of drivers to install. Each driver has a name, platform, bit,
     * version, URL and checksum that can be provided.<br/>
     * <br/>
     * If no drivers are provided the latest drivers will be installed for the
     * running platform and the bit version will be chosen as if it has not
     * been provided (see rule below).<br/>
     * <br/>
     * If no platform is provided for a driver the platform will automatically
     * be set to the running platform.<br/>
     * <br/>
     * If no bit version is provided for a driver the bit will automatically
     * be set to 32 if running the plugin on a windows or mac platform. However
     * if running the plugin from a linux platform the bit will be determined
     * from the OS bit version.<br/>
     * <br/>
     * If the driver is not available in the repository the plugin does not know
     * from which URL to download the driver. In that case the URL should be
     * provided for the driver together with a checksum (to retrieve the
     * checksum run the plugin without providing a checksum once, the plugin
     * will then calculate and print the checksum for you). The default
     * repository with all available drivers can be found <a href="https://github.com/webdriverextensions/webdriverextensions-maven-plugin-repository/blob/master/repository-3.0.json">here</a>.<br/>
     * <br/>
     * <strong>Some Examples</strong><br/>
     * Installing all latest drivers<br/>
     * <pre>
     * &lt;drivers&gt;
     *   &lt;driver&gt;
     *     &lt;name&gt;chromedriver&lt;/name&gt;
     *     &lt;platform&gt;windows&lt;/platform&gt;
     *     &lt;bit&gt;32&lt;/bit&gt;
     *   &lt;/driver&gt;
     *   &lt;driver&gt;
     *     &lt;name&gt;chromedriver&lt;/name&gt;
     *     &lt;platform&gt;mac&lt;/platform&gt;
     *     &lt;bit&gt;32&lt;/bit&gt;
     *   &lt;/driver&gt;
     *   &lt;driver&gt;
     *     &lt;name&gt;chromedriver&lt;/name&gt;
     *     &lt;platform&gt;linux&lt;/platform&gt;
     *     &lt;bit&gt;32&lt;/bit&gt;
     *   &lt;/driver&gt;
     *   &lt;driver&gt;
     *     &lt;name&gt;chromedriver&lt;/name&gt;
     *     &lt;platform&gt;linux&lt;/platform&gt;
     *     &lt;bit&gt;64&lt;/bit&gt;
     *   &lt;/driver&gt;
     *   &lt;driver&gt;
     *     &lt;name&gt;internetexplorerdriver&lt;/name&gt;
     *     &lt;platform&gt;windows&lt;/platform&gt;
     *     &lt;bit&gt;32&lt;/bit&gt;
     *   &lt;/driver&gt;
     *   &lt;driver&gt;
     *     &lt;name&gt;internetexplorerdriver&lt;/name&gt;
     *     &lt;platform&gt;windows&lt;/platform&gt;
     *     &lt;bit&gt;64&lt;/bit&gt;
     *   &lt;/driver&gt;
     * &lt;/drivers&gt;
     * </pre>
     * <br/>
     * <p/>
     * Installing a driver not available in the repository, e.g. PhantomJS<br/>
     * <pre>
     * &lt;driver&gt;
     *   &lt;name&gt;phanthomjs&lt;/name&gt;
     *   &lt;platform&gt;mac&lt;/platform&gt;
     *   &lt;bit&gt;32&lt;/bit&gt;
     *   &lt;version&gt;1.9.7&lt;/version&gt;
     *   &lt;url&gt;http://bitbucket.org/ariya/phantomjs/downloads/phantomjs-1.9.7-macosx.zip&lt;/url&gt;
     *   &lt;checksum&gt;0f4a64db9327d19a387446d43bbf5186&lt;/checksum&gt;
     * &lt;/driver&gt;
     * </pre>
     */
    List<Driver> drivers = new ArrayList<>();

    /**
     * Keep downloaded files as local cache
     */
    boolean keepDownloadedWebdrivers = false;

    Path pluginWorkingDirectory = Paths.get(System.getProperty("java.io.tmpdir")).resolve("webdrivers-installer");
    Path downloadDirectory = pluginWorkingDirectory.resolve("downloads");
    Path tempDirectory = pluginWorkingDirectory.resolve("temp");
    Repository repository;

    public InstallWebDrivers() {
        installationDirectory = new File(System.getProperty("user.dir") + "/drivers/");
        try {
            repositoryUrl = new URL(new URL("https://raw.githubusercontent.com") +
                    "/webdriverextensions/webdriverextensions-maven-plugin-repository/master/repository-3.0.json");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param driverForInstall driver what you want to install
     * @throws InstallWebDriversException exception
     */
    public void installDriver(Driver driverForInstall) throws InstallWebDriversException {
        repository = new Repository().load(repositoryUrl);

        logger.info("Installation directory " + Utils.quote(installationDirectory.toPath()));

        if (drivers.isEmpty()) {
            logger.info("Installing latest drivers for current platform");
            drivers = repository.getLatestDrivers();
        } else {
            logger.info("Installing drivers from configuration");
        }

        DriverDownloader driverDownloader = new DriverDownloader(this);
        DriverExtractor driverExtractor = new DriverExtractor(this);
        DriverInstaller driverInstaller = new DriverInstaller(this);

        cleanupTempDirectory();
        {
            Driver driver = repository.enrichDriver(driverForInstall);
            if (driver == null) {
                throw new IllegalArgumentException("  Unreachable driver: " + driverForInstall.toString());
            }
            logger.info(driver.getId() + " version " + driver.getVersion());
            if (driverInstaller.needInstallation(driver)) {
                Path downloadPath = downloadDirectory.resolve(driver.getDriverDownloadDirectoryName());
                Path downloadLocation = driverDownloader.downloadFile(driver, downloadPath);
                Path extractLocation = driverExtractor.extractDriver(driver, downloadLocation);
                driverInstaller.install(driver, extractLocation);
                if (!keepDownloadedWebdrivers) {
                    cleanupDownloadsDirectory();
                }
                cleanupTempDirectory();
            } else {
                logger.info("  Already installed");
            }
        }
    }

    public void initiateConfig(File installationDirectory) {
        initiateConfig(installationDirectory, null);
    }

    public void initiateConfig(URL repositoryUrl) {
        initiateConfig(null, repositoryUrl);
    }

    public void initiateConfig(File installationDirectory, URL repositoryUrl) {
        if (installationDirectory != null) {
            this.installationDirectory = installationDirectory;
        }
        if (repositoryUrl != null) {
            this.repositoryUrl = repositoryUrl;
        }
    }

    private void cleanupDownloadsDirectory() throws InstallWebDriversException {
        try {
            FileUtils.deleteDirectory(downloadDirectory.toFile());
        } catch (IOException e) {
            throw new InstallWebDriversException("Failed to delete downloads directory:" + System.lineSeparator()
                    + Utils.directoryToString(downloadDirectory), e);
        }
    }

    private void cleanupTempDirectory() throws InstallWebDriversException {
        try {
            FileUtils.deleteDirectory(tempDirectory.toFile());
        } catch (IOException e) {
            throw new InstallWebDriversException("Failed to delete temp directory:" + System.lineSeparator()
                    + Utils.directoryToString(tempDirectory), e);
        }
    }

}
