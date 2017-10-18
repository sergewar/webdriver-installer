package com.sss.testing.utils.webdriversinstaller;

import ch.lambdaj.function.compare.ArgumentComparator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.collections.ComparatorUtils;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static ch.lambdaj.Lambda.collect;
import static ch.lambdaj.Lambda.having;
import static ch.lambdaj.Lambda.on;
import static ch.lambdaj.Lambda.select;
import static ch.lambdaj.Lambda.selectDistinct;
import static ch.lambdaj.Lambda.selectMax;
import static ch.lambdaj.Lambda.sort;
import static org.apache.commons.lang3.CharEncoding.UTF_8;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;

class Repository {

    private List<Driver> drivers;

    Repository load(URL repositoryUrl) throws InstallWebDriversException {
        String repositoryAsString;
        try {
            repositoryAsString = downloadAsString(repositoryUrl);
        } catch (IOException e) {
            throw new InstallWebDriversException("Failed to download repository from url " + Utils.quote(
                    repositoryUrl), e);
        }

        Repository repository;
        try {
            repository = new Gson().fromJson(repositoryAsString, Repository.class);
        } catch (JsonSyntaxException e) {
            throw new InstallWebDriversException("Failed to parse repository json " + repositoryAsString, e);
        }

        repository.drivers = sortDrivers(repository.drivers);

        return repository;
    }

    @SuppressWarnings("unchecked")
    private static List<Driver> sortDrivers(List<Driver> drivers) {
        Comparator byId = new ArgumentComparator(on(Driver.class).getId());
        Comparator byVersion = new ArgumentComparator(on(Driver.class).getVersion());
        Comparator orderByIdAndVersion = ComparatorUtils.chainedComparator(byId, byVersion);

        return sort(drivers, on(Driver.class), orderByIdAndVersion);
    }

    private static String downloadAsString(URL url) throws IOException {
        if (url.getProtocol().contains("file") && (url.toString().contains(".jar!\\") || url.toString().contains(".jar!/"))) {
            String resourceUrl = url.toString();
            resourceUrl = resourceUrl.substring(resourceUrl.lastIndexOf("!/") + 2);
            try (InputStream inputStream = getResourceFileStream(resourceUrl)) {
                return IOUtils.toString(inputStream, UTF_8);
            }
        } else {
            URLConnection connection;
            connection = url.openConnection();
            try (InputStream inputStream = connection.getInputStream()) {
                return IOUtils.toString(inputStream, UTF_8);
            }
        }
    }

