package com.sss.testing.utils.webdriversinstaller;

public class InstallWebDriversException extends Exception {
    public InstallWebDriversException(String message) {
        super(message);
    }

    public InstallWebDriversException(String message, Exception cause) {
        super(message, cause);
    }

    public InstallWebDriversException(String message, InstallWebDrivers mojo, Driver driver) {
        this(message + Utils.debugInfo(mojo, driver));
    }

    public InstallWebDriversException(String message, Exception cause, InstallWebDrivers mojo, Driver driver) {
        this(message + Utils.debugInfo(mojo, driver), cause);
    }
}
