package com.foodordering.util;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public final class RmiUtils {

    private RmiUtils() {}

    /**
     * Obtains an RMI registry for the specified port, creating one if it does not yet exist.
     */
    public static Registry getOrCreateRegistry(int port) throws RemoteException {
        try {
            return LocateRegistry.createRegistry(port);
        } catch (RemoteException e) {
            return LocateRegistry.getRegistry(port);
        }
    }
}