    /**
     * @param filePath filePath
     * @return resourceFile
     */
    private static InputStream getResourceFileStream(String filePath) {
        try {
            ClassLoader classLoader = Repository.class.getClassLoader();
            return classLoader.getResourceAsStream(filePath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public List<Driver> getDrivers() {
        return drivers;
    }

    List<Driver> getDrivers(String name, String platform, String bit, String version) {
        List<Driver> drivers = this.drivers;
        if (name != null) {
            drivers = select(drivers, having(on(Driver.class).getName(), is(equalToIgnoringCase(name))));
        }
        if (platform != null) {
            drivers = select(drivers, having(on(Driver.class).getPlatform(), is(equalToIgnoringCase(platform))));
        }
        if (bit != null) {
            drivers = select(drivers, having(on(Driver.class).getBit(), is(equalToIgnoringCase(bit))));
        }
        if (version != null) {
            drivers = select(drivers,
                    having(on(Driver.class).getComparableVersion(), Matchers.is(new ComparableVersion(version))));
        }
        return drivers;
    }

    Driver enrichDriver(Driver driver) throws InstallWebDriversException {
        if (isBlank(driver.getName())) {
            throw new InstallWebDriversException("Driver name must be set in configuration, driver: " + driver);
        }
        if (isNotBlank(driver.getUrl())) {
            return driver;
        }
        if (isNotBlank(driver.getPlatform()) || isNotBlank(driver.getBit()) || isNotBlank(driver.getVersion())) {
            // Explicit driver config make sure it exists in repo
            if (getDrivers(driver.getName(), driver.getPlatform(), driver.getBit(), driver.getVersion()).size() == 0) {
                throw new InstallWebDriversException("Could not find driver: " + driver + System.lineSeparator()
                        + System.lineSeparator()
                        + "in repository: " + this);
            }
        }

        if (isBlank(driver.getPlatform())) {
            driver.setPlatform(detectPlatform());
        }
        if (isBlank(driver.getBit())) {
            driver.setBit(detectBits(driver.getName()));
        }
        if (isBlank(driver.getVersion())) {
            driver.setVersion(getLatestDriverVersion(driver.getId()));
        }

        List<Driver> drivers = getDrivers(driver.getName(),
                driver.getPlatform(),
                driver.getBit(),
                driver.getVersion());
        if (drivers.isEmpty()) {
            if ("64".equals(driver.getBit())) {
                // toogle bits and try the other bit to get a driver configuration
                drivers = getDrivers(driver.getName(),
                        driver.getPlatform(),
                        "32",
                        driver.getVersion());

                if (drivers.isEmpty()) {
                    // Could not find any driver for the current platform/bit/version in repo
                    return null;
                }
                return filterLatestDriver(drivers);
            }
            return null;
        }
        return drivers.get(0);
    }

    List<Driver> getLatestDrivers() {
        List<Driver> latestDrivers = new ArrayList<Driver>();
        Collection<String> driverNames = selectDistinct(collect(drivers, on(Driver.class).getName()));

        String platform = detectPlatform();

        for (String driverName : driverNames) {
            List<Driver> driversWithDriverName = select(drivers, having(on(Driver.class).getName(), is(driverName)));
            List<Driver> driversWithDriverNameAndPlatform = select(driversWithDriverName,
                    having(on(Driver.class).getPlatform(),
                            is(platform)));
            String bit = detectBits(driverName);
            boolean is64Bit = bit.equals("64");
            Driver latestDriver = getDriverByBit(bit, driversWithDriverNameAndPlatform);
            if (latestDriver != null) {
                latestDrivers.add(latestDriver);
            } else if (is64Bit) {
                Driver latestDriverComplement = getDriverByBit("32", driversWithDriverNameAndPlatform);
                if (latestDriverComplement != null) {
                    latestDrivers.add(latestDriverComplement);
                }
            }
        }

        return sortDrivers(latestDrivers);
    }

    private Driver getDriverByBit(String bit, List<Driver> driversWithDriverNameAndPlatform) {
        List<Driver> driversWithDriverNameAndPlatformAndBit = select(driversWithDriverNameAndPlatform,
                having(on(Driver.class).getBit(), is(bit)));

        return selectMax(driversWithDriverNameAndPlatformAndBit,
                on(Driver.class).getComparableVersion());
    }

    private static String detectBits(String driverName) {
        // Default installed internetexplorer bit version on < Windows 10 versions is 32 bit
        if (driverName.equals("internetexplorerdriver") && !Utils.isWindows10()) {
            return "32";
        }

        // Detect bit version from os
        if (Utils.is64Bit()) {
            return "64";
        }
        return "32";
    }

    private static String detectPlatform() {
        if (Utils.isMac()) {
            return "mac";
        } else if (Utils.isLinux()) {
            return "linux";
        }
        return "windows";
    }

    private String getLatestDriverVersion(String driverId) {
        List<Driver> allDriverVersions = select(drivers, having(on(Driver.class).getId(), is(driverId)));
        Driver latestDriver = filterLatestDriver(allDriverVersions);
        if (latestDriver == null) {
            return null;
        }
        return latestDriver.getVersion();
    }

    private Driver filterLatestDriver(List<Driver> drivers) {
        return selectMax(drivers, on(Driver.class).getComparableVersion());
    }

    @Override
    public String toString() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(this);
    }
}
