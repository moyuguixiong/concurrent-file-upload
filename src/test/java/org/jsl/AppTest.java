package org.jsl;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * Unit test for simple App.
 */
public class AppTest {
    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue() {
        try {
            InetSocketAddress socketAddress = new InetSocketAddress("350.1.1.1", 25252);
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.bind(socketAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
